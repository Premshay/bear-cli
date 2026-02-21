package com.bear.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ProjectTestRunner {
    private enum GradleHomeMode {
        EXTERNAL_ENV("external-env", "external-env-retry"),
        ISOLATED("isolated", "isolated-retry"),
        USER_CACHE("user-cache", "user-cache-retry");

        private final String initialLabel;
        private final String retryLabel;

        GradleHomeMode(String initialLabel, String retryLabel) {
            this.initialLabel = initialLabel;
            this.retryLabel = retryLabel;
        }
    }

    private record ProjectTestAttempt(
        String label,
        String gradleUserHome,
        ProjectTestStatus status,
        String output
    ) {
    }

    private ProjectTestRunner() {
    }

    static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        Path wrapper = resolveWrapper(projectRoot);
        List<ProjectTestAttempt> attempts = new ArrayList<>();

        String externalGradleUserHome = configuredExternalGradleUserHome();
        if (externalGradleUserHome != null) {
            ProjectTestAttempt first = runProjectTestsOnce(
                projectRoot,
                wrapper,
                externalGradleUserHome,
                GradleHomeMode.EXTERNAL_ENV.initialLabel
            );
            attempts.add(first);
            ProjectTestAttempt latest = first;
            if (isLockOrBootstrap(latest.status)) {
                safeSelfHealGradleHome(latest.gradleUserHome);
                latest = runProjectTestsOnce(
                    projectRoot,
                    wrapper,
                    externalGradleUserHome,
                    GradleHomeMode.EXTERNAL_ENV.retryLabel
                );
                attempts.add(latest);
            }
            return finalizeAttempts(attempts, latest);
        }

        String isolatedGradleUserHome = projectRoot.resolve(".bear-gradle-user-home").toString();
        ProjectTestAttempt isolated = runProjectTestsOnce(
            projectRoot,
            wrapper,
            isolatedGradleUserHome,
            GradleHomeMode.ISOLATED.initialLabel
        );
        attempts.add(isolated);

        ProjectTestAttempt latest = isolated;
        if (isLockOrBootstrap(latest.status)) {
            safeSelfHealGradleHome(latest.gradleUserHome);
            latest = runProjectTestsOnce(
                projectRoot,
                wrapper,
                isolatedGradleUserHome,
                GradleHomeMode.ISOLATED.retryLabel
            );
            attempts.add(latest);
        }
        if (isLockOrBootstrap(latest.status)) {
            String userGradleUserHome = defaultUserGradleHome();
            latest = runProjectTestsOnce(
                projectRoot,
                wrapper,
                userGradleUserHome,
                GradleHomeMode.USER_CACHE.initialLabel
            );
            attempts.add(latest);
        }
        return finalizeAttempts(attempts, latest);
    }

    private static ProjectTestAttempt runProjectTestsOnce(
        Path projectRoot,
        Path wrapper,
        String gradleUserHome,
        String attemptLabel
    ) throws IOException, InterruptedException {
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
        Map<String, String> environment = pb.environment();
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            environment.put("GRADLE_USER_HOME", gradleUserHome);
        }
        Process process = pb.start();

        String output;
        try (InputStream in = process.getInputStream()) {
            boolean finished = process.waitFor(testTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new ProjectTestAttempt(attemptLabel, gradleUserHome, ProjectTestStatus.TIMEOUT, output);
            }
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ProjectTestStatus status = classifyProjectTestStatus(process.exitValue(), output);
        return new ProjectTestAttempt(attemptLabel, gradleUserHome, status, output);
    }

    private static ProjectTestStatus classifyProjectTestStatus(int exitValue, String output) {
        if (exitValue == 0) {
            return ProjectTestStatus.PASSED;
        }
        if (isGradleWrapperLockOutput(output)) {
            return ProjectTestStatus.LOCKED;
        }
        if (isGradleWrapperBootstrapIoOutput(output)) {
            return ProjectTestStatus.BOOTSTRAP_IO;
        }
        return ProjectTestStatus.FAILED;
    }

    private static ProjectTestResult finalizeAttempts(List<ProjectTestAttempt> attempts, ProjectTestAttempt latest) {
        String attemptTrail = attempts.stream().map(ProjectTestAttempt::label).reduce((a, b) -> a + "," + b).orElse("");
        return new ProjectTestResult(
            latest.status,
            latest.output,
            attemptTrail,
            firstLockLineAcrossAttempts(attempts),
            firstBootstrapLineAcrossAttempts(attempts)
        );
    }

    private static String firstLockLineAcrossAttempts(List<ProjectTestAttempt> attempts) {
        for (ProjectTestAttempt attempt : attempts) {
            String line = firstGradleLockLine(attempt.output);
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static String firstBootstrapLineAcrossAttempts(List<ProjectTestAttempt> attempts) {
        for (ProjectTestAttempt attempt : attempts) {
            String line = firstGradleBootstrapIoLine(attempt.output);
            if (line != null) {
                return line;
            }
        }
        return null;
    }

    private static boolean isLockOrBootstrap(ProjectTestStatus status) {
        return status == ProjectTestStatus.LOCKED || status == ProjectTestStatus.BOOTSTRAP_IO;
    }

    private static String configuredExternalGradleUserHome() {
        String override = System.getProperty("bear.cli.test.gradleUserHomeOverride");
        if (override != null) {
            String trimmed = override.trim();
            if ("NONE".equalsIgnoreCase(trimmed)) {
                return null;
            }
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        String env = System.getenv("GRADLE_USER_HOME");
        if (env == null || env.isBlank()) {
            return null;
        }
        return env.trim();
    }

    private static String defaultUserGradleHome() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return Path.of(".gradle").toString();
        }
        return Path.of(userHome, ".gradle").toString();
    }

    private static void safeSelfHealGradleHome(String gradleUserHome) {
        if (gradleUserHome == null || gradleUserHome.isBlank()) {
            return;
        }
        try {
            Path distsRoot = Path.of(gradleUserHome).resolve("wrapper").resolve("dists").normalize();
            if (!Files.isDirectory(distsRoot)) {
                return;
            }
            List<Path> deleteTargets = new ArrayList<>();
            try (var stream = Files.walk(distsRoot)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".zip.lck") || fileName.endsWith(".zip.part")) {
                        deleteTargets.add(path);
                        return;
                    }
                    if (fileName.endsWith(".zip.ok")) {
                        Path zipPath = path.resolveSibling(path.getFileName().toString().substring(0, path.getFileName().toString().length() - 3));
                        if (!Files.exists(zipPath)) {
                            deleteTargets.add(path);
                        }
                    }
                });
            }
            deleteTargets.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            for (Path target : deleteTargets) {
                Path normalized = target.normalize();
                if (!normalized.startsWith(distsRoot)) {
                    continue;
                }
                Files.deleteIfExists(normalized);
            }
        } catch (Exception ignored) {
            // Safe self-heal is best effort and must not hide primary test result classification.
        }
    }

    static boolean isGradleWrapperLockOutput(String output) {
        String lower = CliText.normalizeLf(output).toLowerCase();
        if (lower.contains(".zip.lck")) {
            return true;
        }
        if (lower.contains("gradlewrappermain") && lower.contains("access is denied")) {
            return true;
        }
        return lower.contains("project_test_gradle_lock_simulated");
    }

    static boolean isGradleWrapperBootstrapIoOutput(String output) {
        String lower = CliText.normalizeLf(output).toLowerCase();
        if (lower.contains("project_test_gradle_bootstrap_simulated")) {
            return true;
        }

        boolean mentionsGradleZip = lower.contains("gradle-") && lower.contains("-bin.zip");
        if (mentionsGradleZip
            && (lower.contains("nosuchfileexception")
            || lower.contains("filenotfoundexception")
            || lower.contains("zipexception")
            || lower.contains("error in opening zip file")
            || lower.contains("end header not found")
            || lower.contains("cannot unzip")
            || lower.contains("unable to unzip")
            || lower.contains("unable to install gradle"))) {
            return true;
        }

        return lower.contains("error in opening zip file")
            || lower.contains("end header not found")
            || lower.contains("project_test_gradle_bootstrap");
    }

    static String firstGradleLockLine(String output) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
            String lower = line.toLowerCase();
            if (lower.contains(".zip.lck") || lower.contains("access is denied")) {
                return line.trim();
            }
        }
        return null;
    }

    static String firstGradleBootstrapIoLine(String output) {
        for (String line : CliText.normalizeLf(output).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            boolean mentionsGradleZip = lower.contains("gradle-") && lower.contains("-bin.zip");
            if ((mentionsGradleZip
                && (lower.contains("nosuchfileexception")
                || lower.contains("filenotfoundexception")
                || lower.contains("zipexception")
                || lower.contains("error in opening zip file")
                || lower.contains("end header not found")
                || lower.contains("cannot unzip")
                || lower.contains("unable to unzip")
                || lower.contains("unable to install gradle")))
                || lower.contains("project_test_gradle_bootstrap_simulated")) {
                return trimmed;
            }
        }
        return null;
    }

    static String firstRelevantProjectTestFailureLine(String output) {
        List<String> lines = CliText.normalizeLf(output).lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("exception")
                || lower.contains("error")
                || lower.contains("failed")
                || lower.contains("failure")
                || lower.contains("could not")) {
                return line;
            }
        }
        if (!lines.isEmpty()) {
            return lines.get(0);
        }
        return null;
    }

    static String projectTestDetail(String base, String firstLine, String tail) {
        StringBuilder detail = new StringBuilder(base);
        if (firstLine != null && !firstLine.isBlank()) {
            detail.append("; line: ").append(firstLine.trim());
        }
        if (tail != null && !tail.isBlank()) {
            String normalizedTail = tail.trim();
            if (firstLine == null || !normalizedTail.equals(firstLine.trim())) {
                detail.append("; tail: ").append(normalizedTail);
            }
        }
        return detail.toString();
    }

    static Path resolveWrapper(Path projectRoot) throws IOException {
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

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static int testTimeoutSeconds() {
        String raw = System.getProperty("bear.check.testTimeoutSeconds");
        if (raw == null || raw.isBlank()) {
            return 300;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed <= 0 ? 300 : parsed;
        } catch (NumberFormatException e) {
            return 300;
        }
    }
}
