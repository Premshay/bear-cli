package com.bear.kernel.ir;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BearIrParser {
    private final Yaml yaml = new Yaml();

    public BearIr parse(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object parsed = yaml.load(in);
            if (parsed == null) {
                return new BearIr(Map.of());
            }
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("BEAR IR root must be a YAML object");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return new BearIr(result);
        }
    }
}
