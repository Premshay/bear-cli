package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CheckRulesPolicyParser {
    static final String CHECK_RULES_POLICY_PATH = ".bear/policy/check-rules.properties";
    private static final String KEY_IMPL_CONTAINMENT = "impl_containment";
    private static final Set<String> ALLOWED_KEYS = Set.of(KEY_IMPL_CONTAINMENT);

    private CheckRulesPolicyParser() {
    }

    static CheckRulesPolicy parse(Path projectRoot) throws IOException, PolicyValidationException {
        Path policyFile = projectRoot.resolve(CHECK_RULES_POLICY_PATH).normalize();
        if (!Files.exists(policyFile)) {
            return CheckRulesPolicy.defaults();
        }
        if (!Files.isRegularFile(policyFile)) {
            throw new PolicyValidationException(CHECK_RULES_POLICY_PATH, "policy file is not a regular file");
        }

        List<String> lines = Files.readAllLines(policyFile, StandardCharsets.UTF_8);
        ArrayList<String> keyOrder = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        HashMap<String, String> values = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int eq = trimmed.indexOf('=');
            if (eq <= 0 || eq == trimmed.length() - 1) {
                throw invalid(i + 1, "expected key=value");
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                throw invalid(i + 1, "expected key=value");
            }
            if (!ALLOWED_KEYS.contains(key)) {
                throw invalid(i + 1, "unknown key: " + key);
            }
            if (!seen.add(key)) {
                throw invalid(i + 1, "duplicate key: " + key);
            }

            keyOrder.add(key);
            values.put(key, value);
        }

        ArrayList<String> sortedKeys = new ArrayList<>(keyOrder);
        sortedKeys.sort(String::compareTo);
        if (!sortedKeys.equals(keyOrder)) {
            throw new PolicyValidationException(
                CHECK_RULES_POLICY_PATH,
                "keys must be sorted lexicographically"
            );
        }

        String rawContainment = values.getOrDefault(KEY_IMPL_CONTAINMENT, "true");
        if (!"true".equals(rawContainment) && !"false".equals(rawContainment)) {
            throw new PolicyValidationException(
                CHECK_RULES_POLICY_PATH,
                "invalid value for impl_containment: " + rawContainment
            );
        }
        return new CheckRulesPolicy("true".equals(rawContainment));
    }

    private static PolicyValidationException invalid(int line, String message) {
        return new PolicyValidationException(
            CHECK_RULES_POLICY_PATH,
            "line " + line + ": " + message
        );
    }
}
