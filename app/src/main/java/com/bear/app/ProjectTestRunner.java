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
    private ProjectTestRunner() {
    }

    static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
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
        Map<String, String> environment = pb.environment();
        if (!environment.containsKey("GRADLE_USER_HOME")) {
            environment.put("GRADLE_USER_HOME", projectRoot.resolve(".bear-gradle-user-home").toString());
        }
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
        if (isGradleWrapperLockOutput(output)) {
            return new ProjectTestResult(ProjectTestStatus.LOCKED, output);
        }
        if (isGradleWrapperBootstrapIoOutput(output)) {
            return new ProjectTestResult(ProjectTestStatus.BOOTSTRAP_IO, output);
        }
        return new ProjectTestResult(ProjectTestStatus.FAILED, output);
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
