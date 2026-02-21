package com.bear.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

final class AllModeOptionParser {
    private AllModeOptionParser() {
    }

    static AllCheckOptions parseAllCheckOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        boolean failFast = false;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear check --all --project <repoRoot>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--fail-fast" -> failFast = true;
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear check --all --project <repoRoot>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check --all --project <repoRoot>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllCheckOptions(repoRoot, blocksPath, onlyNames, failFast, strictOrphans);
    }

    static AllFixOptions parseAllFixOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        boolean failFast = false;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear fix --all --project <repoRoot>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--fail-fast" -> failFast = true;
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear fix --all --project <repoRoot>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix --all --project <repoRoot>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllFixOptions(repoRoot, blocksPath, onlyNames, failFast, strictOrphans);
    }

    static AllPrCheckOptions parseAllPrCheckOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        String baseRef = null;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear pr-check --all --project <repoRoot> --base <ref>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--base" -> {
                    if (i + 1 >= args.length) {
                        BearCli.failWithLegacy(
                            err,
                            CliCodes.EXIT_USAGE,
                            "usage: INVALID_ARGS: expected value after --base",
                            CliCodes.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a base ref after `--base`."
                        );
                        return null;
                    }
                    baseRef = args[++i];
                }
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null || baseRef == null || baseRef.isBlank()) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check --all --project <repoRoot> --base <ref>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check --all --project <repoRoot> --base <ref>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllPrCheckOptions(repoRoot, blocksPath, onlyNames, strictOrphans, baseRef);
    }

    static Path resolveBlocksPath(Path repoRoot, String blocksArg) {
        if (blocksArg == null) {
            return repoRoot.resolve("bear.blocks.yaml").normalize();
        }
        Path relative = Path.of(blocksArg).normalize();
        if (relative.isAbsolute() || relative.toString().isBlank() || relative.startsWith("..")) {
            throw new IllegalArgumentException("blocks path must be repo-relative");
        }
        Path resolved = repoRoot.resolve(relative).normalize();
        if (!resolved.startsWith(repoRoot)) {
            throw new IllegalArgumentException("blocks path must be repo-relative");
        }
        return resolved;
    }

    static Set<String> parseOnlyNames(String onlyArg) {
        if (onlyArg == null) {
            return Set.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : onlyArg.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("--only requires at least one block name");
        }
        return Set.copyOf(names);
    }

    static Set<String> findUnknownOnlyNames(Set<String> selectedNames, Set<String> knownNames) {
        HashSet<String> unknown = new HashSet<>(selectedNames);
        unknown.removeAll(knownNames);
        return unknown;
    }
}
