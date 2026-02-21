package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.ir.BearIrYamlEmitter;
import com.bear.kernel.target.JvmTarget;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BearCli {
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "record", "sealed", "permits", "var", "yield", "non-sealed"
    );
    private static final String CHECK_BLOCKED_MARKER_RELATIVE = "build/bear/check.blocked.marker";
    private static final String CHECK_BLOCKED_REASON_LOCK = "LOCK";
    private static final String CHECK_BLOCKED_REASON_BOOTSTRAP = "BOOTSTRAP_IO";
    private static final Pattern DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile(
        "\\bimport\\s+blocks(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.impl\\.[A-Za-z_][A-Za-z0-9_]*Impl\\s*;"
    );
    private static final Pattern DIRECT_IMPL_NEW_PATTERN = Pattern.compile(
        "\\bnew\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\("
    );
    private static final Pattern DIRECT_IMPL_TYPE_CAST_PATTERN = Pattern.compile(
        "\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\)"
    );
    private static final Pattern DIRECT_IMPL_VAR_DECL_PATTERN = Pattern.compile(
        "(?m)\\b(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:[=;,)])"
    );
    private static final Pattern DIRECT_IMPL_EXTENDS_IMPL_PATTERN = Pattern.compile(
        "\\bextends\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN = Pattern.compile(
        "\\bimplements\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile("(?m)^\\s*//\\s*BEAR:PORT_USED\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");

    private BearCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String command = args.length == 0 ? "help" : args[0];
        return switch (command) {
            case "help", "-h", "--help" -> {
                printUsage(out);
                yield ExitCode.OK;
            }
            case "validate" -> runValidate(args, out, err);
            case "compile" -> runCompile(args, out, err);
            case "fix" -> runFix(args, out, err);
            case "check" -> runCheck(args, out, err);
            case "unblock" -> runUnblock(args, out, err);
            case "pr-check" -> runPrCheck(args, out, err);
            default -> failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: UNKNOWN_COMMAND: unknown command: " + command,
                FailureCode.USAGE_UNKNOWN_COMMAND,
                "cli.command",
                "Run `bear --help` and use a supported command."
            );
        };
    }

    private static int runValidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 2) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear validate <file>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear validate <file>` with exactly one IR file path."
            );
        }

        Path file = Path.of(args[1]);
        try {
            maybeFailInternalForTest("validate");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIrYamlEmitter emitter = new BearIrYamlEmitter();

            BearIr ir = parser.parse(file);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);

            out.print(emitter.toCanonicalYaml(normalized));
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear validate <file>`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + file,
                FailureCode.IO_ERROR,
                "input.ir",
                "Ensure the IR file exists and is readable, then rerun `bear validate <file>`."
            );
        } catch (Exception e) {
            return failWithLegacy(
                err,
                ExitCode.INTERNAL,
                "internal: INTERNAL_ERROR:",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        }
    }

    private static int runCompile(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear compile <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);

        try {
            maybeFailInternalForTest("compile");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            target.compile(normalized, projectRoot);

            out.println("compiled: OK");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear compile <ir-file> --project <path>`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + e.getMessage(),
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear compile`."
            );
        } catch (Exception e) {
            return failWithLegacy(
                err,
                ExitCode.INTERNAL,
                "internal: INTERNAL_ERROR:",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        }
    }

    private static int runFix(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runFixAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear fix <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        FixResult result = executeFix(irFile, projectRoot, null);
        return emitFixResult(result, out, err);
    }

    private static int runFixAll(String[] args, PrintStream out, PrintStream err) {
        AllFixOptions options = parseAllFixOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear fix --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear fix --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "fix: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear fix --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "fix: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "fix: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear fix --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            FixResult fixResult = executeFix(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                block.name()
            );
            BlockExecutionResult blockResult = toFixBlockResult(block, fixResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
        }

        RepoAggregationResult summary = aggregateFixResults(blockResults, failFastTriggered);
        List<String> lines = renderFixAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int runCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runCheckAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear check <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        CheckBlockedState blockedState = readCheckBlockedState(projectRoot);
        if (blockedState.blocked()) {
            String line = "check: IO_ERROR: CHECK_BLOCKED: " + blockedState.summary();
            return failWithLegacy(
                err,
                ExitCode.IO,
                line,
                FailureCode.IO_ERROR,
                CHECK_BLOCKED_MARKER_RELATIVE,
                "Run `bear unblock --project <path>` after fixing lock/bootstrap IO and rerun `bear check`."
            );
        }
        CheckResult result = executeCheck(irFile, projectRoot, true, null);
        return emitCheckResult(result, out, err);
    }

    private static int runUnblock(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }
        if (args.length != 3 || !"--project".equals(args[1])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear unblock --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear unblock --project <path>` with the expected arguments."
            );
        }
        Path projectRoot = Path.of(args[2]);
        try {
            clearCheckBlockedMarker(projectRoot);
            out.println("unblock: OK");
            return ExitCode.OK;
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                CHECK_BLOCKED_MARKER_RELATIVE,
                "Ensure the project path is writable, then rerun `bear unblock --project <path>`."
            );
        }
    }

    private static int runPrCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }
        if (args.length >= 2 && "--all".equals(args[1])) {
            return runPrCheckAll(args, out, err);
        }
        if (args.length != 6 || !"--project".equals(args[2]) || !"--base".equals(args[4])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check <ir-file> --project <path> --base <ref>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check <ir-file> --project <path> --base <ref>` with the expected arguments."
            );
        }

        String irArg = args[1];
        Path irPath = Path.of(irArg);
        if (irPath.isAbsolute()) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        Path normalizedRelative = irPath.normalize();
        if (normalizedRelative.startsWith("..") || normalizedRelative.toString().isBlank()) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }

        Path projectRoot = Path.of(args[3]).toAbsolutePath().normalize();
        String baseRef = args[5];
        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
        if (!headIrPath.startsWith(projectRoot)) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        PrCheckResult result = executePrCheck(projectRoot, repoRelativePath, baseRef);
        return emitPrCheckResult(result, out, err);
    }

    private static int runCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllCheckOptions options = parseAllCheckOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear check --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear check --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        TreeSet<String> managedRoots = new TreeSet<>();
        for (BlockIndexEntry entry : selected) {
            if (!entry.enabled()) {
                continue;
            }
            managedRoots.add(entry.projectRoot());
        }
        for (String managedRoot : managedRoots) {
            Path root = options.repoRoot().resolve(managedRoot).normalize();
            CheckBlockedState blockedState = readCheckBlockedState(root);
            if (!blockedState.blocked()) {
                continue;
            }
            String markerPath = options.repoRoot()
                .relativize(root.resolve(CHECK_BLOCKED_MARKER_RELATIVE))
                .toString()
                .replace('\\', '/');
            return failWithLegacy(
                err,
                ExitCode.IO,
                "check: IO_ERROR: CHECK_BLOCKED: " + managedRoot + ": " + blockedState.summary(),
                FailureCode.IO_ERROR,
                markerPath,
                "Run `bear unblock --project <path>` after fixing lock/bootstrap IO and rerun `bear check --all`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "check: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear check --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            CheckResult checkResult = executeCheck(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                false,
                block.name()
            );
            BlockExecutionResult blockResult = toCheckBlockResult(block, checkResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
        }

        Map<String, List<Integer>> rootPassIndexes = new TreeMap<>();
        for (int i = 0; i < blockResults.size(); i++) {
            BlockExecutionResult blockResult = blockResults.get(i);
            if (blockResult.status() != BlockStatus.PASS) {
                continue;
            }
            rootPassIndexes.computeIfAbsent(blockResult.project(), ignored -> new ArrayList<>()).add(i);
        }

        int rootReachFailed = 0;
        int rootTestFailed = 0;
        int rootTestSkippedDueToReach = 0;
        for (Map.Entry<String, List<Integer>> entry : rootPassIndexes.entrySet()) {
            Path root = options.repoRoot().resolve(entry.getKey()).normalize();
            try {
                List<UndeclaredReachFinding> undeclaredReach = scanUndeclaredReach(root);
                if (!undeclaredReach.isEmpty()) {
                    rootReachFailed++;
                    rootTestSkippedDueToReach++;
                    String locator = undeclaredReach.get(0).path();
                    String detail = "root-level undeclared reach in projectRoot " + entry.getKey();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.UNDECLARED_REACH,
                            "UNDECLARED_REACH",
                            FailureCode.UNDECLARED_REACH,
                            locator,
                            detail,
                            "Declare a port/op in IR, run bear compile, and route call through generated port interface."
                        ));
                    }
                    continue;
                }

                List<WiringManifest> wiringManifests = new ArrayList<>();
                for (int idx : entry.getValue()) {
                    String blockKey = blockResults.get(idx).name();
                    Path wiringPath = root.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
                    wiringManifests.add(parseWiringManifest(wiringPath));
                }
                List<BoundaryBypassFinding> bypassFindings = scanBoundaryBypass(root, wiringManifests);
                if (!bypassFindings.isEmpty()) {
                    BoundaryBypassFinding first = bypassFindings.get(0);
                    String firstLine = "check: BOUNDARY_BYPASS: RULE=" + first.rule() + ": " + first.path() + ": " + first.detail();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.BOUNDARY_BYPASS,
                            "BOUNDARY_BYPASS",
                            FailureCode.BOUNDARY_BYPASS,
                            first.path(),
                            firstLine,
                            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
                        ));
                    }
                    continue;
                }

                ProjectTestResult testResult = runProjectTests(root);
                if (testResult.status() == ProjectTestStatus.LOCKED) {
                    String lockLine = testResult.firstLockLine() != null
                        ? testResult.firstLockLine()
                        : firstGradleLockLine(testResult.output());
                    String markerWriteSuffix = "";
                    try {
                        writeCheckBlockedMarker(root, CHECK_BLOCKED_REASON_LOCK, lockLine);
                    } catch (IOException markerWriteError) {
                        markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                    }
                    String detail = projectTestDetail(
                        "root-level project test runner lock in projectRoot " + entry.getKey(),
                        lockLine,
                        null
                    );
                    String attemptsSuffix = attemptTrailSuffix(testResult.attemptTrail());
                    if (!attemptsSuffix.isBlank()) {
                        detail += attemptsSuffix;
                    }
                    if (!markerWriteSuffix.isBlank()) {
                        detail += markerWriteSuffix;
                    }
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.IO,
                            "IO_ERROR",
                            FailureCode.IO_ERROR,
                            "project.tests",
                            detail,
                            "Release Gradle wrapper lock or set isolated GRADLE_USER_HOME, then rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.BOOTSTRAP_IO) {
                    String bootstrapLine = testResult.firstBootstrapLine() != null
                        ? testResult.firstBootstrapLine()
                        : firstGradleBootstrapIoLine(testResult.output());
                    String markerWriteSuffix = "";
                    try {
                        writeCheckBlockedMarker(root, CHECK_BLOCKED_REASON_BOOTSTRAP, bootstrapLine);
                    } catch (IOException markerWriteError) {
                        markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                    }
                    String detail = projectTestDetail(
                        "root-level project test bootstrap IO failure in projectRoot " + entry.getKey(),
                        bootstrapLine,
                        shortTailSummary(testResult.output(), 3)
                    );
                    String attemptsSuffix = attemptTrailSuffix(testResult.attemptTrail());
                    if (!attemptsSuffix.isBlank()) {
                        detail += attemptsSuffix;
                    }
                    if (!markerWriteSuffix.isBlank()) {
                        detail += markerWriteSuffix;
                    }
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.IO,
                            "IO_ERROR",
                            FailureCode.IO_ERROR,
                            "project.tests",
                            detail,
                            "Fix Gradle wrapper bootstrap/cache (distribution zip/unzip) and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.FAILED) {
                    rootTestFailed++;
                    String detail = projectTestDetail(
                        "root-level project tests failed for projectRoot " + entry.getKey(),
                        firstRelevantProjectTestFailureLine(testResult.output()),
                        shortTailSummary(testResult.output(), 3)
                    );
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.TEST_FAILURE,
                            "TEST_FAILURE",
                            FailureCode.TEST_FAILURE,
                            "project.tests",
                            detail,
                            "Fix project tests and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.TIMEOUT) {
                    rootTestFailed++;
                    String detail = projectTestDetail(
                        "root-level project tests timed out for projectRoot " + entry.getKey(),
                        firstRelevantProjectTestFailureLine(testResult.output()),
                        shortTailSummary(testResult.output(), 3)
                    );
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.TEST_FAILURE,
                            "TEST_FAILURE",
                            FailureCode.TEST_TIMEOUT,
                            "project.tests",
                            detail,
                            "Reduce test runtime or increase timeout, then rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.PASSED) {
                    clearCheckBlockedMarker(root);
                }
            } catch (IOException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, rootFailure(
                        blockResults.get(idx),
                        ExitCode.IO,
                        "IO_ERROR",
                        FailureCode.IO_ERROR,
                        "project.root",
                        "io: IO_ERROR: " + squash(e.getMessage()),
                        "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check --all`."
                    ));
                }
            } catch (ManifestParseException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, rootFailure(
                        blockResults.get(idx),
                        ExitCode.DRIFT,
                        "DRIFT",
                        FailureCode.DRIFT_MISSING_BASELINE,
                        "build/generated/bear/wiring/" + blockResults.get(idx).name() + ".wiring.json",
                        "drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode(),
                        "Run `bear compile <ir-file> --project <path>`, then rerun `bear check --all`."
                    ));
                }
            } catch (InterruptedException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, rootFailure(
                        blockResults.get(idx),
                        ExitCode.INTERNAL,
                        "INTERNAL_ERROR",
                        FailureCode.INTERNAL_ERROR,
                        "internal",
                        "internal: INTERNAL_ERROR:",
                        "Capture stderr and file an issue against bear-cli."
                    ));
                }
            }
        }

        RepoAggregationResult summary = aggregateCheckResults(
            blockResults,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach
        );
        List<String> lines = renderCheckAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllPrCheckOptions options = parseAllPrCheckOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear pr-check --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear pr-check --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear pr-check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "pr-check: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear pr-check --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            String mappingError = validateIndexIrNameMatch(options.repoRoot().resolve(block.ir()).normalize(), block.name());
            if (mappingError != null) {
                blockResults.add(new BlockExecutionResult(
                    block.name(),
                    block.ir(),
                    block.projectRoot(),
                    BlockStatus.FAIL,
                    ExitCode.VALIDATION,
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    mappingError,
                    "Set `block.name` to match index `name` and rerun `bear pr-check --all`.",
                    null,
                    null,
                    List.of()
                ));
                continue;
            }
            PrCheckResult prResult = executePrCheck(options.repoRoot(), block.ir(), options.baseRef());
            blockResults.add(toPrBlockResult(block, prResult));
        }

        RepoAggregationResult summary = aggregatePrResults(blockResults);
        List<String> lines = renderPrAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int emitCheckResult(CheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static int emitFixResult(FixResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static int emitPrCheckResult(PrCheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static FixResult executeFix(Path irFile, Path projectRoot, String expectedBlockKey) {
        try {
            maybeFailInternalForTest("fix");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            String blockKey = toBlockKey(normalized.block().name());
            if (expectedBlockKey != null && !expectedBlockKey.equals(blockKey)) {
                String line = "schema at block.name: INVALID_VALUE: block name must match index name: " + expectedBlockKey;
                return fixFailure(
                    ExitCode.VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    "Set `block.name` to match index `name` and rerun `bear fix --all`.",
                    line
                );
            }

            target.compile(normalized, projectRoot);
            return new FixResult(ExitCode.OK, List.of("fix: OK"), List.of(), null, null, null, null, null);
        } catch (BearIrValidationException e) {
            return fixFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear fix <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (IOException e) {
            return fixFailure(
                ExitCode.IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear fix`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return fixFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        }
    }

    private static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        String expectedBlockKey
    ) {
        Path baselineRoot = projectRoot.resolve("build").resolve("generated").resolve("bear");
        Path tempRoot = null;
        try {
            maybeFailInternalForTest("check");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            String blockKey = toBlockKey(normalized.block().name());
            if (expectedBlockKey != null && !expectedBlockKey.equals(blockKey)) {
                String line = "schema at block.name: INVALID_VALUE: block name must match index name: " + expectedBlockKey;
                return checkFailure(
                    ExitCode.VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    "Set `block.name` to match index `name` and rerun `bear check --all`.",
                    line
                );
            }
            String packageSegment = toGeneratedPackageSegment(normalized.block().name());
            Set<String> ownedPrefixes = Set.of(
                "src/main/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/",
                "src/test/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/"
            );
            String markerRelPath = "surfaces/" + blockKey + ".surface.json";
            String wiringRelPath = "wiring/" + blockKey + ".wiring.json";
            Path legacyMarkerPath = baselineRoot.resolve("bear.surface.json");
            if (Files.isRegularFile(legacyMarkerPath)) {
                return checkFailure(
                    ExitCode.IO,
                    List.of("check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"),
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    "build/generated/bear/bear.surface.json",
                    "Delete legacy marker and rerun compile for managed blocks, then rerun `bear check`.",
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"
                );
            }

            if (!hasOwnedBaselineFiles(baselineRoot, ownedPrefixes, markerRelPath)) {
                String line = "drift: MISSING_BASELINE: build/generated/bear (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                return checkFailure(
                    ExitCode.DRIFT,
                    List.of(line),
                    "DRIFT",
                    FailureCode.DRIFT_MISSING_BASELINE,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: MISSING_BASELINE: build/generated/bear"
                );
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            target.compile(normalized, tempRoot);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve(markerRelPath);
            Path candidateManifestPath = candidateRoot.resolve(markerRelPath);
            Path baselineWiringPath = baselineRoot.resolve(wiringRelPath);
            Path candidateWiringPath = candidateRoot.resolve(wiringRelPath);

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> diagnostics = new ArrayList<>();
            BoundaryManifest baselineManifest = null;
            if (!Files.isRegularFile(baselineManifestPath)) {
                diagnostics.add("check: BASELINE_MANIFEST_MISSING: " + baselineManifestPath);
            } else {
                try {
                    baselineManifest = parseManifest(baselineManifestPath);
                } catch (ManifestParseException e) {
                    diagnostics.add("check: BASELINE_MANIFEST_INVALID: " + e.reasonCode());
                }
            }
            if (!Files.isRegularFile(candidateManifestPath)) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"
                );
            }
            BoundaryManifest candidateManifest;
            try {
                candidateManifest = parseManifest(candidateManifestPath);
            } catch (ManifestParseException e) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()
                );
            }

            if (!Files.isRegularFile(baselineWiringPath)) {
                String line = "drift: MISSING_BASELINE: build/generated/bear/" + wiringRelPath
                    + " (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                return checkFailure(
                    ExitCode.DRIFT,
                    List.of(line),
                    "DRIFT",
                    FailureCode.DRIFT_MISSING_BASELINE,
                    "build/generated/bear/" + wiringRelPath,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: MISSING_BASELINE: build/generated/bear/" + wiringRelPath
                );
            }
            if (!Files.isRegularFile(candidateWiringPath)) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_MISSING"),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + wiringRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_MISSING"
                );
            }
            WiringManifest baselineWiringManifest;
            WiringManifest candidateWiringManifest;
            try {
                baselineWiringManifest = parseWiringManifest(baselineWiringPath);
            } catch (ManifestParseException e) {
                return checkFailure(
                    ExitCode.DRIFT,
                    List.of("drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode()),
                    "DRIFT",
                    FailureCode.DRIFT_MISSING_BASELINE,
                    "build/generated/bear/" + wiringRelPath,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode()
                );
            }
            try {
                candidateWiringManifest = parseWiringManifest(candidateWiringPath);
            } catch (ManifestParseException e) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_INVALID:" + e.reasonCode()),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + wiringRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_INVALID:" + e.reasonCode()
                );
            }

            List<BoundarySignal> boundarySignals = List.of();
            if (baselineManifest != null) {
                if (!baselineManifest.irHash().equals(candidateManifest.irHash())
                    || !baselineManifest.generatorVersion().equals(candidateManifest.generatorVersion())) {
                    diagnostics.add("check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale");
                }
                boundarySignals = computeBoundarySignals(baselineManifest, candidateManifest);
            }
            for (BoundarySignal signal : boundarySignals) {
                diagnostics.add("boundary: EXPANSION: " + signal.type().label + ": " + signal.key());
            }

            List<DriftItem> drift = computeDrift(
                baselineRoot,
                candidateRoot,
                path -> path.equals(markerRelPath) || path.equals(wiringRelPath) || startsWithAny(path, ownedPrefixes)
            );
            if (!drift.isEmpty()) {
                for (DriftItem item : drift) {
                    diagnostics.add("drift: " + item.type().label + ": " + item.path());
                }
                return checkFailure(
                    ExitCode.DRIFT,
                    diagnostics,
                    "DRIFT",
                    FailureCode.DRIFT_DETECTED,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            CheckResult containmentFailure = verifyContainmentIfRequired(normalized, projectRoot, diagnostics);
            if (containmentFailure != null) {
                return containmentFailure;
            }

            if (!runReachAndTests) {
                return new CheckResult(ExitCode.OK, List.of(), List.of(), null, null, null, null, null);
            }

            List<UndeclaredReachFinding> undeclaredReach = scanUndeclaredReach(projectRoot);
            if (!undeclaredReach.isEmpty()) {
                for (UndeclaredReachFinding finding : undeclaredReach) {
                    diagnostics.add("check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface());
                }
                return checkFailure(
                    ExitCode.UNDECLARED_REACH,
                    diagnostics,
                    "UNDECLARED_REACH",
                    FailureCode.UNDECLARED_REACH,
                    undeclaredReach.get(0).path(),
                    "Declare a port/op in IR, run bear compile, and route call through generated port interface.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            List<BoundaryBypassFinding> bypassFindings = scanBoundaryBypass(projectRoot, List.of(baselineWiringManifest));
            if (!bypassFindings.isEmpty()) {
                for (BoundaryBypassFinding finding : bypassFindings) {
                    diagnostics.add(
                        "check: BOUNDARY_BYPASS: RULE="
                            + finding.rule()
                            + ": "
                            + finding.path()
                            + ": "
                            + finding.detail()
                    );
                }
                return checkFailure(
                    ExitCode.BOUNDARY_BYPASS,
                    diagnostics,
                    "BOUNDARY_BYPASS",
                    FailureCode.BOUNDARY_BYPASS,
                    bypassFindings.get(0).path(),
                    "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            ProjectTestResult testResult = runProjectTests(projectRoot);
            if (testResult.status() == ProjectTestStatus.LOCKED) {
                String lockLine = testResult.firstLockLine() != null
                    ? testResult.firstLockLine()
                    : firstGradleLockLine(testResult.output());
                String markerWriteSuffix = "";
                try {
                    writeCheckBlockedMarker(projectRoot, CHECK_BLOCKED_REASON_LOCK, lockLine);
                } catch (IOException markerWriteError) {
                    markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                }
                String ioLine = lockLine == null
                    ? "io: IO_ERROR: PROJECT_TEST_LOCK: Gradle wrapper lock detected"
                    : "io: IO_ERROR: PROJECT_TEST_LOCK: " + lockLine;
                String attemptsSuffix = attemptTrailSuffix(testResult.attemptTrail());
                if (!attemptsSuffix.isBlank()) {
                    ioLine += attemptsSuffix;
                }
                if (!markerWriteSuffix.isBlank()) {
                    ioLine += markerWriteSuffix;
                }
                diagnostics.add(ioLine);
                diagnostics.addAll(tailLines(testResult.output()));
                return checkFailure(
                    ExitCode.IO,
                    diagnostics,
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    "project.tests",
                    "Release Gradle wrapper lock or set isolated GRADLE_USER_HOME, then rerun `bear check <ir-file> --project <path>`.",
                    ioLine
                );
            }
            if (testResult.status() == ProjectTestStatus.BOOTSTRAP_IO) {
                String bootstrapLine = testResult.firstBootstrapLine() != null
                    ? testResult.firstBootstrapLine()
                    : firstGradleBootstrapIoLine(testResult.output());
                String markerWriteSuffix = "";
                try {
                    writeCheckBlockedMarker(projectRoot, CHECK_BLOCKED_REASON_BOOTSTRAP, bootstrapLine);
                } catch (IOException markerWriteError) {
                    markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                }
                String ioLine = bootstrapLine == null
                    ? "io: IO_ERROR: PROJECT_TEST_BOOTSTRAP: Gradle wrapper bootstrap/unzip failed"
                    : "io: IO_ERROR: PROJECT_TEST_BOOTSTRAP: " + bootstrapLine;
                String attemptsSuffix = attemptTrailSuffix(testResult.attemptTrail());
                if (!attemptsSuffix.isBlank()) {
                    ioLine += attemptsSuffix;
                }
                if (!markerWriteSuffix.isBlank()) {
                    ioLine += markerWriteSuffix;
                }
                diagnostics.add(ioLine);
                diagnostics.addAll(tailLines(testResult.output()));
                return checkFailure(
                    ExitCode.IO,
                    diagnostics,
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    "project.tests",
                    "Fix Gradle wrapper bootstrap/cache (distribution zip/unzip) and rerun `bear check <ir-file> --project <path>`.",
                    ioLine
                );
            }
            if (testResult.status() == ProjectTestStatus.FAILED) {
                diagnostics.add("check: TEST_FAILED: project tests failed");
                diagnostics.addAll(tailLines(testResult.output()));
                return checkFailure(
                    ExitCode.TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    FailureCode.TEST_FAILURE,
                    "project.tests",
                    "Fix project tests and rerun `bear check <ir-file> --project <path>`.",
                    "check: TEST_FAILED: project tests failed"
                );
            }
            if (testResult.status() == ProjectTestStatus.TIMEOUT) {
                String timeoutLine = "check: TEST_TIMEOUT: project tests exceeded " + testTimeoutSeconds() + "s";
                diagnostics.add(timeoutLine);
                diagnostics.addAll(tailLines(testResult.output()));
                return checkFailure(
                    ExitCode.TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    FailureCode.TEST_TIMEOUT,
                    "project.tests",
                    "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`.",
                    timeoutLine
                );
            }

            clearCheckBlockedMarker(projectRoot);
            return new CheckResult(ExitCode.OK, List.of("check: OK"), List.of(), null, null, null, null, null);
        } catch (BearIrValidationException e) {
            return checkFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear check <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (IOException e) {
            return checkFailure(
                ExitCode.IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return checkFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
        Path tempRoot = null;
        try {
            maybeFailInternalForTest("pr-check");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();

            Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
            if (!headIrPath.startsWith(projectRoot) || !Files.isRegularFile(headIrPath)) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath),
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    repoRelativePath,
                    "Ensure the IR file exists at HEAD and rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath,
                    List.of(),
                    false,
                    false
                );
            }

            BearIr head = normalizer.normalize(parseAndValidateIr(parser, validator, headIrPath));

            GitResult isRepoResult = runGitForPrCheck(projectRoot, List.of("rev-parse", "--is-inside-work-tree"), "git.repo");
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
                    "git.repo",
                    "Run `bear pr-check` from a git working tree with a valid project path.",
                    "pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot,
                    List.of(),
                    false,
                    false
                );
            }

            GitResult mergeBaseResult = runGitForPrCheck(projectRoot, List.of("merge-base", "HEAD", baseRef), "git.baseRef");
            if (mergeBaseResult.exitCode() != 0) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref exists and is fetchable, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef,
                    List.of(),
                    false,
                    false
                );
            }
            String mergeBase = mergeBaseResult.stdout().trim();
            if (mergeBase.isBlank()) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base"),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref resolves to a merge base with HEAD, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base",
                    List.of(),
                    false,
                    false
                );
            }

            List<String> stderrLines = new ArrayList<>();
            BearIr base = null;
            GitResult catFileResult = runGitForPrCheck(
                projectRoot,
                List.of("cat-file", "-e", mergeBase + ":" + repoRelativePath),
                repoRelativePath
            );
            if (catFileResult.exitCode() != 0) {
                GitResult existsResult = runGitForPrCheck(
                    projectRoot,
                    List.of("ls-tree", "--name-only", mergeBase, "--", repoRelativePath),
                    repoRelativePath
                );
                if (existsResult.exitCode() != 0) {
                    return prFailure(
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
                if (existsResult.stdout().trim().isEmpty()) {
                    stderrLines.add("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: " + repoRelativePath + ": treated_as_empty_base");
                } else {
                    return prFailure(
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
            } else {
                GitResult showResult = runGitForPrCheck(
                    projectRoot,
                    List.of("show", mergeBase + ":" + repoRelativePath),
                    repoRelativePath
                );
                if (showResult.exitCode() != 0) {
                    return prFailure(
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base IR snapshot is readable from git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
                tempRoot = Files.createTempDirectory("bear-pr-check-");
                Path baseTempIr = tempRoot.resolve("base.bear.yaml");
                Files.writeString(baseTempIr, showResult.stdout(), StandardCharsets.UTF_8);
                base = normalizer.normalize(parseAndValidateIr(parser, validator, baseTempIr));
            }

            List<PrDelta> deltas = computePrDeltas(base, head);
            List<String> deltaLines = new ArrayList<>();
            for (PrDelta delta : deltas) {
                String line = "pr-delta: " + delta.clazz().label + ": " + delta.category().label + ": " + delta.change().label + ": " + delta.key();
                deltaLines.add(line);
                stderrLines.add(line);
            }

            boolean hasBoundary = deltas.stream().anyMatch(delta -> delta.clazz() == PrClass.BOUNDARY_EXPANDING);
            if (hasBoundary) {
                stderrLines.add("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED");
                return prFailure(
                    ExitCode.BOUNDARY_EXPANSION,
                    stderrLines,
                    "BOUNDARY_EXPANSION",
                    FailureCode.BOUNDARY_EXPANSION,
                    repoRelativePath,
                    "Review boundary-expanding deltas and route through explicit boundary review.",
                    "pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED",
                    deltaLines,
                    true,
                    !deltaLines.isEmpty()
                );
            }

            return new PrCheckResult(
                ExitCode.OK,
                List.of("pr-check: OK: NO_BOUNDARY_EXPANSION"),
                stderrLines,
                null,
                null,
                null,
                null,
                null,
                deltaLines,
                false,
                !deltaLines.isEmpty()
            );
        } catch (PrCheckGitException e) {
            return prFailure(
                ExitCode.IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                FailureCode.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            );
        } catch (BearIrValidationException e) {
            return prFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear pr-check`.",
                e.formatLine(),
                List.of(),
                false,
                false
            );
        } catch (IOException e) {
            return prFailure(
                ExitCode.IO,
                List.of("pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage())),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`.",
                "pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()),
                List.of(),
                false,
                false
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return prFailure(
                ExitCode.IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                FailureCode.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            );
        } catch (Exception e) {
            return prFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:",
                List.of(),
                false,
                false
            );
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static CheckResult checkFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new CheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail
        );
    }

    private static PrCheckResult prFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail,
        List<String> deltaLines,
        boolean hasBoundary,
        boolean hasDeltas
    ) {
        return new PrCheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            List.copyOf(deltaLines),
            hasBoundary,
            hasDeltas
        );
    }

    private static FixResult fixFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new FixResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail
        );
    }

    private static List<String> tailLines(String output) {
        return CliText.tailLines(output);
    }

    private static void printLines(PrintStream stream, List<String> lines) {
        CliText.printLines(stream, lines);
    }

    private static AllCheckOptions parseAllCheckOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllCheckOptions(args, err);
    }

    private static AllFixOptions parseAllFixOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllFixOptions(args, err);
    }

    private static AllPrCheckOptions parseAllPrCheckOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllPrCheckOptions(args, err);
    }

    private static Path resolveBlocksPath(Path repoRoot, String blocksArg) {
        return AllModeOptionParser.resolveBlocksPath(repoRoot, blocksArg);
    }

    private static Set<String> parseOnlyNames(String onlyArg) {
        return AllModeOptionParser.parseOnlyNames(onlyArg);
    }

    private static List<BlockIndexEntry> selectBlocks(BlockIndex index, Set<String> onlyNames) {
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

    private static List<String> computeOrphanMarkersRepoWide(Path repoRoot, BlockIndex index) throws IOException {
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

    private static List<String> computeOrphanMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) throws IOException {
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

    private static List<String> computeLegacyMarkersRepoWide(Path repoRoot) throws IOException {
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

    private static List<String> computeLegacyMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) {
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

    private static BlockExecutionResult skipBlock(BlockIndexEntry block, String reason) {
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.SKIP,
            ExitCode.OK,
            null,
            null,
            null,
            null,
            null,
            reason,
            null,
            List.of()
        );
    }

    private static BlockExecutionResult toCheckBlockResult(BlockIndexEntry block, CheckResult result) {
        if (result.exitCode() == ExitCode.OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
            );
        }
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            null,
            List.of()
        );
    }

    private static BlockExecutionResult toFixBlockResult(BlockIndexEntry block, FixResult result) {
        if (result.exitCode() == ExitCode.OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
            );
        }
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            null,
            List.of()
        );
    }

    private static BlockExecutionResult rootFailure(
        BlockExecutionResult base,
        int exitCode,
        String category,
        String blockCode,
        String blockPath,
        String detail,
        String remediation
    ) {
        return new BlockExecutionResult(
            base.name(),
            base.ir(),
            base.project(),
            BlockStatus.FAIL,
            exitCode,
            category,
            blockCode,
            normalizeLocator(blockPath),
            squash(detail),
            remediation,
            null,
            base.classification(),
            base.deltaLines()
        );
    }

    private static String validateIndexIrNameMatch(Path irFile, String expectedBlockKey) {
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIr normalized = normalizer.normalize(parseAndValidateIr(parser, validator, irFile));
            String actualBlockKey = toBlockKey(normalized.block().name());
            if (!expectedBlockKey.equals(actualBlockKey)) {
                return "index name `" + expectedBlockKey + "` does not match IR block.name `" + normalized.block().name() + "`";
            }
            return null;
        } catch (Exception e) {
            return "unable to validate block name mapping: " + squash(e.getMessage());
        }
    }

    private static BlockExecutionResult toPrBlockResult(BlockIndexEntry block, PrCheckResult result) {
        if (result.exitCode() == ExitCode.OK) {
            String classification = result.hasDeltas() ? "ORDINARY" : "NO_CHANGES";
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
                null,
                null,
                null,
                null,
                null,
                null,
                classification,
                result.deltaLines()
            );
        }
        String classification = result.exitCode() == ExitCode.BOUNDARY_EXPANSION ? "BOUNDARY_EXPANDING" : null;
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            classification,
            result.deltaLines()
        );
    }

    private static RepoAggregationResult aggregateCheckResults(
        List<BlockExecutionResult> results,
        boolean failFastTriggered,
        int rootReachFailed,
        int rootTestFailed,
        int rootTestSkippedDueToReach
    ) {
        return AllModeAggregation.aggregateCheckResults(results, failFastTriggered, rootReachFailed, rootTestFailed, rootTestSkippedDueToReach);
    }

    private static RepoAggregationResult aggregatePrResults(List<BlockExecutionResult> results) {
        return AllModeAggregation.aggregatePrResults(results);
    }

    private static RepoAggregationResult aggregateFixResults(List<BlockExecutionResult> results, boolean failFastTriggered) {
        return AllModeAggregation.aggregateFixResults(results, failFastTriggered);
    }

    private static int severityRankCheck(int code) {
        return AllModeAggregation.severityRankCheck(code);
    }

    private static int severityRankPr(int code) {
        return AllModeAggregation.severityRankPr(code);
    }

    private static int severityRankFix(int code) {
        return AllModeAggregation.severityRankFix(code);
    }

    private static List<String> renderCheckAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderCheckAllOutput(results, summary);
    }

    private static List<String> renderPrAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderPrAllOutput(results, summary);
    }

    private static List<String> renderFixAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderFixAllOutput(results, summary);
    }

    private static List<DriftItem> computeDrift(
        Path baselineRoot,
        Path candidateRoot,
        java.util.function.Predicate<String> includePath
    ) throws IOException {
        return DriftAnalyzer.computeDrift(baselineRoot, candidateRoot, includePath);
    }

    private static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        return PrDeltaClassifier.computeBoundarySignals(baseline, candidate);
    }

    private static BearIr parseAndValidateIr(BearIrParser parser, BearIrValidator validator, Path path) throws IOException {
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
    }

    private static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        return PrDeltaClassifier.computePrDeltas(baseIr, headIr);
    }

    private static void addAllowedDepDeltas(List<PrDelta> deltas, Map<String, String> base, Map<String, String> head) {
        PrDeltaClassifier.addAllowedDepDeltas(deltas, base, head);
    }

    private static void addIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.Idempotency base,
        BearIr.Idempotency head
    ) {
        PrDeltaClassifier.addIdempotencyDeltas(deltas, base, head);
    }

    private static void addContractDeltas(
        List<PrDelta> deltas,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        PrDeltaClassifier.addContractDeltas(deltas, base, head, input);
    }

    private static String typeToken(BearIr.FieldType type) {
        return PrDeltaClassifier.typeToken(type);
    }

    private static PrSurface toPrSurface(BearIr ir) {
        return PrDeltaClassifier.toPrSurface(ir);
    }

    private static PrSurface emptyPrSurface() {
        return PrDeltaClassifier.emptyPrSurface();
    }

    private static GitResult runGit(Path projectRoot, List<String> gitArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(projectRoot.toString());
        command.addAll(gitArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IOException("GIT_TIMEOUT: " + String.join(" ", gitArgs));
        }

        String stdout;
        String stderr;
        try (InputStream out = process.getInputStream(); InputStream err = process.getErrorStream()) {
            stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new GitResult(process.exitValue(), normalizeLf(stdout), normalizeLf(stderr));
    }

    private static GitResult runGitForPrCheck(Path projectRoot, List<String> gitArgs, String pathLocator)
        throws PrCheckGitException, InterruptedException {
        try {
            return runGit(projectRoot, gitArgs);
        } catch (IOException e) {
            throw new PrCheckGitException("pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()), pathLocator);
        }
    }

    private static String squash(String text) {
        return CliText.squash(text);
    }

    private static BoundaryManifest parseManifest(Path path) throws IOException, ManifestParseException {
        return ManifestParsers.parseManifest(path);
    }

    private static WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        return ManifestParsers.parseWiringManifest(path);
    }

    private static String extractRequiredString(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractRequiredString(json, key);
    }

    private static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractRequiredArrayPayload(json, key);
    }

    private static String extractOptionalArrayPayload(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractOptionalArrayPayload(json, key);
    }

    private static Map<String, TreeSet<String>> parseCapabilities(String payload) throws ManifestParseException {
        return ManifestParsers.parseCapabilities(payload);
    }

    private static TreeSet<String> parseInvariants(String payload) throws ManifestParseException {
        return ManifestParsers.parseInvariants(payload);
    }

    private static Map<String, String> parseAllowedDeps(String payload) throws ManifestParseException {
        return ManifestParsers.parseAllowedDeps(payload);
    }

    private static List<String> parseStringArray(String payload) throws ManifestParseException {
        return ManifestParsers.parseStringArray(payload);
    }

    private static String jsonUnescape(String value) {
        return ManifestParsers.jsonUnescape(value);
    }

    private static void applyCandidateManifestTestMode(Path candidateManifestPath) throws IOException {
        String mode = System.getProperty("bear.check.test.candidateManifestMode");
        if (mode == null || mode.isBlank()) {
            return;
        }
        if ("missing".equals(mode)) {
            Files.deleteIfExists(candidateManifestPath);
            return;
        }
        if ("invalid".equals(mode)) {
            Files.writeString(candidateManifestPath, "{", StandardCharsets.UTF_8);
        }
    }

    private static CheckResult verifyContainmentIfRequired(BearIr ir, Path projectRoot, List<String> diagnostics) throws IOException {
        if (!hasAllowedDeps(ir)) {
            return null;
        }
        Path gradlew = projectRoot.resolve("gradlew");
        Path gradlewBat = projectRoot.resolve("gradlew.bat");
        boolean hasWrapper = Files.isRegularFile(gradlew) || Files.isRegularFile(gradlewBat);
        if (!hasWrapper) {
            String line = "check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper";
            diagnostics.add(line);
            return checkFailure(
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_UNSUPPORTED_TARGET,
                "project.root",
                "Allowed dependency containment in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps` or use supported target, then rerun `bear check`.",
                line
            );
        }

        Path entrypoint = projectRoot.resolve("build/generated/bear/gradle/bear-containment.gradle");
        if (!Files.isRegularFile(entrypoint)) {
            String line = "check: CONTAINMENT_REQUIRED: SCRIPT_MISSING: build/generated/bear/gradle/bear-containment.gradle";
            diagnostics.add(line);
            return checkFailure(
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
                "build/generated/bear/gradle/bear-containment.gradle",
                "Run `bear compile <ir-file> --project <path>`, ensure Gradle applies the generated containment script, then rerun `bear check`.",
                line
            );
        }

        Path required = projectRoot.resolve("build/generated/bear/config/containment-required.json");
        if (!Files.isRegularFile(required)) {
            String line = "check: CONTAINMENT_REQUIRED: INDEX_MISSING: build/generated/bear/config/containment-required.json";
            diagnostics.add(line);
            return checkFailure(
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
                "build/generated/bear/config/containment-required.json",
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check`.",
                line
            );
        }

        Path marker = projectRoot.resolve("build/bear/containment/applied.marker");
        if (!Files.isRegularFile(marker)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
                "build/bear/containment/applied.marker",
                "Run Gradle build once so BEAR containment compile tasks write markers, then rerun `bear check`.",
                line
            );
        }

        String expectedHash = sha256Hex(Files.readAllBytes(required));
        String markerHash = readMarkerHash(marker);
        if (markerHash == null || !markerHash.equals(expectedHash)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_STALE: build/bear/containment/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
                "build/bear/containment/applied.marker",
                "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                line
            );
        }

        return null;
    }

    private static boolean hasAllowedDeps(BearIr ir) {
        return ir.block().impl() != null
            && ir.block().impl().allowedDeps() != null
            && !ir.block().impl().allowedDeps().isEmpty();
    }

    private static String readMarkerHash(Path markerFile) throws IOException {
        String content = normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("hash=")) {
                String hash = line.substring("hash=".length()).trim();
                return hash.isEmpty() ? null : hash;
            }
        }
        return null;
    }

    private static Map<String, byte[]> readRegularFiles(Path root) throws IOException {
        return DriftAnalyzer.readRegularFiles(root);
    }

    private static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException {
        return UndeclaredReachScanner.scanUndeclaredReach(projectRoot);
    }

    private static boolean isUndeclaredReachExcluded(String relPath) {
        return UndeclaredReachScanner.isUndeclaredReachExcluded(relPath);
    }

    private static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests) throws IOException {
        return BoundaryBypassScanner.scanBoundaryBypass(projectRoot, manifests);
    }

    private static boolean isBoundaryScanExcluded(String relPath) {
        return BoundaryBypassScanner.isBoundaryScanExcluded(relPath);
    }

    private static String firstDirectImplUsageToken(String source) {
        return BoundaryBypassScanner.firstDirectImplUsageToken(source);
    }

    private static String firstTopLevelNullPortWiringToken(
        String source,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        return BoundaryBypassScanner.firstTopLevelNullPortWiringToken(source, governedEntrypointFqcns, governedSimpleNameCounts);
    }

    private static boolean isGovernedEntrypointType(
        String typeName,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        return BoundaryBypassScanner.isGovernedEntrypointType(typeName, governedEntrypointFqcns, governedSimpleNameCounts);
    }

    private static String simpleName(String fqcn) {
        return BoundaryBypassScanner.simpleName(fqcn);
    }

    private static List<String> parseTopLevelArguments(String source, int openParenIndex) {
        return BoundaryBypassScanner.parseTopLevelArguments(source, openParenIndex);
    }

    private static Set<String> parsePortSuppressions(String source) {
        return BoundaryBypassScanner.parsePortSuppressions(source);
    }

    private static boolean referencesPortAsReceiver(String source, String portParam) {
        return BoundaryBypassScanner.referencesPortAsReceiver(source, portParam);
    }

    private static boolean passesPortAsInvocationArgument(String source, String portParam) {
        return BoundaryBypassScanner.passesPortAsInvocationArgument(source, portParam);
    }

    private static String normalizeToken(String token) {
        return BoundaryBypassScanner.normalizeToken(token);
    }

    private static String stripJavaCommentsStringsAndChars(String source) {
        return BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
    }

    private static CheckBlockedState readCheckBlockedState(Path projectRoot) {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
        if (!Files.isRegularFile(marker)) {
            return CheckBlockedState.notBlocked();
        }
        try {
            String content = Files.readString(marker, StandardCharsets.UTF_8);
            String reason = null;
            String detail = null;
            for (String rawLine : normalizeLf(content).lines().toList()) {
                String line = rawLine.trim();
                if (line.startsWith("reason=")) {
                    reason = line.substring("reason=".length()).trim();
                } else if (line.startsWith("detail=")) {
                    detail = line.substring("detail=".length()).trim();
                }
            }
            if (reason == null || reason.isBlank()) {
                reason = "UNKNOWN";
            }
            if (detail == null || detail.isBlank()) {
                detail = "no details";
            }
            return new CheckBlockedState(true, reason, detail);
        } catch (IOException e) {
            return new CheckBlockedState(true, "UNKNOWN", squash(e.getMessage()));
        }
    }

    private static void writeCheckBlockedMarker(Path projectRoot, String reason, String detail) throws IOException {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
        Files.createDirectories(marker.getParent());
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
        String safeDetail = (detail == null || detail.isBlank()) ? "no details" : detail.trim();
        String content = "reason=" + safeReason + "\n" + "detail=" + safeDetail + "\n";
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    private static void clearCheckBlockedMarker(Path projectRoot) throws IOException {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
        Files.deleteIfExists(marker);
    }

    private static boolean hasOwnedBaselineFiles(Path baselineRoot, Set<String> ownedPrefixes, String markerRelPath) throws IOException {
        return DriftAnalyzer.hasOwnedBaselineFiles(baselineRoot, ownedPrefixes, markerRelPath);
    }

    private static boolean startsWithAny(String value, Set<String> prefixes) {
        return DriftAnalyzer.startsWithAny(value, prefixes);
    }

    private static String toBlockKey(String raw) {
        List<String> tokens = splitTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        return String.join("-", tokens);
    }

    private static String toGeneratedPackageSegment(String raw) {
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return "block";
        }
        StringBuilder out = new StringBuilder();
        String[] parts = normalized.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('.');
            }
            String segment = parts[i];
            if (Character.isDigit(segment.charAt(0))) {
                segment = "_" + segment;
            }
            if (JAVA_KEYWORDS.contains(segment)) {
                segment = segment + "_";
            }
            out.append(segment);
        }
        return out.toString();
    }

    private static List<String> splitTokens(String raw) {
        String adjusted = raw.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (adjusted.isEmpty()) {
            return List.of();
        }
        String[] parts = adjusted.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            tokens.add(part.toLowerCase());
        }
        return tokens;
    }

    private static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        return ProjectTestRunner.runProjectTests(projectRoot);
    }

    private static boolean isGradleWrapperLockOutput(String output) {
        return ProjectTestRunner.isGradleWrapperLockOutput(output);
    }

    private static boolean isGradleWrapperBootstrapIoOutput(String output) {
        return ProjectTestRunner.isGradleWrapperBootstrapIoOutput(output);
    }

    private static String firstGradleLockLine(String output) {
        return ProjectTestRunner.firstGradleLockLine(output);
    }

    private static String firstGradleBootstrapIoLine(String output) {
        return ProjectTestRunner.firstGradleBootstrapIoLine(output);
    }

    private static String firstRelevantProjectTestFailureLine(String output) {
        return ProjectTestRunner.firstRelevantProjectTestFailureLine(output);
    }

    private static String shortTailSummary(String output, int maxLines) {
        return CliText.shortTailSummary(output, maxLines);
    }

    private static String projectTestDetail(String base, String firstLine, String tail) {
        return ProjectTestRunner.projectTestDetail(base, firstLine, tail);
    }

    private static String attemptTrailSuffix(String attemptTrail) {
        if (attemptTrail == null || attemptTrail.isBlank()) {
            return "";
        }
        return "; attempts=" + attemptTrail.trim();
    }

    private static String markerWriteFailureSuffix(IOException error) {
        return "; markerWrite=failed:" + squash(error.getMessage());
    }

    private static Path resolveWrapper(Path projectRoot) throws IOException {
        return ProjectTestRunner.resolveWrapper(projectRoot);
    }

    private static boolean isWindows() {
        return ProjectTestRunner.isWindows();
    }

    private static int testTimeoutSeconds() {
        return ProjectTestRunner.testTimeoutSeconds();
    }

    private static void printTail(PrintStream err, String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        for (int i = start; i < lines.size(); i++) {
            err.println(lines.get(i));
        }
    }

    private static String normalizeLf(String text) {
        return CliText.normalizeLf(text);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean hasRegularFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.anyMatch(Files::isRegularFile);
        }
    }

    private static void deleteRecursivelyBestEffort(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort only: check result must not depend on cleanup success.
                }
            });
        } catch (IOException ignored) {
            // Best effort only.
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path>");
        out.println("       bear fix <ir-file> --project <path>");
        out.println("       bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear check <ir-file> --project <path>");
        out.println("       bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear unblock --project <path>");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref>");
        out.println("       bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]");
        out.println("       bear --help");
    }

    private static void maybeFailInternalForTest(String command) {
        String key = "bear.cli.test.failInternal." + command;
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_" + command);
        }
    }

    static int failWithLegacy(
        PrintStream err,
        int exitCode,
        String legacyLine,
        String code,
        String pathLocator,
        String remediation
    ) {
        err.println(legacyLine);
        return fail(err, exitCode, code, pathLocator, remediation);
    }

    private static int fail(PrintStream err, int exitCode, String code, String pathLocator, String remediation) {
        String locator = normalizeLocator(pathLocator);
        err.println("CODE=" + code);
        err.println("PATH=" + locator);
        err.println("REMEDIATION=" + remediation);
        return exitCode;
    }

    private static String normalizeLocator(String raw) {
        if (raw == null) {
            return "internal";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "internal";
        }
        if (looksAbsolute(trimmed)) {
            return "internal";
        }
        return trimmed.replace('\\', '/');
    }

    private static boolean looksAbsolute(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.startsWith("//")) {
            return true;
        }
        return normalized.matches("^[A-Za-z]:/.*");
    }

    private static final class ExitCode {
        private static final int OK = 0;
        private static final int VALIDATION = 2;
        private static final int DRIFT = 3;
        private static final int TEST_FAILURE = 4;
        private static final int BOUNDARY_EXPANSION = 5;
        private static final int UNDECLARED_REACH = 6;
        private static final int BOUNDARY_BYPASS = 6;
        private static final int USAGE = 64;
        private static final int IO = 74;
        private static final int INTERNAL = 70;
    }

    private static final class FailureCode {
        private static final String USAGE_INVALID_ARGS = "USAGE_INVALID_ARGS";
        private static final String USAGE_UNKNOWN_COMMAND = "USAGE_UNKNOWN_COMMAND";
        private static final String IR_VALIDATION = "IR_VALIDATION";
        private static final String IO_ERROR = "IO_ERROR";
        private static final String IO_GIT = "IO_GIT";
        private static final String DRIFT_MISSING_BASELINE = "DRIFT_MISSING_BASELINE";
        private static final String DRIFT_DETECTED = "DRIFT_DETECTED";
        private static final String TEST_FAILURE = "TEST_FAILURE";
        private static final String TEST_TIMEOUT = "TEST_TIMEOUT";
        private static final String BOUNDARY_EXPANSION = "BOUNDARY_EXPANSION";
        private static final String UNDECLARED_REACH = "UNDECLARED_REACH";
        private static final String BOUNDARY_BYPASS = "BOUNDARY_BYPASS";
        private static final String CONTAINMENT_NOT_VERIFIED = "CONTAINMENT_NOT_VERIFIED";
        private static final String CONTAINMENT_UNSUPPORTED_TARGET = "CONTAINMENT_UNSUPPORTED_TARGET";
        private static final String REPO_MULTI_BLOCK_FAILED = "REPO_MULTI_BLOCK_FAILED";
        private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }

    private static final class PrCheckGitException extends Exception {
        private final String legacyLine;
        private final String pathLocator;

        private PrCheckGitException(String legacyLine, String pathLocator) {
            super(legacyLine);
            this.legacyLine = legacyLine;
            this.pathLocator = pathLocator;
        }

        private String legacyLine() {
            return legacyLine;
        }

        private String pathLocator() {
            return pathLocator;
        }
    }

}


