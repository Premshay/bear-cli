package com.bear.kernel.target.node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves TypeScript path aliases from tsconfig.json.
 * 
 * Reads `tsconfig.json` at project root, extracts `compilerOptions.paths["@/*"][0]`.
 * Result is cached in the resolver instance (read once per scan invocation).
 * 
 * Key behaviors:
 * - `tsconfig.json` missing or unreadable → `Optional.empty()` (caller treats `@/` as bare → FAIL)
 * - No `@/*` entry in `compilerOptions.paths` → `Optional.empty()`
 * - Multi-element array → only first element used (regex matches first `"..."` after `[`)
 * - Other `paths` entries (e.g., `#utils`) → ignored; only `@/*` is extracted
 * - Uses simple string/regex parsing (no Jackson in kernel)
 */
public class NodePathAliasResolver {

    // Regex to extract compilerOptions.paths["@/*"][0] value
    // Matches: "@/*" : [ "./src/*" ... ]
    // Captures the first string value in the array
    private static final Pattern AT_ALIAS_PATTERN = Pattern.compile(
        "\"@/\\*\"\\s*:\\s*\\[\\s*\"([^\"]+)\""
    );

    private Optional<String> cachedAlias = null;
    private Path cachedProjectRoot = null;

    /**
     * Resolves a specifier using the @/* alias from tsconfig.json.
     * 
     * @param specifier the import specifier (e.g., "@/foo/bar")
     * @param projectRoot the project root directory
     * @return Optional containing the resolved path, or empty if not resolvable
     */
    public Optional<Path> resolve(String specifier, Path projectRoot) {
        // Only handle @/ prefixed specifiers
        if (!specifier.startsWith("@/")) {
            return Optional.empty();
        }

        Optional<String> alias = getAlias(projectRoot);
        if (alias.isEmpty()) {
            return Optional.empty();
        }

        // alias is e.g. "./src/*" → strip trailing "*" → "./src/"
        String aliasValue = alias.get();
        String prefix;
        if (aliasValue.endsWith("*")) {
            prefix = aliasValue.substring(0, aliasValue.length() - 1);
        } else {
            prefix = aliasValue;
        }

        // suffix = specifier.removePrefix("@/")  // "foo/bar"
        String suffix = specifier.substring(2); // Remove "@/"

        // resolved = projectRoot.resolve(prefix + suffix).normalize()
        Path resolved = projectRoot.resolve(prefix + suffix).normalize();
        return Optional.of(resolved);
    }

    /**
     * Gets the @/* alias value from tsconfig.json, caching the result.
     * 
     * @param projectRoot the project root directory
     * @return Optional containing the alias value (e.g., "./src/*"), or empty if not found
     */
    public Optional<String> getAlias(Path projectRoot) {
        // Return cached value if available for the same project root
        if (cachedAlias != null && projectRoot.equals(cachedProjectRoot)) {
            return cachedAlias;
        }

        cachedProjectRoot = projectRoot;
        cachedAlias = readAliasFromTsconfig(projectRoot);
        return cachedAlias;
    }

    private Optional<String> readAliasFromTsconfig(Path projectRoot) {
        Path tsconfig = projectRoot.resolve("tsconfig.json");

        // tsconfig.json missing → Optional.empty()
        if (!Files.exists(tsconfig)) {
            return Optional.empty();
        }

        String json;
        try {
            json = Files.readString(tsconfig, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // tsconfig.json unreadable → Optional.empty()
            return Optional.empty();
        }

        // Extract compilerOptions.paths["@/*"][0] via regex
        Matcher matcher = AT_ALIAS_PATTERN.matcher(json);
        if (!matcher.find()) {
            // No @/* entry in compilerOptions.paths → Optional.empty()
            return Optional.empty();
        }

        // Return the first captured group (the alias value)
        return Optional.of(matcher.group(1));
    }
}
