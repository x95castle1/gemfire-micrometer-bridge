package com.vmware.tanzu.gemfire.starter;

import java.util.HashMap;
import java.util.Map;

public class GemFireMetricBridgeProperties {

    private Map<String, String> export = new HashMap<>(
            Map.of("CachePerfStats", "cachePerfStats|gets,getTime,puts,putTime")
    );
    private Long rescanInterval = 60000L;

    public Map<String, String> getExport() {
        return export;
    }

    public void setExport(Map<String, String> export) {
        this.export = export;
    }

    public Long getRescanInterval() {
        return rescanInterval;
    }

    public void setRescanInterval(Long rescanInterval) {
        this.rescanInterval = rescanInterval;
    }
}
