package com.bear.app;

import java.util.List;
import java.util.Locale;

final class CanonicalDoneGateMatcher {
    private CanonicalDoneGateMatcher() {
    }

    enum GateKind {
        CHECK,
        PR_CHECK
    }

    record MatchResult(boolean matched, String reason) {
        static MatchResult ok() {
            return new MatchResult(true, "");
        }

        static MatchResult fail(String reason) {
            return new MatchResult(false, reason);
        }
    }

    static MatchResult match(String gateResultLine, GateKind kind) {
        if (gateResultLine == null || gateResultLine.isBlank()) {
            return MatchResult.fail("gate line missing");
        }
        ParsedGateLine parsed = ParsedGateLine.parse(gateResultLine);
        if (parsed == null) {
            return MatchResult.fail("gate line parse failed");
        }
        if (parsed.exitCode() != 0) {
            return MatchResult.fail("done gate exit must be 0");
        }

        List<String> tokens = parsed.commandTokens();
        if (tokens.size() < 5) {
            return MatchResult.fail("done gate command too short");
        }
        if (!"bear".equals(tokens.get(0))) {
            return MatchResult.fail("done gate must start with bear");
        }

        if (kind == GateKind.CHECK) {
            return matchCheck(tokens);
        }
        return matchPrCheck(tokens);
    }

    private static MatchResult matchCheck(List<String> tokens) {
        if (!"check".equals(tokens.get(1))) {
            return MatchResult.fail("expected check done gate");
        }
        int i = 2;
        if (!"--all".equals(tokenAt(tokens, i++))) {
            return MatchResult.fail("check done gate must include --all");
        }
        if (!"--project".equals(tokenAt(tokens, i++))) {
            return MatchResult.fail("check done gate must include --project");
        }
        if (tokenAt(tokens, i++) == null) {
            return MatchResult.fail("check done gate missing --project value");
        }

        boolean agent = false;
        while (i < tokens.size()) {
            String token = tokens.get(i++);
            if ("--agent".equals(token)) {
                agent = true;
                continue;
            }
            if ("--collect=all".equals(token)) {
                continue;
            }
            if ("--blocks".equals(token)) {
                String blocksValue = tokenAt(tokens, i++);
                if (blocksValue == null || !isRepoRelativePath(blocksValue)) {
                    return MatchResult.fail("--blocks must be repo-relative when present");
                }
                continue;
            }
            return MatchResult.fail("unexpected token in check done gate: " + token);
        }

        if (!agent) {
            return MatchResult.fail("done gate must include --agent");
        }
        return MatchResult.ok();
    }

    private static MatchResult matchPrCheck(List<String> tokens) {
        if (!"pr-check".equals(tokens.get(1))) {
            return MatchResult.fail("expected pr-check done gate");
        }
        int i = 2;
        if (!"--all".equals(tokenAt(tokens, i++))) {
            return MatchResult.fail("pr-check done gate must include --all");
        }
        if (!"--project".equals(tokenAt(tokens, i++))) {
            return MatchResult.fail("pr-check done gate must include --project");
        }
        if (tokenAt(tokens, i++) == null) {
            return MatchResult.fail("pr-check done gate missing --project value");
        }

        boolean base = false;
        boolean agent = false;
        while (i < tokens.size()) {
            String token = tokens.get(i++);
            if ("--agent".equals(token)) {
                agent = true;
                continue;
            }
            if ("--collect=all".equals(token)) {
                continue;
            }
            if ("--base".equals(token)) {
                String baseValue = tokenAt(tokens, i++);
                if (baseValue == null || baseValue.isBlank()) {
                    return MatchResult.fail("pr-check done gate missing --base value");
                }
                base = true;
                continue;
            }
            if ("--blocks".equals(token)) {
                String blocksValue = tokenAt(tokens, i++);
                if (blocksValue == null || !isRepoRelativePath(blocksValue)) {
                    return MatchResult.fail("--blocks must be repo-relative when present");
                }
                continue;
            }
            return MatchResult.fail("unexpected token in pr-check done gate: " + token);
        }

        if (!base) {
            return MatchResult.fail("pr-check done gate must include --base");
        }
        if (!agent) {
            return MatchResult.fail("done gate must include --agent");
        }
        return MatchResult.ok();
    }

    private static String tokenAt(List<String> tokens, int idx) {
        if (idx < 0 || idx >= tokens.size()) {
            return null;
        }
        return tokens.get(idx);
    }

    private static boolean isRepoRelativePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String normalized = RepoPathNormalizer.normalizePathForIdentity(rawPath).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/") || normalized.startsWith("..") || normalized.startsWith("../")) {
            return false;
        }
        if (normalized.matches("^[a-z]:/.*")) {
            return false;
        }
        return !normalized.contains("/../");
    }

    private record ParsedGateLine(List<String> commandTokens, int exitCode) {
        static ParsedGateLine parse(String gateResultLine) {
            String trimmed = gateResultLine.trim();
            if (trimmed.startsWith("-")) {
                trimmed = trimmed.substring(1).trim();
            }
            int arrow = trimmed.lastIndexOf("=>");
            if (arrow < 0) {
                return null;
            }
            String command = trimmed.substring(0, arrow).trim();
            String exit = trimmed.substring(arrow + 2).trim();
            int exitCode;
            try {
                exitCode = Integer.parseInt(exit);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (command.isBlank()) {
                return null;
            }
            List<String> tokens = List.of(command.split("\\s+"));
            return new ParsedGateLine(tokens, exitCode);
        }
    }
}
