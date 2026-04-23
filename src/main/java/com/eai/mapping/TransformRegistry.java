package com.eai.mapping;

import jakarta.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TransformRegistry {

    private final Map<String, TransformFn> byName = new HashMap<>();

    public TransformRegistry(List<TransformFn> discovered) {
        for (TransformFn fn : discovered) register(fn);
    }

    @PostConstruct
    void registerBuiltins() {
        register(simple("identity", (v, a) -> v));
        register(simple("uppercase", (v, a) -> v == null ? null : v.toString().toUpperCase()));
        register(simple("lowercase", (v, a) -> v == null ? null : v.toString().toLowerCase()));
        register(simple("trim", (v, a) -> v == null ? null : v.toString().trim()));
        register(simple("toString", (v, a) -> v == null ? null : String.valueOf(v)));
        register(simple("toNumber", (v, a) -> {
            if (v == null) return null;
            if (v instanceof Number n) return n;
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            try { return Long.parseLong(s); }
            catch (NumberFormatException ignored) { return Double.parseDouble(s); }
        }));
        register(simple("constant", (v, args) -> args.isEmpty() ? null : args.get(0)));
        register(simple("default", (v, args) -> v != null ? v : (args.isEmpty() ? null : args.get(0))));
        register(simple("concat", (v, args) -> {
            StringBuilder sb = new StringBuilder();
            if (v != null) sb.append(v);
            for (Object a : args) if (a != null) sb.append(a);
            return sb.toString();
        }));
        register(simple("substring", (v, args) -> {
            if (v == null) return null;
            String s = v.toString();
            int start = args.isEmpty() ? 0 : ((Number) args.get(0)).intValue();
            int end = args.size() < 2 ? s.length() : ((Number) args.get(1)).intValue();
            start = Math.max(0, Math.min(start, s.length()));
            end = Math.max(start, Math.min(end, s.length()));
            return s.substring(start, end);
        }));
        register(simple("dateFormat", (v, args) -> {
            if (v == null) return null;
            String pattern = args.isEmpty() ? "yyyy-MM-dd" : args.get(0).toString();
            Date d = (v instanceof Date) ? (Date) v : new Date(((Number) v).longValue());
            return new SimpleDateFormat(pattern).format(d);
        }));
    }

    public void register(TransformFn fn) {
        byName.put(fn.name(), fn);
    }

    public TransformFn require(String name) {
        TransformFn fn = byName.get(name);
        if (fn == null) throw new IllegalArgumentException("Unknown transform: " + name);
        return fn;
    }

    private static TransformFn simple(String name, SimpleFn fn) {
        return new TransformFn() {
            @Override public String name() { return name; }
            @Override public Object apply(Object input, List<Object> args, TransformContext ctx) {
                return fn.apply(input, args);
            }
        };
    }

    @FunctionalInterface
    interface SimpleFn {
        Object apply(Object input, List<Object> args);
    }
}
