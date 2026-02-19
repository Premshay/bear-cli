package com.bear.app;

final class BlockIndexValidationException extends Exception {
    private final String path;

    BlockIndexValidationException(String message, String path) {
        super(message);
        this.path = path;
    }

    String path() {
        return path;
    }
}
