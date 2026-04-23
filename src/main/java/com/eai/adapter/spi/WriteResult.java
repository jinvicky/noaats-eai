package com.eai.adapter.spi;

public record WriteResult(long recordsWritten, long recordsFailed, String summary) {

    public static WriteResult of(long written) {
        return new WriteResult(written, 0, null);
    }
}
