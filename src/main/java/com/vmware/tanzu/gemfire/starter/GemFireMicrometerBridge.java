package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GemFireMicrometerBridge {
    private static final Logger log = LoggerFactory.getLogger(GemFireMicrometerBridge.class);

    private final ClientCache clientCache;
    private final MeterRegistry registry;
    private final Set<String> bound = new HashSet<>();
    private final Map<String, String> exportConfig;

    public GemFireMicrometerBridge(ClientCache clientCache, MeterRegistry registry, GemFireMetricBridgeProperties gemFireMetricBridgeProperties) {
        this.clientCache = clientCache;
        this.registry = registry;
        this.exportConfig = gemFireMetricBridgeProperties.getExport();
        log.info("GemFireMicroMeterBridge enabled - rescan-interval: {} exports: {}", gemFireMetricBridgeProperties.getRescanInterval(), exportConfig);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialBind() {
        rescan();
    }

    @Scheduled(fixedRateString = "#{@gemFireMetricBridgeProperties.rescanInterval}")
    public void rescan() {
        if (exportConfig.isEmpty()) {
            log.debug("Rescan skipped: exportConfig is empty.");
            return;
        }

        log.debug("Starting GemFire statistics rescan...");
        int matchCount = 0;

        try {
            InternalDistributedSystem ids = (InternalDistributedSystem) clientCache.getDistributedSystem();
            List<Statistics> statsList = ids.getStatisticsManager().getStatsList();

            log.trace("Found {} total GemFire statistics instances to evaluate.", statsList.size());

            for (Statistics stats : statsList) {
                String typeName = stats.getType().getName();
                for (Map.Entry<String, String> entry : exportConfig.entrySet()) {
                    if (Pattern.matches(entry.getKey(), typeName)) {
                        log.trace("Type match found: '{}' matches filter '{}'", typeName, entry.getKey());
                        processStatisticsInstance(stats, entry.getValue());
                        matchCount++;
                    }
                }
            }
            log.debug("Rescan complete. Processed {} matching statistic types. Total unique meters bound: {}",
                    matchCount, bound.size());
        } catch (Exception e) {
            log.error("GemFire Micrometer bridge rescan failed unexpectedly", e);
        }
    }

    private void processStatisticsInstance(Statistics stats, String configFilter) {
        String textId = stats.getTextId() == null ? "default" : stats.getTextId();

        String[] parts = configFilter.split("::");
        String instanceRegex = parts.length > 1 ? parts[0] : ".*";
        String statsRegex = parts.length > 1 ? parts[1] : parts[0];

        if (Pattern.matches(instanceRegex, textId)) {
            log.debug("Instance match: '{}' matches filter '{}'", textId, instanceRegex);
            bindMatchedStats(stats, statsRegex);
        }
    }

    private void bindMatchedStats(Statistics stats, String statsRegex) {
        String[] statPatterns = statsRegex.split(",");
        for (StatisticDescriptor d : stats.getType().getStatistics()) {
            for (String pattern : statPatterns) {
                if (Pattern.matches(pattern.trim(), d.getName())) {
                    registerMeter(stats, d);
                    break;
                }
            }
        }
    }

    private void registerMeter(Statistics stats, StatisticDescriptor d) {
        String typeName = stats.getType().getName();
        String meterKey = typeName + "#" + stats.getUniqueId() + "#" + d.getName();

        if (!bound.add(meterKey)) {
            // Already bound, no log needed unless you're deep-diving (TRACE)
            return;
        }

        String meterName = sanitize("gemfire." + typeName + "." + d.getName());
        log.debug("Registering new meter: {} (Tags: category={}, name={})",
                meterName, typeName, stats.getTextId());

        Tags tags = Tags.of("category", typeName, "name", stats.getTextId() == null ? "default" : stats.getTextId());

        try {
            if (d.isCounter()) {
                FunctionCounter.builder(meterName, stats, s -> s.get(d).doubleValue())
                        .tags(tags)
                        .description(d.getDescription())
                        .register(registry);
            } else {
                Gauge.builder(meterName, stats, s -> s.get(d).doubleValue())
                        .tags(tags)
                        .description(d.getDescription())
                        .register(registry);
            }
        } catch (Exception e) {
            log.error("Failed to register meter '{}': {}", meterName, e.getMessage());
        }
    }

    private String sanitize(String value) {
        return value.toLowerCase().replace('_', '.').replaceAll("[:\\- ,=\\*\\?\\\\]", ".");
    }
}
