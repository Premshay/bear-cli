package com.bear.kernel.target;

public enum TargetId {
    JVM("jvm");

    private final String value;

    TargetId(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}