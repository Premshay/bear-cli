package com.bear.kernel.template;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure, stateless registry of available CapabilityTemplate implementations.
 * No I/O, no target resolution — all I/O is orchestrated by the app layer.
 */
public final class CapabilityTemplateRegistry {

    // ReadStoreTemplate will be added in Task 2; placeholder for now.
    private static final List<CapabilityTemplate> TEMPLATES = List.of();

    private static final List<CapabilityTemplate> SORTED_TEMPLATES =
            TEMPLATES.stream()
                    .sorted(Comparator.comparing(CapabilityTemplate::id))
                    .toList();

    private CapabilityTemplateRegistry() {}

    /** Returns templates sorted by id(), unmodifiable, stable across calls. */
    public static List<CapabilityTemplate> list() {
        return SORTED_TEMPLATES;
    }

    /** Returns Optional.empty() for unknown ids — never throws. */
    public static Optional<CapabilityTemplate> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return SORTED_TEMPLATES.stream()
                .filter(t -> t.id().equals(id))
                .findFirst();
    }
}
