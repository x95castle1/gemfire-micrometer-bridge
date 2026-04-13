package com.vmware.tanzu.gemfire.starter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GemFireMicrometerAutoConfigurationTest {

    @Test
    void gemfireBridgeExportsReturnsExpectedDefaults() {
        GemFireMetricBridgeProperties props = new GemFireMicrometerAutoConfiguration().gemFireMetricBridgeProperties();

        Map<String, String> result = props.getExport();

        assertEquals(1, result.size());
        assertEquals("cachePerfStats|gets,getTime,puts,putTime", result.get("CachePerfStats"));
    }

}