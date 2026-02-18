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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BearCli {
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
            case "check" -> runCheck(args, out, err);
            case "pr-check" -> runPrCheck(args, out, err);
            default -> {
                err.println("usage: UNKNOWN_COMMAND: unknown command: " + command);
                yield ExitCode.USAGE;
            }
        };
    }

    private static int runValidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 2) {
            err.println("usage: INVALID_ARGS: expected: bear validate <file>");
            return ExitCode.USAGE;
        }

        Path file = Path.of(args[1]);
        try {
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
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("io: IO_ERROR: " + file);
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        }
    }

    private static int runCompile(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            err.println("usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path>");
            return ExitCode.USAGE;
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);

        try {
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
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("io: IO_ERROR: " + e.getMessage());
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        }
    }

    private static int runCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            err.println("usage: INVALID_ARGS: expected: bear check <ir-file> --project <path>");
            return ExitCode.USAGE;
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        Path baselineRoot = projectRoot.resolve("build").resolve("generated").resolve("bear");

        Path tempRoot = null;
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);

            if (!Files.isDirectory(baselineRoot) || !hasRegularFiles(baselineRoot)) {
                err.println("drift: MISSING_BASELINE: build/generated/bear (run: bear compile "
                    + irFile + " --project " + projectRoot + ")");
                return ExitCode.DRIFT;
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            target.compile(normalized, tempRoot);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve("bear.surface.json");
            Path candidateManifestPath = candidateRoot.resolve("bear.surface.json");

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> manifestWarnings = new ArrayList<>();
            BoundaryManifest baselineManifest = null;
            if (!Files.isRegularFile(baselineManifestPath)) {
                manifestWarnings.add("check: BASELINE_MANIFEST_MISSING: " + baselineManifestPath);
            } else {
                try {
                    baselineManifest = parseManifest(baselineManifestPath);
                } catch (ManifestParseException e) {
                    manifestWarnings.add("check: BASELINE_MANIFEST_INVALID: " + e.reasonCode());
                }
            }
            if (!Files.isRegularFile(candidateManifestPath)) {
                err.println("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING");
                return ExitCode.INTERNAL;
            }
            BoundaryManifest candidateManifest;
            try {
                candidateManifest = parseManifest(candidateManifestPath);
            } catch (ManifestParseException e) {
                err.println("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode());
                return ExitCode.INTERNAL;
            }

            List<BoundarySignal> boundarySignals = List.of();
            if (baselineManifest != null) {
                if (!baselineManifest.irHash().equals(candidateManifest.irHash())
                    || !baselineManifest.generatorVersion().equals(candidateManifest.generatorVersion())) {
                    manifestWarnings.add("check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale");
                }
                boundarySignals = computeBoundarySignals(baselineManifest, candidateManifest);
            }

            List<DriftItem> drift = computeDrift(baselineRoot, candidateRoot);
            for (String warning : manifestWarnings) {
                err.println(warning);
            }
            for (BoundarySignal signal : boundarySignals) {
                err.println("boundary: EXPANSION: " + signal.type().label + ": " + signal.key());
            }
            if (!drift.isEmpty()) {
                for (DriftItem item : drift) {
                    err.println("drift: " + item.type().label + ": " + item.path());
                }
                return ExitCode.DRIFT;
            }

            ProjectTestResult testResult = runProjectTests(projectRoot);
            if (testResult.status == ProjectTestStatus.FAILED) {
                err.println("check: TEST_FAILED: project tests failed");
                printTail(err, testResult.output);
                return ExitCode.TEST_FAILURE;
            }
            if (testResult.status == ProjectTestStatus.TIMEOUT) {
                err.println("check: TEST_TIMEOUT: project tests exceeded " + testTimeoutSeconds() + "s");
                printTail(err, testResult.output);
                return ExitCode.TEST_FAILURE;
            }

            out.println("check: OK");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("io: IO_ERROR: " + e.getMessage());
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static int runPrCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }
        if (args.length != 6 || !"--project".equals(args[2]) || !"--base".equals(args[4])) {
            err.println("usage: INVALID_ARGS: expected: bear pr-check <ir-file> --project <path> --base <ref>");
            return ExitCode.USAGE;
        }

        String irArg = args[1];
        Path irPath = Path.of(irArg);
        if (irPath.isAbsolute()) {
            err.println("usage: INVALID_ARGS: ir-file must be repo-relative");
            return ExitCode.USAGE;
        }
        Path normalizedRelative = irPath.normalize();
        if (normalizedRelative.startsWith("..") || normalizedRelative.toString().isBlank()) {
            err.println("usage: INVALID_ARGS: ir-file must be repo-relative");
            return ExitCode.USAGE;
        }

        Path projectRoot = Path.of(args[3]).toAbsolutePath().normalize();
        String baseRef = args[5];
        Path headIrPath = projectRoot.resolve(normalizedRelative).normalize();
        if (!headIrPath.startsWith(projectRoot)) {
            err.println("usage: INVALID_ARGS: ir-file must be repo-relative");
            return ExitCode.USAGE;
        }
        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        if (!Files.isRegularFile(headIrPath)) {
            err.println("pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath);
            return ExitCode.IO;
        }

        Path tempRoot = null;
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();

            BearIr head = normalizer.normalize(parseAndValidateIr(parser, validator, headIrPath));

            GitResult isRepoResult = runGit(projectRoot, List.of("rev-parse", "--is-inside-work-tree"));
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                err.println("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot);
                return ExitCode.IO;
            }

            GitResult mergeBaseResult = runGit(projectRoot, List.of("merge-base", "HEAD", baseRef));
            if (mergeBaseResult.exitCode() != 0) {
                err.println("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef);
                return ExitCode.IO;
            }
            String mergeBase = mergeBaseResult.stdout().trim();
            if (mergeBase.isBlank()) {
                err.println("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base");
                return ExitCode.IO;
            }

            BearIr base = null;
            GitResult catFileResult = runGit(projectRoot, List.of("cat-file", "-e", mergeBase + ":" + repoRelativePath));
            if (catFileResult.exitCode() != 0) {
                GitResult existsResult = runGit(projectRoot, List.of("ls-tree", "--name-only", mergeBase, "--", repoRelativePath));
                if (existsResult.exitCode() != 0) {
                    err.println("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath);
                    return ExitCode.IO;
                }
                if (existsResult.stdout().trim().isEmpty()) {
                    err.println("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: " + repoRelativePath + ": treated_as_empty_base");
                } else {
                    err.println("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath);
                    return ExitCode.IO;
                }
            } else {
                GitResult showResult = runGit(projectRoot, List.of("show", mergeBase + ":" + repoRelativePath));
                if (showResult.exitCode() != 0) {
                    err.println("pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath);
                    return ExitCode.IO;
                }
                tempRoot = Files.createTempDirectory("bear-pr-check-");
                Path baseTempIr = tempRoot.resolve("base.bear.yaml");
                Files.writeString(baseTempIr, showResult.stdout(), StandardCharsets.UTF_8);
                base = normalizer.normalize(parseAndValidateIr(parser, validator, baseTempIr));
            }

            List<PrDelta> deltas = computePrDeltas(base, head);
            for (PrDelta delta : deltas) {
                err.println("pr-delta: " + delta.clazz().label + ": " + delta.category().label + ": " + delta.change().label + ": " + delta.key());
            }

            boolean hasBoundary = deltas.stream().anyMatch(delta -> delta.clazz() == PrClass.BOUNDARY_EXPANDING);
            if (hasBoundary) {
                err.println("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED");
                return ExitCode.BOUNDARY_EXPANSION;
            }

            out.println("pr-check: OK: NO_BOUNDARY_EXPANSION");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()));
            return ExitCode.IO;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("pr-check: IO_ERROR: INTERRUPTED");
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static List<DriftItem> computeDrift(Path baselineRoot, Path candidateRoot) throws IOException {
        Map<String, byte[]> baseline = readRegularFiles(baselineRoot);
        Map<String, byte[]> candidate = readRegularFiles(candidateRoot);

        TreeSet<String> allPaths = new TreeSet<>();
        allPaths.addAll(baseline.keySet());
        allPaths.addAll(candidate.keySet());

        List<DriftItem> drift = new ArrayList<>();
        for (String path : allPaths) {
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

    private static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        List<BoundarySignal> signals = new ArrayList<>();
        for (String capability : candidate.capabilities().keySet()) {
            if (!baseline.capabilities().containsKey(capability)) {
                signals.add(new BoundarySignal(BoundaryType.CAPABILITY_ADDED, capability));
            }
        }
        for (Map.Entry<String, TreeSet<String>> entry : candidate.capabilities().entrySet()) {
            String capability = entry.getKey();
            if (!baseline.capabilities().containsKey(capability)) {
                continue;
            }
            TreeSet<String> baselineOps = baseline.capabilities().get(capability);
            for (String op : entry.getValue()) {
                if (!baselineOps.contains(op)) {
                    signals.add(new BoundarySignal(BoundaryType.CAPABILITY_OP_ADDED, capability + "." + op));
                }
            }
        }
        for (String invariant : baseline.invariants()) {
            if (!candidate.invariants().contains(invariant)) {
                signals.add(new BoundarySignal(BoundaryType.INVARIANT_RELAXED, invariant));
            }
        }
        signals.sort(Comparator
            .comparing((BoundarySignal signal) -> signal.type().order)
            .thenComparing(BoundarySignal::key));
        return signals;
    }

    private static BearIr parseAndValidateIr(BearIrParser parser, BearIrValidator validator, Path path) throws IOException {
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
    }

    private static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        PrSurface base = baseIr == null ? emptyPrSurface() : toPrSurface(baseIr);
        PrSurface head = toPrSurface(headIr);

        List<PrDelta> deltas = new ArrayList<>();

        for (String port : head.ports()) {
            if (!base.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.ADDED, port));
            }
        }
        for (String port : base.ports()) {
            if (!head.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.PORTS, PrChange.REMOVED, port));
            }
        }

        TreeSet<String> commonPorts = new TreeSet<>(head.ports());
        commonPorts.retainAll(base.ports());
        for (String port : commonPorts) {
            TreeSet<String> headOps = head.opsByPort().getOrDefault(port, new TreeSet<>());
            TreeSet<String> baseOps = base.opsByPort().getOrDefault(port, new TreeSet<>());
            for (String op : headOps) {
                if (!baseOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.ADDED, port + "." + op));
                }
            }
            for (String op : baseOps) {
                if (!headOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.REMOVED, port + "." + op));
                }
            }
        }

        addIdempotencyDeltas(deltas, base.idempotency(), head.idempotency());
        addContractDeltas(deltas, base.inputs(), head.inputs(), true);
        addContractDeltas(deltas, base.outputs(), head.outputs(), false);

        for (String invariant : head.invariants()) {
            if (!base.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.INVARIANTS, PrChange.ADDED, invariant));
            }
        }
        for (String invariant : base.invariants()) {
            if (!head.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.INVARIANTS, PrChange.REMOVED, invariant));
            }
        }

        deltas.sort(Comparator
            .comparing((PrDelta delta) -> delta.clazz().order)
            .thenComparing(delta -> delta.category().order)
            .thenComparing(delta -> delta.change().order)
            .thenComparing(PrDelta::key));
        return deltas;
    }

    private static void addIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.Idempotency base,
        BearIr.Idempotency head
    ) {
        if (base == null && head == null) {
            return;
        }
        if (base == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.ADDED, "idempotency"));
            return;
        }
        if (head == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "idempotency"));
            return;
        }

        if (!base.key().equals(head.key())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.key"));
        }
        if (!base.store().port().equals(head.store().port())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.port"));
        }
        if (!base.store().getOp().equals(head.store().getOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.getOp"));
        }
        if (!base.store().putOp().equals(head.store().putOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.putOp"));
        }
    }

    private static void addContractDeltas(
        List<PrDelta> deltas,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String name : names) {
            boolean inBase = base.containsKey(name);
            boolean inHead = head.containsKey(name);
            String prefix = input ? "input." : "output.";

            if (!inBase) {
                PrClass clazz = input ? PrClass.ORDINARY : PrClass.BOUNDARY_EXPANDING;
                deltas.add(new PrDelta(
                    clazz,
                    PrCategory.CONTRACT,
                    PrChange.ADDED,
                    prefix + name + ":" + typeToken(head.get(name))
                ));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.REMOVED,
                    prefix + name + ":" + typeToken(base.get(name))
                ));
                continue;
            }
            if (base.get(name) != head.get(name)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.CHANGED,
                    prefix + name + ":" + typeToken(base.get(name)) + "->" + typeToken(head.get(name))
                ));
            }
        }
    }

    private static String typeToken(BearIr.FieldType type) {
        return type.name().toLowerCase();
    }

    private static PrSurface toPrSurface(BearIr ir) {
        TreeSet<String> ports = new TreeSet<>();
        Map<String, TreeSet<String>> opsByPort = new TreeMap<>();
        for (BearIr.EffectPort port : ir.block().effects().allow()) {
            ports.add(port.port());
            opsByPort.put(port.port(), new TreeSet<>(port.ops()));
        }

        Map<String, BearIr.FieldType> inputs = new TreeMap<>();
        for (BearIr.Field input : ir.block().contract().inputs()) {
            inputs.put(input.name(), input.type());
        }
        Map<String, BearIr.FieldType> outputs = new TreeMap<>();
        for (BearIr.Field output : ir.block().contract().outputs()) {
            outputs.put(output.name(), output.type());
        }

        TreeSet<String> invariants = new TreeSet<>();
        if (ir.block().invariants() != null) {
            for (BearIr.Invariant invariant : ir.block().invariants()) {
                invariants.add(invariant.kind().name().toLowerCase() + ":" + invariant.field());
            }
        }
        return new PrSurface(ports, opsByPort, inputs, outputs, ir.block().idempotency(), invariants);
    }

    private static PrSurface emptyPrSurface() {
        return new PrSurface(
            new TreeSet<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            null,
            new TreeSet<>()
        );
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

    private static String squash(String text) {
        if (text == null) {
            return "no details";
        }
        String squashed = normalizeLf(text).replace('\n', ' ').trim();
        return squashed.isEmpty() ? "no details" : squashed;
    }

    private static BoundaryManifest parseManifest(Path path) throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String schemaVersion = extractRequiredString(json, "schemaVersion");
        String target = extractRequiredString(json, "target");
        String block = extractRequiredString(json, "block");
        String irHash = extractRequiredString(json, "irHash");
        String generatorVersion = extractRequiredString(json, "generatorVersion");

        String capabilitiesPayload = extractRequiredArrayPayload(json, "capabilities");
        String invariantsPayload = extractRequiredArrayPayload(json, "invariants");
        Map<String, TreeSet<String>> capabilities = parseCapabilities(capabilitiesPayload);
        TreeSet<String> invariants = parseInvariants(invariantsPayload);
        return new BoundaryManifest(schemaVersion, target, block, irHash, generatorVersion, capabilities, invariants);
    }

    private static String extractRequiredString(String json, String key) throws ManifestParseException {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        return jsonUnescape(m.group(1));
    }

    private static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        int start = json.indexOf('[', keyIdx);
        if (start < 0) {
            throw new ManifestParseException("MALFORMED_ARRAY_" + key);
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        throw new ManifestParseException("MALFORMED_ARRAY_" + key);
    }

    private static Map<String, TreeSet<String>> parseCapabilities(String payload) throws ManifestParseException {
        Map<String, TreeSet<String>> capabilities = new TreeMap<>();
        if (payload.isBlank()) {
            return capabilities;
        }

        Matcher m = Pattern.compile("\\{\"name\":\"((?:\\\\.|[^\\\\\"])*)\",\"ops\":\\[([^\\]]*)\\]\\}").matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String name = jsonUnescape(m.group(1));
            TreeSet<String> ops = new TreeSet<>();
            String opsPayload = m.group(2);
            if (!opsPayload.isBlank()) {
                Matcher opMatcher = Pattern.compile("\"((?:\\\\.|[^\\\\\"])*)\"").matcher(opsPayload);
                while (opMatcher.find()) {
                    ops.add(jsonUnescape(opMatcher.group(1)));
                }
            }
            capabilities.put(name, ops);
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_CAPABILITIES");
        }
        return capabilities;
    }

    private static TreeSet<String> parseInvariants(String payload) throws ManifestParseException {
        TreeSet<String> invariants = new TreeSet<>();
        if (payload.isBlank()) {
            return invariants;
        }

        Matcher m = Pattern.compile("\\{\"kind\":\"((?:\\\\.|[^\\\\\"])*)\",\"field\":\"((?:\\\\.|[^\\\\\"])*)\"\\}")
            .matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String kind = jsonUnescape(m.group(1));
            String field = jsonUnescape(m.group(2));
            if ("non_negative".equals(kind)) {
                invariants.add("non_negative:" + field);
            }
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_INVARIANTS");
        }
        return invariants;
    }

    private static String jsonUnescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    out.append('\n');
                } else if (next == 'r') {
                    out.append('\r');
                } else if (next == 't') {
                    out.append('\t');
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
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

    private static Map<String, byte[]> readRegularFiles(Path root) throws IOException {
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

    private static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        Path wrapper = resolveWrapper(projectRoot);
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd");
            command.add("/c");
            command.add(wrapper.toString());
        } else {
            command.add(wrapper.toString());
        }
        command.add("--no-daemon");
        command.add("test");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (InputStream in = process.getInputStream()) {
            boolean finished = process.waitFor(testTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new ProjectTestResult(ProjectTestStatus.TIMEOUT, output);
            }
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (process.exitValue() == 0) {
            return new ProjectTestResult(ProjectTestStatus.PASSED, output);
        }
        return new ProjectTestResult(ProjectTestStatus.FAILED, output);
    }

    private static Path resolveWrapper(Path projectRoot) throws IOException {
        if (isWindows()) {
            Path wrapper = projectRoot.resolve("gradlew.bat");
            if (!Files.isRegularFile(wrapper)) {
                throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
            }
            return wrapper;
        }

        Path wrapper = projectRoot.resolve("gradlew");
        if (!Files.isRegularFile(wrapper)) {
            throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
        }
        if (!Files.isExecutable(wrapper)) {
            throw new IOException("PROJECT_TEST_WRAPPER_NOT_EXECUTABLE: expected executable " + wrapper + " (run: chmod +x gradlew)");
        }
        return wrapper;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static int testTimeoutSeconds() {
        String raw = System.getProperty("bear.check.testTimeoutSeconds");
        if (raw == null || raw.isBlank()) {
            return 300;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : 300;
        } catch (NumberFormatException ignored) {
            return 300;
        }
    }

    private static void printTail(PrintStream err, String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        for (int i = start; i < lines.size(); i++) {
            err.println(lines.get(i));
        }
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
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
        out.println("       bear check <ir-file> --project <path>");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref>");
        out.println("       bear --help");
    }

    private static final class ExitCode {
        private static final int OK = 0;
        private static final int VALIDATION = 2;
        private static final int DRIFT = 3;
        private static final int TEST_FAILURE = 4;
        private static final int BOUNDARY_EXPANSION = 5;
        private static final int USAGE = 64;
        private static final int IO = 74;
        private static final int INTERNAL = 70;
    }

    private enum DriftType {
        ADDED("ADDED", 0),
        REMOVED("REMOVED", 1),
        CHANGED("CHANGED", 2);

        private final String label;
        private final int order;

        DriftType(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record DriftItem(String path, DriftType type) {
    }

    private enum BoundaryType {
        CAPABILITY_ADDED("CAPABILITY_ADDED", 0),
        CAPABILITY_OP_ADDED("CAPABILITY_OP_ADDED", 1),
        INVARIANT_RELAXED("INVARIANT_RELAXED", 2);

        private final String label;
        private final int order;

        BoundaryType(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record BoundarySignal(BoundaryType type, String key) {
    }

    private enum PrClass {
        BOUNDARY_EXPANDING("BOUNDARY_EXPANDING", 0),
        ORDINARY("ORDINARY", 1);

        private final String label;
        private final int order;

        PrClass(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private enum PrCategory {
        PORTS("PORTS", 0),
        OPS("OPS", 1),
        IDEMPOTENCY("IDEMPOTENCY", 2),
        CONTRACT("CONTRACT", 3),
        INVARIANTS("INVARIANTS", 4);

        private final String label;
        private final int order;

        PrCategory(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private enum PrChange {
        CHANGED("CHANGED", 0),
        ADDED("ADDED", 1),
        REMOVED("REMOVED", 2);

        private final String label;
        private final int order;

        PrChange(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record PrDelta(PrClass clazz, PrCategory category, PrChange change, String key) {
    }

    private record PrSurface(
        TreeSet<String> ports,
        Map<String, TreeSet<String>> opsByPort,
        Map<String, BearIr.FieldType> inputs,
        Map<String, BearIr.FieldType> outputs,
        BearIr.Idempotency idempotency,
        TreeSet<String> invariants
    ) {
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }

    private record BoundaryManifest(
        String schemaVersion,
        String target,
        String block,
        String irHash,
        String generatorVersion,
        Map<String, TreeSet<String>> capabilities,
        TreeSet<String> invariants
    ) {
    }

    private static final class ManifestParseException extends Exception {
        private final String reasonCode;

        private ManifestParseException(String reasonCode) {
            super(reasonCode);
            this.reasonCode = reasonCode;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }

    private enum ProjectTestStatus {
        PASSED,
        FAILED,
        TIMEOUT
    }

    private record ProjectTestResult(ProjectTestStatus status, String output) {
    }
}
