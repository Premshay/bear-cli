package com.bear.kernel.target.node;

import com.bear.kernel.target.ManifestParseException;
import com.bear.kernel.target.TargetManifestParsers;
import com.bear.kernel.target.WiringManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Node wiring manifest JSON files.
 * 
 * Node manifests use a distinct schema from JVM manifests:
 * - version (must be "1")
 * - blockKey (required)
 * - targetId (stored as entrypointFqcn)
 * - generatedPackage (stored as logicInterfaceFqcn)
 * - implPackage (stored as implFqcn)
 * - wrappers array (stored as governedSourceRoots, serialized as JSON strings)
 * - ports array (stored as requiredEffectPorts, serialized as JSON strings)
 */
public final class NodeManifestParser {
    private NodeManifestParser() {}

    /**
     * Parses a Node wiring manifest JSON file into a WiringManifest record.
     *
     * @param path the path to the wiring manifest JSON file
     * @return the parsed WiringManifest
     * @throws IOException if the file cannot be read
     * @throws ManifestParseException if the JSON is malformed or missing required fields
     */
    public static WiringManifest parse(Path path) throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        return parseJson(json);
    }

    /**
     * Parses a Node wiring manifest JSON string into a WiringManifest record.
     * Package-private for testing.
     *
     * @param json the JSON string to parse
     * @return the parsed WiringManifest
     * @throws ManifestParseException if the JSON is malformed or missing required fields
     */
    static WiringManifest parseJson(String json) throws ManifestParseException {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }

        String version = extractRequiredString(json, "version");
        if (!"1".equals(version)) {
            throw new ManifestParseException("UNSUPPORTED_WIRING_SCHEMA_VERSION");
        }

        String blockKey = extractRequiredString(json, "blockKey");
        String targetId = extractOptionalString(json, "targetId");
        String generatedPackage = extractOptionalString(json, "generatedPackage");
        String implPackage = extractOptionalString(json, "implPackage");
        List<String> wrappers = extractOptionalObjectArray(json, "wrappers");
        List<String> ports = extractOptionalObjectArray(json, "ports");

        return new WiringManifest(
            version,
            blockKey,
            targetId != null ? targetId : "",
            generatedPackage != null ? generatedPackage : "",
            implPackage != null ? implPackage : "",
            "",  // implSourcePath - absent in Node manifests
            "",  // blockRootSourceDir - absent in Node manifests
            wrappers,
            ports,
            List.of(),  // constructorPortParams
            List.of(),  // logicRequiredPorts
            List.of(),  // wrapperOwnedSemanticPorts
            List.of()   // wrapperOwnedSemanticChecks
        );
    }

    /**
     * Extracts a required string field from JSON.
     * Handles whitespace around the colon.
     */
    private static String extractRequiredString(String json, String key) throws ManifestParseException {
        // Pattern allows optional whitespace around the colon: "key" : "value" or "key":"value"
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        return TargetManifestParsers.jsonUnescape(m.group(1));
    }

    /**
     * Extracts an optional string field from JSON.
     * Returns null if the field is not present.
     */
    private static String extractOptionalString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            return null;
        }
        return TargetManifestParsers.jsonUnescape(m.group(1));
    }

    /**
     * Extracts an optional array of JSON objects as serialized strings.
     * Returns List.of() if the field is not present or the array is empty.
     */
    private static List<String> extractOptionalObjectArray(String json, String key) {
        // Find the array start: "key":[
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) {
            return List.of();
        }

        // Find the opening bracket after the key
        int colonIdx = json.indexOf(':', keyIdx + key.length() + 2);
        if (colonIdx < 0) {
            return List.of();
        }

        int start = -1;
        for (int i = colonIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                start = i;
                break;
            } else if (!Character.isWhitespace(c)) {
                return List.of();
            }
        }

        if (start < 0) {
            return List.of();
        }

        // Find matching closing bracket
        int depth = 0;
        int end = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end < 0) {
            return List.of();
        }

        String payload = json.substring(start + 1, end).trim();
        if (payload.isEmpty()) {
            return List.of();
        }

        // Split into individual objects and return as serialized JSON strings
        return splitObjectArray(payload);
    }

    /**
     * Splits an array payload into individual JSON object strings.
     */
    private static List<String> splitObjectArray(String payload) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(payload.substring(start, i + 1));
                    start = -1;
                }
            }
        }

        return List.copyOf(objects);
    }
}
