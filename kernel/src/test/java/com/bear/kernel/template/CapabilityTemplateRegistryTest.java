package com.bear.kernel.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CapabilityTemplateRegistry.
 *
 * Feature: p3-capability-templates
 * Property 1: Registry enumeration is stable and sorted — Validates: Requirements 1.1, 1.4
 * Property 2: Registry lookup round-trip — Validates: Requirements 1.2
 * Property 3: Registry not-found is explicit — Validates: Requirements 1.3
 */
class CapabilityTemplateRegistryTest {

    /**
     * Property 1: Registry enumeration is stable and sorted.
     * Validates: Requirements 1.1, 1.4
     */
    @Test
    void listIsSortedAndStable() {
        List<CapabilityTemplate> first = CapabilityTemplateRegistry.list();
        List<CapabilityTemplate> second = CapabilityTemplateRegistry.list();

        // Stable across calls
        assertEquals(first, second, "list() must return identical results across calls");

        // Sorted by id
        for (int i = 0; i < first.size() - 1; i++) {
            String a = first.get(i).id();
            String b = first.get(i + 1).id();
            assertTrue(a.compareTo(b) <= 0,
                    "list() must be sorted by id: '" + a + "' should come before '" + b + "'");
        }
    }

    /**
     * Property 2: Registry lookup round-trip.
     * For each id in list(), find(id) must return a non-empty Optional whose id() equals the queried id.
     * Validates: Requirements 1.2
     *
     * Note: exercises all registered templates via round-trip lookup.
     */
    @Test
    void findKnownTemplateReturnsPresent() {
        List<CapabilityTemplate> templates = CapabilityTemplateRegistry.list();
        for (CapabilityTemplate template : templates) {
            String id = template.id();
            Optional<CapabilityTemplate> found = CapabilityTemplateRegistry.find(id);
            assertTrue(found.isPresent(), "find('" + id + "') must return non-empty Optional");
            assertEquals(id, found.get().id(), "found template id must equal queried id");
        }
    }

    /**
     * Property 3: Registry not-found is explicit.
     * Validates: Requirements 1.3
     */
    @Test
    void findUnknownTemplateReturnsEmpty() {
        Optional<CapabilityTemplate> result = CapabilityTemplateRegistry.find("unknown-xyz");
        assertTrue(result.isEmpty(), "find('unknown-xyz') must return Optional.empty()");
    }

    /**
     * Property 3: find() never throws for arbitrary input.
     * Validates: Requirements 1.3
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null-like", "read-store", "UNKNOWN", "!@#$"})
    void findNeverThrowsForArbitraryInput(String id) {
        assertDoesNotThrow(
                () -> CapabilityTemplateRegistry.find(id),
                "find() must not throw for input: '" + id + "'"
        );
    }
}
