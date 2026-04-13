package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.geode.cache.client.ClientCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GemFireMicrometerAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GemFireMicrometerAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class))
            .withBean(ClientCache.class, () -> mock(ClientCache.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultPropertiesAreApplied() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GemFireMetricBridgeProperties.class);
            GemFireMetricBridgeProperties props = context.getBean(GemFireMetricBridgeProperties.class);

            assertThat(props.getRescanInterval()).isEqualTo(60000L);
            assertThat(props.getExport()).containsKey("CachePerfStats");
            assertThat(props.getExport().get("CachePerfStats"))
                    .isEqualTo("cachePerfStats|gets,getTime,puts,putTime");
        });
    }

    @Test
    void exportConfigCanBeOverridden() {
        contextRunner
                .withPropertyValues(
                        "gemfire.metrics.bridge.export.PoolStats=.*|connections",
                        "gemfire.metrics.bridge.export.ClientStats=.*|sentBytes,receivedBytes"
                )
                .run(context -> {
                    GemFireMetricBridgeProperties props = context.getBean(GemFireMetricBridgeProperties.class);

                    assertThat(props.getExport()).containsKey("PoolStats");
                    assertThat(props.getExport()).containsKey("ClientStats");
                    assertThat(props.getExport().get("PoolStats")).isEqualTo(".*|connections");
                });
    }

    @Test
    void rescanIntervalCanBeOverridden() {
        contextRunner
                .withPropertyValues("gemfire.metrics.bridge.rescan-interval=5000")
                .run(context -> {
                    GemFireMetricBridgeProperties props = context.getBean(GemFireMetricBridgeProperties.class);
                    assertThat(props.getRescanInterval()).isEqualTo(5000L);
                });
    }

    @Test
    void bridgeBeanIsCreatedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GemFireMicrometerBridge.class);
        });
    }

    @Test
    void bridgeBeanIsDisabledWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("gemfire.metrics.bridge.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GemFireMicrometerBridge.class);
                });
    }

    @Test
    void bridgeBeanNotCreatedWithoutClientCache() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GemFireMicrometerAutoConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GemFireMicrometerBridge.class);
                });
    }
}