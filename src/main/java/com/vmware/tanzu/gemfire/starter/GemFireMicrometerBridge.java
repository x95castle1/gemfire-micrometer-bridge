package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.*;
import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GemFireMicrometerBridge {
    private static final Logger log = LoggerFactory.getLogger(GemFireMicrometerBridge.class);

    private final ClientCache clientCache;
    private final MeterRegistry registry;
    private final Set<String> bound = new HashSet<>();

    @Value("#{${gemfire.metrics.export:{}}}")
    private Map<String, String> exportConfig;

    public GemFireMicrometerBridge(ClientCache clientCache, MeterRegistry registry) {
        log.info("Enabling GemFireMicroMeterBridge");
        this.clientCache = clientCache;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialBind() { rescan(); }

    @Scheduled(fixedDelayString = "${gemfire.metrics.rescan-interval:30000}")
    public void rescan() {
        if (exportConfig.isEmpty()) return;
        try {
            InternalDistributedSystem ids = (InternalDistributedSystem) clientCache.getDistributedSystem();
            for (Statistics stats : ids.getStatisticsManager().getStatsList()) {
                String typeName = stats.getType().getName();
                // Check if the typeName matches any regex key in our config
                for (Map.Entry<String, String> entry : exportConfig.entrySet()) {
                    if (Pattern.matches(entry.getKey(), typeName)) {
                        processStatisticsInstance(stats, entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("GemFire Micrometer bridge rescan failed", e);
        }
    }

    private void processStatisticsInstance(Statistics stats, String configFilter) {
        String textId = stats.getTextId() == null ? "default" : stats.getTextId();
        String[] parts = configFilter.split("\\|");
        String instanceRegex = parts.length > 1 ? parts[0] : ".*";
        String statsRegex = parts.length > 1 ? parts[1] : parts[0];

        if (Pattern.matches(instanceRegex, textId)) {
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
        if (!bound.add(meterKey)) return;

        String meterName = sanitize("gemfire." + typeName + "." + d.getName());
        Tags tags = Tags.of("type", typeName, "textId", stats.getTextId() == null ? "default" : stats.getTextId());

        if (d.isCounter()) {
            FunctionCounter.builder(meterName, stats, s -> s.get(d).doubleValue()).tags(tags).register(registry);
        } else {
            Gauge.builder(meterName, stats, s -> s.get(d).doubleValue()).tags(tags).register(registry);
        }
    }

    private String sanitize(String value) {
        return value.toLowerCase().replace('_', '.').replaceAll("[:\\- ,=\\*\\?\\\\]", ".");
    }
}