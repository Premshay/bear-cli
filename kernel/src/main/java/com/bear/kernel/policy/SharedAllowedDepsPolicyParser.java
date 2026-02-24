package com.bear.kernel.policy;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SharedAllowedDepsPolicyParser {
    private static final Pattern MAVEN_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+");
    private final Yaml yaml;

    public SharedAllowedDepsPolicyParser() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    public SharedAllowedDepsPolicy parse(Path policyFile) throws IOException, SharedAllowedDepsPolicyException {
        if (!Files.isRegularFile(policyFile)) {
            return SharedAllowedDepsPolicy.empty();
        }
        try (InputStream in = Files.newInputStream(policyFile)) {
            Object document = loadSingleDocument(in, policyFile.toString());
            return parseDocument(document, policyFile.toString());
        }
    }

    public SharedAllowedDepsPolicy parseContent(String content, String sourceLabel) throws SharedAllowedDepsPolicyException {
        Object document;
        try {
            document = yaml.load(content == null ? "" : content);
        } catch (YAMLException e) {
            throw policyError("INVALID_YAML", sourceLabel, "invalid YAML");
        }
        return parseDocument(document, sourceLabel);
    }

    private Object loadSingleDocument(InputStream in, String sourceLabel) throws SharedAllowedDepsPolicyException {
        Object first = null;
        int count = 0;
        try {
            for (Object doc : yaml.loadAll(in)) {
                if (count == 0) {
                    first = doc;
                }
                count++;
                if (count > 1) {
                    throw policyError("MULTI_DOCUMENT", sourceLabel, "multiple YAML documents are not allowed");
                }
            }
            return first;
        } catch (YAMLException e) {
            throw policyError("INVALID_YAML", sourceLabel, "invalid YAML");
        }
    }

    private SharedAllowedDepsPolicy parseDocument(Object document, String sourceLabel) throws SharedAllowedDepsPolicyException {
        if (document == null) {
            throw policyError("MISSING_ROOT", sourceLabel, "policy file is empty");
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw policyError("INVALID_TYPE_root", sourceLabel, "root must be a YAML mapping");
        }

        requireOnlyKeys(root, sourceLabel, "root", Set.of("version", "scope", "impl"));
        String version = requireString(root, sourceLabel, "version", "version");
        String scope = requireString(root, sourceLabel, "scope", "scope");
        if (!"v1".equals(version)) {
            throw policyError("INVALID_VERSION", sourceLabel, "version must be v1");
        }
        if (!"shared".equals(scope)) {
            throw policyError("INVALID_SCOPE", sourceLabel, "scope must be shared");
        }

        Map<?, ?> impl = requireMap(root, sourceLabel, "impl", "impl");
        requireOnlyKeys(impl, sourceLabel, "impl", Set.of("allowedDeps"));
        List<?> allowedDepsRaw = requireList(impl, sourceLabel, "allowedDeps", "impl.allowedDeps");

        ArrayList<SharedAllowedDepsPolicy.Dependency> deps = new ArrayList<>();
        HashSet<String> seenGa = new HashSet<>();
        for (int i = 0; i < allowedDepsRaw.size(); i++) {
            String itemPath = "impl.allowedDeps[" + i + "]";
            Object rawItem = allowedDepsRaw.get(i);
            if (!(rawItem instanceof Map<?, ?> dep)) {
                throw policyError("INVALID_TYPE_allowedDeps_item", sourceLabel, itemPath + " must be a mapping");
            }
            requireOnlyKeys(dep, sourceLabel, itemPath, Set.of("maven", "version"));
            String maven = requireString(dep, sourceLabel, "maven", itemPath + ".maven");
            String versionValue = requireString(dep, sourceLabel, "version", itemPath + ".version");

            validateMaven(sourceLabel, itemPath + ".maven", maven);
            validatePinnedVersion(sourceLabel, itemPath + ".version", versionValue);
            if (!seenGa.add(maven)) {
                throw policyError("DUPLICATE_ALLOWED_DEP", sourceLabel, "duplicate maven coordinate: " + maven);
            }
            deps.add(new SharedAllowedDepsPolicy.Dependency(maven, versionValue));
        }

        deps.sort((left, right) -> left.maven().compareTo(right.maven()));
        return new SharedAllowedDepsPolicy(List.copyOf(deps));
    }

    private void validateMaven(String sourceLabel, String path, String value) throws SharedAllowedDepsPolicyException {
        if (!MAVEN_COORDINATE.matcher(value).matches()) {
            throw policyError("INVALID_MAVEN_COORDINATE", sourceLabel, path + " must be groupId:artifactId");
        }
        if (value.contains("*")) {
            throw policyError("INVALID_MAVEN_COORDINATE", sourceLabel, path + " must not contain wildcard");
        }
    }

    private void validatePinnedVersion(String sourceLabel, String path, String value) throws SharedAllowedDepsPolicyException {
        if (value.contains("*")
            || value.contains("[")
            || value.contains("]")
            || value.contains("(")
            || value.contains(")")
            || value.contains(",")
            || value.contains("+")) {
            throw policyError("INVALID_PINNED_VERSION", sourceLabel, path + " must be exact pinned version");
        }
    }

    private void requireOnlyKeys(
        Map<?, ?> map,
        String sourceLabel,
        String path,
        Set<String> allowedKeys
    ) throws SharedAllowedDepsPolicyException {
        for (Object key : map.keySet()) {
            String keyName = String.valueOf(key);
            if (!allowedKeys.contains(keyName)) {
                throw policyError("UNKNOWN_KEY", sourceLabel, path + " contains unknown key: " + keyName);
            }
        }
    }

    private String requireString(
        Map<?, ?> map,
        String sourceLabel,
        String key,
        String path
    ) throws SharedAllowedDepsPolicyException {
        if (!map.containsKey(key)) {
            throw policyError("MISSING_FIELD", sourceLabel, "missing field: " + path);
        }
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw policyError("INVALID_TYPE", sourceLabel, path + " must be string");
        }
        if (text.isBlank()) {
            throw policyError("INVALID_VALUE", sourceLabel, path + " must be non-blank");
        }
        return text;
    }

    private Map<?, ?> requireMap(
        Map<?, ?> map,
        String sourceLabel,
        String key,
        String path
    ) throws SharedAllowedDepsPolicyException {
        if (!map.containsKey(key)) {
            throw policyError("MISSING_FIELD", sourceLabel, "missing field: " + path);
        }
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> child)) {
            throw policyError("INVALID_TYPE", sourceLabel, path + " must be mapping");
        }
        return child;
    }

    private List<?> requireList(
        Map<?, ?> map,
        String sourceLabel,
        String key,
        String path
    ) throws SharedAllowedDepsPolicyException {
        if (!map.containsKey(key)) {
            throw policyError("MISSING_FIELD", sourceLabel, "missing field: " + path);
        }
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            throw policyError("INVALID_TYPE", sourceLabel, path + " must be list");
        }
        return list;
    }

    private SharedAllowedDepsPolicyException policyError(String reasonCode, String sourceLabel, String message) {
        return new SharedAllowedDepsPolicyException(reasonCode, sourceLabel + ": " + message);
    }
}
