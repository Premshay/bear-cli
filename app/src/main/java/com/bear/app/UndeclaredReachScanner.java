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
import java.util.List;
import java.util.regex.Pattern;

final class UndeclaredReachScanner {
    private static final List<UndeclaredReachSurface> UNDECLARED_REACH_SURFACES = List.of(
        new UndeclaredReachSurface("java.net.http.HttpClient", Pattern.compile("\\bjava\\.net\\.http\\.HttpClient\\b")),
        new UndeclaredReachSurface("java.net.URL#openConnection", Pattern.compile("\\bjava\\.net\\.URL\\b(?s).*\\bopenConnection\\s*\\(")),
        new UndeclaredReachSurface("okhttp3.OkHttpClient", Pattern.compile("\\bokhttp3\\.OkHttpClient\\b")),
        new UndeclaredReachSurface(
            "org.springframework.web.client.RestTemplate",
            Pattern.compile("\\borg\\.springframework\\.web\\.client\\.RestTemplate\\b")
        ),
        new UndeclaredReachSurface("java.net.HttpURLConnection", Pattern.compile("\\bjava\\.net\\.HttpURLConnection\\b"))
    );

    private UndeclaredReachScanner() {
    }

    static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException {
        List<UndeclaredReachFinding> findings = new ArrayList<>();
        if (!Files.isDirectory(projectRoot)) {
            return findings;
        }
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = projectRoot.relativize(file).toString().replace('\\', '/');
                if (!rel.endsWith(".java") || isUndeclaredReachExcluded(rel)) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (UndeclaredReachSurface surface : UNDECLARED_REACH_SURFACES) {
                    if (surface.matches(content)) {
                        findings.add(new UndeclaredReachFinding(rel, surface.label()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        findings.sort(
            Comparator.comparing(UndeclaredReachFinding::path)
                .thenComparing(UndeclaredReachFinding::surface)
        );
        return findings;
    }

    static boolean isUndeclaredReachExcluded(String relPath) {
        return relPath.startsWith("build/")
            || relPath.startsWith(".gradle/")
            || relPath.startsWith("src/test/")
            || relPath.startsWith("build/generated/bear/");
    }
}
