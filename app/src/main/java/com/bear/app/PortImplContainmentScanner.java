package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PortImplContainmentScanner {
    private static final Pattern PACKAGE_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;"
    );
    private static final Pattern IMPORT_DECL_PATTERN = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;"
    );
    private static final Pattern IMPLEMENTS_DECL_PATTERN = Pattern.compile(
        "\\b(?:class|record|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b[^\\{;]*\\bimplements\\s+([^\\{]+)\\{",
        Pattern.DOTALL
    );

    private PortImplContainmentScanner() {
    }

    static List<PortImplContainmentFinding> scanPortImplOutsideGovernedRoots(
        Path projectRoot,
        List<WiringManifest> manifests
    ) throws IOException {
        if (manifests == null || manifests.isEmpty()) {
            return List.of();
        }
        TreeSet<String> governedRoots = new TreeSet<>();
        for (WiringManifest manifest : manifests) {
            if (manifest.governedSourceRoots() == null) {
                continue;
            }
            for (String root : manifest.governedSourceRoots()) {
                String normalized = normalizeRepoPath(root);
                if (normalized != null && !normalized.isBlank()) {
                    governedRoots.add(normalized);
                }
            }
        }
        if (governedRoots.isEmpty()) {
            return List.of();
        }

        Path srcMainJava = projectRoot.resolve("src/main/java").normalize();
        if (!Files.isDirectory(srcMainJava)) {
            return List.of();
        }

        ArrayList<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(srcMainJava, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        javaFiles.sort(Comparator.comparing(path -> projectRoot.relativize(path).toString().replace('\\', '/')));

        ArrayList<PortImplContainmentFinding> findings = new ArrayList<>();
        for (Path file : javaFiles) {
            String relPath = projectRoot.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
            String packageName = parsePackageName(sanitized);
            Map<String, String> explicitImports = parseExplicitImports(sanitized);

            Matcher matcher = IMPLEMENTS_DECL_PATTERN.matcher(sanitized);
            while (matcher.find()) {
                String className = matcher.group(1).trim();
                String interfacesRaw = matcher.group(2).trim();
                if (interfacesRaw.isEmpty()) {
                    continue;
                }
                String implClassFqcn = packageName.isBlank() ? className : packageName + "." + className;
                for (String token : splitInterfaces(interfacesRaw)) {
                    String resolvedInterface = resolveInterfaceFqcn(token, explicitImports);
                    if (resolvedInterface == null || !isGeneratedPortInterface(resolvedInterface)) {
                        continue;
                    }
                    if (!isUnderAnyGovernedRoot(relPath, governedRoots)) {
                        findings.add(new PortImplContainmentFinding(resolvedInterface, implClassFqcn, relPath));
                    }
                }
            }
        }

        findings.sort(
            Comparator.comparing(PortImplContainmentFinding::interfaceFqcn)
                .thenComparing(PortImplContainmentFinding::implClassFqcn)
                .thenComparing(PortImplContainmentFinding::path)
        );
        return findings;
    }

    private static String parsePackageName(String source) {
        Matcher matcher = PACKAGE_DECL_PATTERN.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }

    private static Map<String, String> parseExplicitImports(String source) {
        HashMap<String, String> importsBySimple = new HashMap<>();
        Matcher matcher = IMPORT_DECL_PATTERN.matcher(source);
        while (matcher.find()) {
            String importFqcn = matcher.group(1).trim();
            if (importFqcn.endsWith(".*")) {
                continue;
            }
            int idx = importFqcn.lastIndexOf('.');
            if (idx <= 0 || idx == importFqcn.length() - 1) {
                continue;
            }
            importsBySimple.putIfAbsent(importFqcn.substring(idx + 1), importFqcn);
        }
        return importsBySimple;
    }

    private static List<String> splitInterfaces(String payload) {
        ArrayList<String> interfaces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int genericDepth = 0;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == '<') {
                genericDepth++;
                current.append(c);
                continue;
            }
            if (c == '>') {
                if (genericDepth > 0) {
                    genericDepth--;
                }
                current.append(c);
                continue;
            }
            if (c == ',' && genericDepth == 0) {
                String token = normalizeInterfaceToken(current.toString());
                if (!token.isBlank()) {
                    interfaces.add(token);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String token = normalizeInterfaceToken(current.toString());
        if (!token.isBlank()) {
            interfaces.add(token);
        }
        return interfaces;
    }

    private static String normalizeInterfaceToken(String raw) {
        String token = raw.trim();
        while (token.startsWith("@")) {
            int split = token.indexOf(' ');
            if (split < 0) {
                return "";
            }
            token = token.substring(split + 1).trim();
        }
        token = stripGenerics(token);
        int whitespace = token.indexOf(' ');
        if (whitespace >= 0) {
            token = token.substring(0, whitespace);
        }
        return token.trim();
    }

    private static String stripGenerics(String token) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '<') {
                depth++;
                continue;
            }
            if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String resolveInterfaceFqcn(String token, Map<String, String> explicitImports) {
        if (token.isBlank()) {
            return null;
        }
        if (token.contains(".")) {
            return token;
        }
        return explicitImports.get(token);
    }

    private static boolean isGeneratedPortInterface(String fqcn) {
        if (!fqcn.startsWith("com.bear.generated.")) {
            return false;
        }
        int idx = fqcn.lastIndexOf('.');
        if (idx < 0 || idx == fqcn.length() - 1) {
            return false;
        }
        String simple = fqcn.substring(idx + 1);
        return simple.endsWith("Port");
    }

    private static boolean isUnderAnyGovernedRoot(String relPath, Set<String> governedRoots) {
        for (String root : governedRoots) {
            if (relPath.equals(root) || relPath.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRepoPath(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
