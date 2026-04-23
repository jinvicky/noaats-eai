package com.eai.adapter.spi;

import com.eai.domain.AdapterType;
import java.util.stream.Stream;

public interface Adapter {

    AdapterType type();

    Capabilities capabilities();

    /**
     * Open a new adapter instance bound to the given configuration JSON.
     * Each execution creates a fresh instance so adapters can be stateful/streaming.
     */
    BoundAdapter bind(String configJson);

    interface BoundAdapter extends AutoCloseable {
        Stream<DataRecord> read(AdapterContext ctx);
        WriteResult write(Stream<DataRecord> records, AdapterContext ctx);
        @Override default void close() {}
    }
}
