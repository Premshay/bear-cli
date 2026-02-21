package com.bear.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

final class DriftAnalyzer {
    private DriftAnalyzer() {
    }

    static List<DriftItem> computeDrift(
        Path baselineRoot,
        Path candidateRoot,
        Predicate<String> includePath
    ) throws IOException {
        Map<String, byte[]> baseline = readRegularFiles(baselineRoot);
        Map<String, byte[]> candidate = readRegularFiles(candidateRoot);

        TreeSet<String> allPaths = new TreeSet<>();
        allPaths.addAll(baseline.keySet());
        allPaths.addAll(candidate.keySet());

        List<DriftItem> drift = new java.util.ArrayList<>();
        for (String path : allPaths) {
            if (!includePath.test(path)) {
                continue;
            }
            boolean inBaseline = baseline.containsKey(path);
            boolean inCandidate = candidate.containsKey(path);
            if (inBaseline && !inCandidate) {
                drift.add(new DriftItem(path, DriftType.ADDED));
                continue;
            }
            if (!inBaseline && inCandidate) {
                drift.add(new DriftItem(path, DriftType.REMOVED));
                continue;
            }
            if (!Arrays.equals(baseline.get(path), candidate.get(path))) {
                drift.add(new DriftItem(path, DriftType.CHANGED));
            }
        }

        drift.sort(Comparator
            .comparing(DriftItem::path)
            .thenComparing(item -> item.type().order));
        return drift;
    }

    static Map<String, byte[]> readRegularFiles(Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    files.put(rel, Files.readAllBytes(path));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
        return files;
    }

    static boolean hasOwnedBaselineFiles(Path baselineRoot, Set<String> ownedPrefixes, String markerRelPath) throws IOException {
        if (!Files.isDirectory(baselineRoot)) {
            return false;
        }
        Path marker = baselineRoot.resolve(markerRelPath);
        if (Files.isRegularFile(marker)) {
            return true;
        }
        try (var stream = Files.walk(baselineRoot)) {
            return stream.filter(Files::isRegularFile)
                .map(path -> baselineRoot.relativize(path).toString().replace('\\', '/'))
                .anyMatch(path -> startsWithAny(path, ownedPrefixes));
        }
    }

    static boolean startsWithAny(String value, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
