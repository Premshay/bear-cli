package com.bear.kernel.target.react.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.TargetCheckIssue;
import com.bear.kernel.target.TargetCheckIssueKind;
import com.bear.kernel.target.react.ReactTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ReactTarget drift gate (P19-P20).
 * 
 * P19: No drift on fresh compile — checkDrift() on freshly compiled workspace returns empty findings
 * P20: Drift detected on modification — checkDrift() returns non-empty findings when generated artifact modified
 * 
 * Uses plain JUnit 5 with 100+ iterations over generated inputs.
 */
class ReactDriftGateProperties {

    private ReactTarget target;
    private Random random;

    @BeforeEach
    void setUp() {
        target = new ReactTarget();
        random = new Random(42); // Fixed seed for reproducibility
    }

    /**
     * P19: No drift on fresh compile — checkDrift() on freshly compiled workspace returns empty findings.
     * 
     * For any valid BearIr input, checkDrift() on a freshly compiled workspace returns an empty findings list.
     * 
     * Validates: Requirement 10.1
     */
    @Test
    void p19_noDriftOnFreshCompile(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 100; i++) {
            Path projectRoot = tempDir.resolve("project-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Check drift - should be empty
            List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, blockKey);
            
            assertTrue(findings.isEmpty(),
                "P19 violated: checkDrift() should return empty findings for freshly compiled workspace. " +
                "Block key: " + blockKey + ", Findings: " + findings);
        }
    }

    /**
     * P20: Drift detected on modification — checkDrift() returns non-empty findings when generated artifact modified.
     * 
     * For any generated artifact that has been modified (byte-level change), checkDrift() returns
     * a non-empty findings list with DRIFT_DETECTED.
     * 
     * Validates: Requirement 10.2
     */
    @Test
    void p20_driftDetectedOnModification(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 100; i++) {
            Path projectRoot = tempDir.resolve("project-mod-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Modify a random generated artifact
            String blockKeyKebab = toKebabCase(blockKey);
            Path typesDir = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
            
            // Pick a random file to modify
            String[] artifactNames = {
                deriveBlockName(blockKey) + "FeaturePorts.ts",
                deriveBlockName(blockKey) + "FeatureLogic.ts",
                deriveBlockName(blockKey) + "FeatureWrapper.ts"
            };
            String artifactToModify = artifactNames[random.nextInt(artifactNames.length)];
            Path artifactPath = typesDir.resolve(artifactToModify);
            
            if (Files.exists(artifactPath)) {
                String original = Files.readString(artifactPath);
                String modified = original + "\n// Modified at iteration " + i;
                Files.writeString(artifactPath, modified);
                
                // Check drift - should detect modification
                List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, blockKey);
                
                assertFalse(findings.isEmpty(),
                    "P20 violated: checkDrift() should return non-empty findings when artifact modified. " +
                    "Block key: " + blockKey + ", Modified: " + artifactToModify);
                
                boolean hasDriftDetected = findings.stream()
                    .anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED);
                assertTrue(hasDriftDetected,
                    "P20 violated: findings should include DRIFT_DETECTED. " +
                    "Block key: " + blockKey + ", Findings: " + findings);
            }
        }
    }

    /**
     * Property: Drift detection is deterministic - same input produces same output.
     * 
     * Validates: Deterministic behavior requirement
     */
    @Test
    void driftDetection_isDeterministic(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 50; i++) {
            Path projectRoot = tempDir.resolve("project-det-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Check drift multiple times
            List<TargetCheckIssue> findings1 = target.checkDrift(ir, projectRoot, blockKey);
            List<TargetCheckIssue> findings2 = target.checkDrift(ir, projectRoot, blockKey);
            List<TargetCheckIssue> findings3 = target.checkDrift(ir, projectRoot, blockKey);
            
            assertEquals(findings1.size(), findings2.size(),
                "Drift detection should be deterministic (size mismatch 1-2)");
            assertEquals(findings2.size(), findings3.size(),
                "Drift detection should be deterministic (size mismatch 2-3)");
        }
    }

    /**
     * Property: Missing baseline is detected when artifact is deleted.
     * 
     * Validates: Requirement 10.3
     */
    @Test
    void missingBaseline_detectedWhenArtifactDeleted(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 50; i++) {
            Path projectRoot = tempDir.resolve("project-del-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Delete a generated artifact
            String blockKeyKebab = toKebabCase(blockKey);
            Path portsFile = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab + "/" 
                + deriveBlockName(blockKey) + "FeaturePorts.ts");
            
            if (Files.exists(portsFile)) {
                Files.delete(portsFile);
                
                // Check drift - should detect missing baseline
                List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, blockKey);
                
                assertFalse(findings.isEmpty(),
                    "Should detect missing baseline when artifact deleted");
                
                boolean hasMissingBaseline = findings.stream()
                    .anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_MISSING_BASELINE);
                assertTrue(hasMissingBaseline,
                    "Findings should include DRIFT_MISSING_BASELINE");
            }
        }
    }

    /**
     * Property: User impl modifications do NOT trigger drift.
     * 
     * Validates: Requirement 10.4
     */
    @Test
    void userImplModifications_doNotTriggerDrift(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 50; i++) {
            Path projectRoot = tempDir.resolve("project-impl-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Modify user impl
            String blockKeyKebab = toKebabCase(blockKey);
            Path implFile = projectRoot.resolve("src/features/" + blockKeyKebab + "/impl/" 
                + deriveBlockName(blockKey) + "FeatureImpl.tsx");
            
            if (Files.exists(implFile)) {
                String original = Files.readString(implFile);
                String modified = original + "\n// User's custom code at iteration " + i;
                Files.writeString(implFile, modified);
                
                // Check drift - should NOT detect user impl changes
                List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, blockKey);
                
                assertTrue(findings.isEmpty(),
                    "User impl modifications should NOT trigger drift. " +
                    "Block key: " + blockKey + ", Findings: " + findings);
            }
        }
    }


    /**
     * Property: Wiring manifest drift is detected.
     * 
     * Validates: Requirement 10.2 (wiring manifest is also checked for drift)
     */
    @Test
    void wiringManifestDrift_isDetected(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 50; i++) {
            Path projectRoot = tempDir.resolve("project-wiring-" + i);
            Files.createDirectories(projectRoot);
            
            String blockKey = generateRandomBlockKey();
            BearIr ir = generateRandomIr(blockKey);
            
            // Compile to workspace
            target.compile(ir, projectRoot, blockKey);
            
            // Modify wiring manifest
            String blockKeyKebab = toKebabCase(blockKey);
            Path wiringFile = projectRoot.resolve("build/generated/bear/wiring/" + blockKeyKebab + ".wiring.json");
            
            if (Files.exists(wiringFile)) {
                String original = Files.readString(wiringFile);
                // Add a comment-like modification (JSON doesn't support comments, but this changes bytes)
                String modified = original.replace("\"schemaVersion\"", "\"schemaVersion\" ");
                Files.writeString(wiringFile, modified);
                
                // Check drift - should detect modification
                List<TargetCheckIssue> findings = target.checkDrift(ir, projectRoot, blockKey);
                
                assertFalse(findings.isEmpty(),
                    "Should detect drift when wiring manifest modified");
            }
        }
    }

    // --- Helper methods ---

    private String generateRandomBlockKey() {
        String[] words = {"user", "product", "order", "payment", "auth", "dashboard", "catalog", "cart"};
        int wordCount = random.nextInt(2) + 1; // 1-2 words
        
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < wordCount; j++) {
            if (j > 0) sb.append("-");
            sb.append(words[random.nextInt(words.length)]);
        }
        return sb.toString();
    }

    private BearIr generateRandomIr(String blockKey) {
        String blockName = deriveBlockName(blockKey);
        
        // Generate 1-3 operations
        int opCount = random.nextInt(3) + 1;
        List<BearIr.Operation> operations = new java.util.ArrayList<>();
        
        for (int i = 0; i < opCount; i++) {
            String opName = "Op" + i;
            operations.add(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input-" + i, BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("output-" + i, BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()),
                null,
                null
            ));
        }
        
        return new BearIr(
            "v1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                operations,
                new BearIr.Effects(List.of()),
                new BearIr.Impl(List.of()),
                null,
                null
            )
        );
    }

    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String deriveBlockName(String blockKey) {
        if (blockKey == null || blockKey.isEmpty()) {
            return blockKey;
        }
        return java.util.Arrays.stream(blockKey.split("-"))
            .map(part -> part.isEmpty() ? "" : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
            .collect(java.util.stream.Collectors.joining());
    }
}
