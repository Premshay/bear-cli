package com.bear.kernel.ir;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BearIr {
    private final Map<String, Object> document;

    public BearIr(Map<String, Object> document) {
        this.document = Map.copyOf(new LinkedHashMap<>(document));
    }

    public Map<String, Object> document() {
        return document;
    }
}
