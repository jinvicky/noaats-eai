package com.eai.adapter.spi;

public record Capabilities(boolean supportsRead, boolean supportsWrite, boolean streaming) {

    public static Capabilities readWrite() {
        return new Capabilities(true, true, true);
    }

    public static Capabilities readOnly() {
        return new Capabilities(true, false, true);
    }

    public static Capabilities writeOnly() {
        return new Capabilities(false, true, true);
    }
}
