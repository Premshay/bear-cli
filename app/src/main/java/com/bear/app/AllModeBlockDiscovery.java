package com.bear.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class AllModeBlockDiscovery {
    private AllModeBlockDiscovery() {
    }

    static List<BlockIndexEntry> selectBlocks(BlockIndex index, Set<String> onlyNames) {
        List<BlockIndexEntry> sorted = new ArrayList<>(index.blocks());
        sorted.sort(Comparator.comparing(BlockIndexEntry::name));
        if (onlyNames == null || onlyNames.isEmpty()) {
            return sorted;
        }
        Set<String> known = new HashSet<>();
        for (BlockIndexEntry entry : sorted) {
            known.add(entry.name());
        }
        for (String name : onlyNames) {
            if (!known.contains(name)) {
                return null;
            }
        }
        List<BlockIndexEntry> selected = new ArrayList<>();
        for (BlockIndexEntry entry : sorted) {
            if (onlyNames.contains(entry.name())) {
                selected.add(entry);
            }
        }
        return selected;
    }

    static List<String> computeOrphanMarkersRepoWide(Path repoRoot, BlockIndex index) throws IOException {
        Set<String> expected = new HashSet<>();
        for (BlockIndexEntry entry : index.blocks()) {
            if (!entry.enabled()) {
                continue;
            }
            expected.add(Path.of(entry.projectRoot())
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("surfaces")
                .resolve(entry.name() + ".surface.json")
                .normalize()
                .toString()
                .replace('\\', '/'));
        }

        List<String> found = new ArrayList<>();
        try (var stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                if (rel.contains("build/generated/bear/surfaces/") && rel.endsWith(".surface.json")) {
                    found.add(rel);
                }
            });
        }
        found.sort(String::compareTo);
        List<String> orphan = new ArrayList<>();
        for (String marker : found) {
            if (!expected.contains(marker)) {
                orphan.add(marker);
            }
        }
        return orphan;
    }

    static List<String> computeOrphanMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) throws IOException {
        Map<String, Set<String>> expectedByRoot = new TreeMap<>();
        for (BlockIndexEntry entry : selected) {
            if (!entry.enabled()) {
                continue;
            }
            expectedByRoot.computeIfAbsent(entry.projectRoot(), ignored -> new HashSet<>())
                .add(entry.name() + ".surface.json");
        }

        List<String> orphan = new ArrayList<>();
        for (Map.Entry<String, Set<String>> rootEntry : expectedByRoot.entrySet()) {
            Path surfacesDir = repoRoot.resolve(rootEntry.getKey())
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("surfaces");
            if (!Files.isDirectory(surfacesDir)) {
                continue;
            }
            try (var stream = Files.list(surfacesDir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (!fileName.endsWith(".surface.json")) {
                        return;
                    }
                    if (!rootEntry.getValue().contains(fileName)) {
                        String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                        orphan.add(rel);
                    }
                });
            }
        }
        orphan.sort(String::compareTo);
        return orphan;
    }

    static List<String> computeLegacyMarkersRepoWide(Path repoRoot) throws IOException {
        List<String> legacy = new ArrayList<>();
        try (var stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                if (rel.endsWith("build/generated/bear/bear.surface.json")) {
                    legacy.add(rel);
                }
            });
        }
        legacy.sort(String::compareTo);
        return legacy;
    }

    static List<String> computeLegacyMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) {
        Set<String> managedRoots = new HashSet<>();
        for (BlockIndexEntry entry : selected) {
            if (entry.enabled()) {
                managedRoots.add(entry.projectRoot());
            }
        }
        List<String> legacy = new ArrayList<>();
        for (String root : managedRoots) {
            Path marker = repoRoot.resolve(root)
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("bear.surface.json");
            if (Files.isRegularFile(marker)) {
                legacy.add(repoRoot.relativize(marker).toString().replace('\\', '/'));
            }
        }
        legacy.sort(String::compareTo);
        return legacy;
    }
}
