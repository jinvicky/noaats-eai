package com.eai.adapter.spi;

import com.eai.domain.AdapterType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdapterFactory {

    private final Map<AdapterType, Adapter> byType = new EnumMap<>(AdapterType.class);

    public AdapterFactory(List<Adapter> adapters) {
        for (Adapter a : adapters) {
            Adapter existing = byType.put(a.type(), a);
            if (existing != null) {
                throw new IllegalStateException("Duplicate adapter for type " + a.type()
                        + ": " + existing.getClass() + " and " + a.getClass());
            }
        }
    }

    public Adapter require(AdapterType type) {
        Adapter a = byType.get(type);
        if (a == null) throw new IllegalStateException("No adapter registered for " + type);
        return a;
    }
}
