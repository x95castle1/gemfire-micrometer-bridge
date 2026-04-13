package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.geode.cache.client.ClientCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableScheduling
@ConditionalOnBean(ClientCache.class)
@EnableConfigurationProperties(GemFireMetricBridgeProperties.class)
public class GemFireMicrometerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GemFireMicrometerAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "gemfire.metrics.bridge.enabled", havingValue = "true", matchIfMissing = true)
    public GemFireMicrometerBridge gemFireMicrometerBridge(ClientCache clientCache, MeterRegistry registry) {
        return new GemFireMicrometerBridge(clientCache, registry);
    }

    @Bean("gemfireBridgeExports")
    public Map<String, String> gemfireBridgeExports(GemFireMetricBridgeProperties properties) {
        if (properties.getExport().isEmpty()) {
            log.info("No GemFire export properties found. Applying default CachePerfStats filter.");
            return Map.of("CachePerfStats", "cachePerfStats|gets,getTime,puts,putTime");
        }
        log.info("GemFire export properties loaded: {}", properties.getExport().keySet());
        return properties.getExport();
    }
}