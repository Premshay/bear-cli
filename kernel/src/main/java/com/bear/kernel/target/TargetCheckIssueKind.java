package com.bear.kernel.target;

public enum TargetCheckIssueKind {
    POLICY_INVALID,
    CONTAINMENT_UNSUPPORTED_TARGET,
    DRIFT_MISSING_BASELINE,
    DRIFT_DETECTED,
    CONTAINMENT_NOT_VERIFIED
}