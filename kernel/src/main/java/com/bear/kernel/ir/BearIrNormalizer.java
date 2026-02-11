package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BearIrNormalizer {
    public BearIr normalize(BearIr ir) {
        Map<String, Object> normalized = normalizeMap(ir.document());
        return new BearIr(normalized);
    }

    private Map<String, Object> normalizeMap(Map<String, Object> map) {
        Map<String, Object> sorted = new LinkedHashMap<>();
        map.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sorted.put(e.getKey(), normalizeValue(e.getValue())));
        return sorted;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> castMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                castMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalizeMap(castMap);
        }
        if (value instanceof List<?> rawList) {
            List<Object> list = new ArrayList<>();
            for (Object item : rawList) {
                list.add(normalizeValue(item));
            }
            return list;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return value;
    }
}
