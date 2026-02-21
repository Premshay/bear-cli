package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class PrCheckCommandService {
    private PrCheckCommandService() {
    }

    static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
        Path tempRoot = null;
        try {
            maybeFailInternalForTest();
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();

            Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
            if (!headIrPath.startsWith(projectRoot) || !Files.isRegularFile(headIrPath)) {
                return prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath),
                    "IO_ERROR",
                    CliCodes.IO_ERROR,
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
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
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
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
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
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base"),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
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
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
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
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
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
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
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

            List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(base, head);
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
                    CliCodes.EXIT_BOUNDARY_EXPANSION,
                    stderrLines,
                    "BOUNDARY_EXPANSION",
                    CliCodes.BOUNDARY_EXPANSION,
                    repoRelativePath,
                    "Review boundary-expanding deltas and route through explicit boundary review.",
                    "pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED",
                    deltaLines,
                    true,
                    !deltaLines.isEmpty()
                );
            }

            return new PrCheckResult(
                CliCodes.EXIT_OK,
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
                CliCodes.EXIT_IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                CliCodes.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            );
        } catch (BearIrValidationException e) {
            return prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear pr-check`.",
                e.formatLine(),
                List.of(),
                false,
                false
            );
        } catch (IOException e) {
            return prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERNAL_IO: " + CliText.squash(e.getMessage())),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`.",
                "pr-check: IO_ERROR: INTERNAL_IO: " + CliText.squash(e.getMessage()),
                List.of(),
                false,
                false
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                CliCodes.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            );
        } catch (Exception e) {
            return prFailure(
                CliCodes.EXIT_INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                CliCodes.INTERNAL_ERROR,
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

    private static BearIr parseAndValidateIr(BearIrParser parser, BearIrValidator validator, Path path) throws IOException {
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
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
        return new GitResult(process.exitValue(), CliText.normalizeLf(stdout), CliText.normalizeLf(stderr));
    }

    private static GitResult runGitForPrCheck(Path projectRoot, List<String> gitArgs, String pathLocator)
        throws PrCheckGitException, InterruptedException {
        try {
            return runGit(projectRoot, gitArgs);
        } catch (IOException e) {
            throw new PrCheckGitException("pr-check: IO_ERROR: INTERNAL_IO: " + CliText.squash(e.getMessage()), pathLocator);
        }
    }

    private static void maybeFailInternalForTest() {
        String key = "bear.cli.test.failInternal.pr-check";
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_pr-check");
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
