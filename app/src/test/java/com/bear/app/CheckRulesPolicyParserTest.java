package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckRulesPolicyParserTest {
    @Test
    void parseMissingPolicyFileUsesDefaults(@TempDir Path tempDir) throws Exception {
        CheckRulesPolicy policy = CheckRulesPolicyParser.parse(tempDir);
        assertTrue(policy.implContainment());
    }

    @Test
    void parseValidPolicyReadsImplContainmentToggle(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(".bear/policy/check-rules.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "impl_containment=false\n");

        CheckRulesPolicy policy = CheckRulesPolicyParser.parse(tempDir);
        assertEquals(false, policy.implContainment());
    }

    @Test
    void parseRejectsUnknownKey(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(".bear/policy/check-rules.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "unknown=true\n");

        PolicyValidationException ex = assertThrows(PolicyValidationException.class, () -> CheckRulesPolicyParser.parse(tempDir));
        assertEquals(CheckRulesPolicyParser.CHECK_RULES_POLICY_PATH, ex.policyPath());
        assertTrue(ex.getMessage().contains("unknown key"));
    }

    @Test
    void parseRejectsDuplicateKey(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(".bear/policy/check-rules.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "impl_containment=true\nimpl_containment=true\n");

        PolicyValidationException ex = assertThrows(PolicyValidationException.class, () -> CheckRulesPolicyParser.parse(tempDir));
        assertTrue(ex.getMessage().contains("duplicate key"));
    }

    @Test
    void parseRejectsMalformedEntry(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(".bear/policy/check-rules.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "impl_containment\n");

        PolicyValidationException ex = assertThrows(PolicyValidationException.class, () -> CheckRulesPolicyParser.parse(tempDir));
        assertTrue(ex.getMessage().contains("expected key=value"));
    }

    @Test
    void parseRejectsMalformedValue(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve(".bear/policy/check-rules.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "impl_containment=yes\n");

        PolicyValidationException ex = assertThrows(PolicyValidationException.class, () -> CheckRulesPolicyParser.parse(tempDir));
        assertTrue(ex.getMessage().contains("invalid value"));
    }
}
