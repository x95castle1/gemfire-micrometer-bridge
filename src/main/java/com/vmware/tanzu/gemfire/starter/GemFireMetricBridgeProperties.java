package com.vmware.tanzu.gemfire.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "gemfire.metrics.bridge")
public class GemFireMetricBridgeProperties {

    private Map<String, String> export = new HashMap<>();

    public Map<String, String> getExport() {
        return export;
    }

    public void setExport(Map<String, String> export) {
        this.export = export;
    }

}
