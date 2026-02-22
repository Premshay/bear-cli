package com.bear.app;

final class PolicyValidationException extends Exception {
    private final String policyPath;

    PolicyValidationException(String policyPath, String message) {
        super(message);
        this.policyPath = policyPath;
    }

    String policyPath() {
        return policyPath;
    }
}
