package com.vmware.tanzu.gemfire.starter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemFireMetricBridgePropertiesTest {

    @Test
    void exportDefaultsToEmptyMap() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();

        assertNotNull(props.getExport());
        assertTrue(props.getExport().isEmpty());
    }

    @Test
    void rescanIntervalDefaultsTo60Seconds() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();

        assertEquals(60000L, props.getRescanInterval());
    }

    @Test
    void setAndGetExport() {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();
        Map<String, String> config = Map.of("CachePerfStats", ".*::gets,puts");

        props.setExport(config);

        assertEquals(config, props.getExport());
    }
}