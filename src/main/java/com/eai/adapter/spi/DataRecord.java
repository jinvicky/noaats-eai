package com.eai.adapter.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DataRecord(Map<String, Object> fields, Map<String, Object> meta) {

    public static DataRecord of(Map<String, Object> fields) {
        return new DataRecord(fields, Collections.emptyMap());
    }

    public static DataRecord empty() {
        return new DataRecord(new LinkedHashMap<>(), new LinkedHashMap<>());
    }
}
