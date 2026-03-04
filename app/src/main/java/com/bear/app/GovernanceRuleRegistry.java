package com.bear.app;

import java.util.Set;

final class GovernanceRuleRegistry {
    static final Set<String> PUBLIC_RULE_IDS = Set.of(
        "DIRECT_IMPL_USAGE",
        "NULL_PORT_WIRING",
        "EFFECTS_BYPASS",
        "IMPL_PLACEHOLDER",
        "IMPL_CONTAINMENT_BYPASS",
        "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
        "MULTI_BLOCK_PORT_IMPL_FORBIDDEN",
        "BLOCK_PORT_IMPL_INVALID",
        "BLOCK_PORT_REFERENCE_FORBIDDEN",
        "BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN",
        "SHARED_PURITY_VIOLATION",
        "IMPL_PURITY_VIOLATION",
        "IMPL_STATE_DEPENDENCY_BYPASS",
        "SCOPED_IMPORT_POLICY_BYPASS",
        "SHARED_LAYOUT_POLICY_VIOLATION",
        "STATE_STORE_OP_MISUSE",
        "STATE_STORE_NOOP_UPDATE"
    );

    private GovernanceRuleRegistry() {
    }
}
