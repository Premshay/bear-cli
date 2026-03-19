package com.bear.kernel.template;

import com.bear.kernel.target.TargetId;

/**
 * Parameters passed to a CapabilityTemplate during emission.
 *
 * <p>{@code blockName} must match {@code [a-z][a-z0-9-]*} (same pattern as BlockIndexParser).
 * {@code targetId} is used by the template to tailor the impl stub language/style.
 */
public record TemplateParams(String blockName, TargetId targetId) {}
