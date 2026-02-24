package com.bear.kernel.policy;

public final class SharedAllowedDepsPolicyException extends Exception {
    private final String reasonCode;

    public SharedAllowedDepsPolicyException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
