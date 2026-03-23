package com.bear.kernel.target.react;

/**
 * Enum representing the supported React project shapes.
 * Each shape corresponds to a specific build tool and framework combination.
 */
public enum ReactProjectShape {
    /**
     * Vite + React project shape.
     * Detected when vite.config.ts is present and no next.config.* exists.
     */
    VITE_REACT("vite-react"),

    /**
     * Next.js App Router project shape.
     * Detected when next.config.js, next.config.mjs, or next.config.ts is present.
     */
    NEXTJS_APP_ROUTER("nextjs-app-router");

    private final String value;

    ReactProjectShape(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
