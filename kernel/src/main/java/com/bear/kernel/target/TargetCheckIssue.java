package com.bear.kernel.target;

public record TargetCheckIssue(
    TargetCheckIssueKind kind,
    String path,
    String remediation,
    String legacyLine
) {
}