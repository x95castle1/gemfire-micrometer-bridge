package com.vmware.tanzu.gemfire.starter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GemFireMetricBridgePropertiesTest {

    @Test
    void exportDefaults() {
        HashMap<String, String> defaultExport = new HashMap<>(
                Map.of("CachePerfStats", "cachePerfStats|gets,getTime,puts,putTime")
        );

        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();

        assertNotNull(props.getExport());
        assertEquals(props.getExport(), defaultExport);
    }

    @Test
    void setAndGetExport() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();
        Map<String, String> config = Map.of("CachePerfStats", ".*|gets,puts");

        props.setExport(config);

        assertEquals(config, props.getExport());
    }
}