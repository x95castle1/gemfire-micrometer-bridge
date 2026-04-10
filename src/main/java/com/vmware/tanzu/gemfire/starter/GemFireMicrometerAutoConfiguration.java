package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.geode.cache.client.ClientCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties(GemFireMetricBridgeProperties.class)
@Configuration
@EnableScheduling
@ConditionalOnBean(ClientCache.class)
public class GemFireMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gemfire.metrics.bridge.enabled", havingValue = "true", matchIfMissing = true)
    public GemFireMicrometerBridge gemFireMicrometerBridge(ClientCache clientCache, MeterRegistry registry) {
        return new GemFireMicrometerBridge(clientCache, registry);
    }

    @Bean("GemFireMetricBridgeProperty")
    public Map<String, String> gemFireMetricBridgeProperties(GemFireMetricBridgeProperties properties) {
        Map<String, String> defaultProps = new HashMap<>(
                Map.of("CachePerfStats", "cachePerfStats|gets,getTime,puts,putTime")
        );

        return properties.getExport().isEmpty()? defaultProps : properties.getExport();
    }
}