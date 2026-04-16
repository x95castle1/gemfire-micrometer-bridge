package com.vmware.tanzu.gemfire.starter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GemFireMicrometerAutoConfigurationTest {

    @Test
    void propertiesBeanHasEmptyExportByDefault() {
        GemFireMetricBridgeProperties props = new GemFireMicrometerAutoConfiguration().gemFireMetricBridgeProperties();

        assertNotNull(props.getExport());
        assertTrue(props.getExport().isEmpty());
    }
}