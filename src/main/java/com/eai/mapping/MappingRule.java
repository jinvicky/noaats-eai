package com.eai.mapping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappingRule(String source, String target, String transform, List<Object> args) {

    public MappingRule {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("mapping rule target is required");
        }
        if (transform == null || transform.isBlank()) transform = "identity";
        if (args == null) args = List.of();
    }
}
