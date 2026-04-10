package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.geode.cache.client.ClientCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnBean(ClientCache.class)
public class GemFireMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gemfire.metrics.bridge.enabled", havingValue = "true", matchIfMissing = true)
    public GemFireMicrometerBridge gemFireMicrometerBridge(ClientCache clientCache, MeterRegistry registry) {
        return new GemFireMicrometerBridge(clientCache, registry);
    }
}