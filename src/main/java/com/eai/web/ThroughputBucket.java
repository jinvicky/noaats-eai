package com.eai.web;

public record ThroughputBucket(
        String label,    // "14:00"
        long hourIndex,  // 0 (oldest) .. 23 (newest)
        long read,
        long written,
        long runs
) {}
