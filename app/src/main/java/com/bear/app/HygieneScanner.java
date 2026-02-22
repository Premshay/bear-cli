package com.bear.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class HygieneScanner {
    private static final List<String> UNEXPECTED_PATH_SEEDS = List.of(
        ".g",
        ".gradle-user"
    );
    private static final Set<String> ALWAYS_EXCLUDED = Set.of(
        ".git",
        ".github",
        "build",
        ".gradle",
        "out",
        "target",
        ".bear-gradle-user-home"
    );

    private HygieneScanner() {
    }

    static List<String> scanUnexpectedPaths(Path projectRoot, Set<String> allowlist) {
        ArrayList<String> findings = new ArrayList<>();
        for (String candidate : UNEXPECTED_PATH_SEEDS) {
            if (ALWAYS_EXCLUDED.contains(candidate) || allowlist.contains(candidate)) {
                continue;
            }
            Path path = projectRoot.resolve(candidate).normalize();
            if (Files.exists(path)) {
                findings.add(candidate);
            }
        }
        findings.sort(String::compareTo);
        return List.copyOf(findings);
    }
}
