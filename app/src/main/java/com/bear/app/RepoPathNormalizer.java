package com.bear.app;

import java.nio.file.Path;

final class RepoPathNormalizer {
    private RepoPathNormalizer() {
    }

    static String normalizePathForIdentity(Path path) {
        if (path == null) {
            return "";
        }
        return normalizePathForIdentity(path.toString());
    }

    static String normalizePathForIdentity(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.endsWith("/") && !isRootSentinel(normalized)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String normalizePathForPrefix(Path path) {
        if (path == null) {
            return "";
        }
        return normalizePathForPrefix(path.toString());
    }

    static String normalizePathForPrefix(String rawPath) {
        String normalized = normalizePathForIdentity(rawPath);
        if (normalized.isBlank() || isRootSentinel(normalized)) {
            return normalized;
        }
        return normalized + "/";
    }

    static boolean hasSegmentPrefix(String pathIdentity, String prefixIdentityOrPrefix) {
        String normalizedPath = normalizePathForIdentity(pathIdentity);
        String normalizedPrefix = normalizePathForPrefix(prefixIdentityOrPrefix);
        if (normalizedPath.isBlank() || normalizedPrefix.isBlank()) {
            return false;
        }
        String prefixIdentity = stripTrailingSlash(normalizedPrefix);
        if (normalizedPath.equals(prefixIdentity)) {
            return true;
        }
        return normalizedPath.startsWith(normalizedPrefix);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.endsWith("/") && !isRootSentinel(value)) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isRootSentinel(String normalizedPath) {
        if ("/".equals(normalizedPath)) {
            return true;
        }
        return normalizedPath.matches("^[A-Za-z]:/$");
    }
}
