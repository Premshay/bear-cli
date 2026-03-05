package com.bear.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalDoneGateMatcherTest {
    @Test
    void checkDoneGateMatchesWithAgentAndZeroExit() {
        CanonicalDoneGateMatcher.MatchResult result = CanonicalDoneGateMatcher.match(
            "- bear check --all --project . --collect=all --agent => 0",
            CanonicalDoneGateMatcher.GateKind.CHECK
        );
        assertTrue(result.matched(), result.reason());
    }

    @Test
    void checkDoneGateFailsWithoutAgent() {
        CanonicalDoneGateMatcher.MatchResult result = CanonicalDoneGateMatcher.match(
            "- bear check --all --project . => 0",
            CanonicalDoneGateMatcher.GateKind.CHECK
        );
        assertFalse(result.matched());
    }

    @Test
    void doneGateFailsWhenBlocksPathIsAbsolute() {
        CanonicalDoneGateMatcher.MatchResult result = CanonicalDoneGateMatcher.match(
            "- bear pr-check --all --project . --base origin/main --blocks C:/repo/bear.blocks.yaml --agent => 0",
            CanonicalDoneGateMatcher.GateKind.PR_CHECK
        );
        assertFalse(result.matched());
    }

    @Test
    void prCheckDoneGateMatchesWithBaseAndAgent() {
        CanonicalDoneGateMatcher.MatchResult result = CanonicalDoneGateMatcher.match(
            "- bear pr-check --all --project . --base origin/main --agent => 0",
            CanonicalDoneGateMatcher.GateKind.PR_CHECK
        );
        assertTrue(result.matched(), result.reason());
    }
}
