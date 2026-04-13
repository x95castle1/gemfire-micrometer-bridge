package com.vmware.tanzu.gemfire.starter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GemFireMicrometerAutoConfigurationTest {

    private final GemFireMicrometerAutoConfiguration config = new GemFireMicrometerAutoConfiguration();

    @Test
    void gemfireBridgeExportsReturnsDefaultsWhenPropertiesEmpty() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();

        Map<String, String> result = config.gemfireBridgeExports(props);

        assertEquals(1, result.size());
        assertEquals("cachePerfStats|gets,getTime,puts,putTime", result.get("CachePerfStats"));
    }

    @Test
    void gemfireBridgeExportsReturnsUserConfigWhenProvided() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();
        Map<String, String> custom = Map.of(
                "PoolStats", ".*|connections",
                "ClientStats", ".*|sentBytes,receivedBytes"
        );
        props.setExport(custom);

        Map<String, String> result = config.gemfireBridgeExports(props);

        assertEquals(custom, result);
        assertFalse(result.containsKey("CachePerfStats"),
                "User config should replace defaults, not merge");
    }
}