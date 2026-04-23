package com.eai.adapter.spi;

import java.util.Map;

public record AdapterContext(String runId, Map<String, Object> triggerMeta) {

    public static AdapterContext of(String runId) {
        return new AdapterContext(runId, Map.of());
    }
}
