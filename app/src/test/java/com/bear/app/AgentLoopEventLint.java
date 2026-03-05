package com.bear.app;

import java.util.ArrayList;
import java.util.List;

final class AgentLoopEventLint {
    private AgentLoopEventLint() {
    }

    static List<String> lint(List<Event> events) {
        ArrayList<String> violations = new ArrayList<>();
        List<String> expectedSequence = List.of();
        int sequenceIndex = 0;

        for (Event event : events) {
            if (event instanceof GateRun gateRun) {
                if (!expectedSequence.isEmpty() && sequenceIndex != expectedSequence.size()) {
                    violations.add("Missing nextAction command execution before next gate run.");
                }
                expectedSequence = List.of();
                sequenceIndex = 0;
                if (gateRun.agentMode() && gateRun.exitCode() != 0 && gateRun.nextActionCommands() != null && !gateRun.nextActionCommands().isEmpty()) {
                    expectedSequence = List.copyOf(gateRun.nextActionCommands());
                }
                continue;
            }

            if (event instanceof Exec exec && !expectedSequence.isEmpty()) {
                if (sequenceIndex >= expectedSequence.size()) {
                    violations.add("Unexpected extra command after gate failure: " + exec.command());
                    continue;
                }
                String expected = expectedSequence.get(sequenceIndex);
                if (!expected.equals(exec.command())) {
                    violations.add("Command drift after gate failure. expected='" + expected + "' actual='" + exec.command() + "'");
                    continue;
                }
                sequenceIndex++;
            }
        }

        if (!expectedSequence.isEmpty() && sequenceIndex != expectedSequence.size()) {
            violations.add("Missing nextAction command execution at end of loop.");
        }

        return List.copyOf(violations);
    }

    sealed interface Event permits GateRun, Exec {
    }

    record GateRun(String command, int exitCode, boolean agentMode, List<String> nextActionCommands) implements Event {
    }

    record Exec(String command) implements Event {
    }
}
