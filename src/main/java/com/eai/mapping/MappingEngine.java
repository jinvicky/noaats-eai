package com.eai.mapping;

import com.eai.adapter.spi.DataRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MappingEngine {

    private final Configuration jpConfig;
    private final ObjectMapper mapper;
    private final TransformRegistry transforms;

    public MappingEngine(ObjectMapper mapper, TransformRegistry transforms) {
        this.mapper = mapper;
        this.transforms = transforms;
        this.jpConfig = Configuration.builder()
                .jsonProvider(new JacksonJsonProvider(mapper))
                .mappingProvider(new JacksonMappingProvider(mapper))
                .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();
    }

    public List<MappingRule> parseRules(String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) return List.of();
        try {
            return mapper.readValue(rulesJson, new TypeReference<List<MappingRule>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid mapping rules JSON: " + e.getMessage(), e);
        }
    }

    public DataRecord apply(DataRecord input, List<MappingRule> rules) {
        if (rules.isEmpty()) return input;
        DocumentContext srcDoc = JsonPath.using(jpConfig).parse(input.fields());
        Map<String, Object> out = new LinkedHashMap<>();
        DocumentContext dstDoc = JsonPath.using(jpConfig).parse(out);
        TransformContext tctx = new TransformContext(srcDoc);

        for (MappingRule rule : rules) {
            Object srcVal = rule.source() == null ? null : readPath(srcDoc, rule.source());
            List<Object> resolvedArgs = resolveArgs(rule.args(), srcDoc);
            Object transformed = transforms.require(rule.transform()).apply(srcVal, resolvedArgs, tctx);
            writePath(dstDoc, rule.target(), transformed);
        }

        return new DataRecord(out, input.meta());
    }

    private Object readPath(DocumentContext doc, String path) {
        try {
            if (path.startsWith("$")) return doc.read(path);
            return doc.read("$." + path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private void writePath(DocumentContext doc, String path, Object value) {
        String p = path.startsWith("$") ? path : "$." + path;
        String[] parts = p.substring(2).split("\\.");
        ensurePath(doc, "$", parts, 0, value);
    }

    private void ensurePath(DocumentContext doc, String base, String[] parts, int i, Object value) {
        if (i == parts.length - 1) {
            String leaf = parts[i];
            Object parent = doc.read(base);
            if (parent instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) m;
                mm.put(leaf, value);
            } else {
                doc.put(base, leaf, value);
            }
            return;
        }
        String seg = parts[i];
        String childPath = base.equals("$") ? "$." + seg : base + "." + seg;
        Object existing = doc.read(childPath);
        if (!(existing instanceof Map)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            if (base.equals("$")) {
                Object root = doc.read("$");
                if (root instanceof Map<?, ?> r) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rr = (Map<String, Object>) r;
                    rr.put(seg, empty);
                }
            } else {
                doc.put(base, seg, empty);
            }
        }
        ensurePath(doc, childPath, parts, i + 1, value);
    }

    private List<Object> resolveArgs(List<Object> args, DocumentContext srcDoc) {
        List<Object> out = new ArrayList<>(args.size());
        for (Object a : args) {
            if (a instanceof String s && s.startsWith("$")) {
                try { out.add(srcDoc.read(s)); }
                catch (PathNotFoundException e) { out.add(null); }
            } else {
                out.add(a);
            }
        }
        return out;
    }
}
