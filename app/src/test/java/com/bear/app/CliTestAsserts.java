package com.bear.app;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CliTestAsserts {
    private CliTestAsserts() {
    }

    static void assertContainsInOrder(String text, List<String> fragments) {
        int cursor = 0;
        for (String fragment : fragments) {
            int idx = text.indexOf(fragment, cursor);
            assertTrue(idx >= 0, "missing fragment: " + fragment + "\ntext:\n" + text);
            cursor = idx + fragment.length();
        }
    }
}
