package com.bear.kernel.target.node;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

public class NodeTargetDetector implements TargetDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public DetectedTarget detect(Path projectRoot) {
        // Check for package.json
        Path packageJson = projectRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return DetectedTarget.none();
        }

        // Parse package.json (strict JSON) to check type and packageManager
        JsonNode pkg;
        try {
            pkg = OBJECT_MAPPER.readTree(packageJson.toFile());
        } catch (Exception e) {
            return DetectedTarget.none();
        }

        JsonNode typeNode = pkg.get("type");
        if (typeNode == null || !"module".equals(typeNode.asText())) {
            return DetectedTarget.none();
        }
        JsonNode pmNode = pkg.get("packageManager");
        if (pmNode == null || !pmNode.asText().startsWith("pnpm")) {
            return DetectedTarget.none();
        }

        // Check for pnpm-lock.yaml
        Path pnpmLock = projectRoot.resolve("pnpm-lock.yaml");
        if (!Files.exists(pnpmLock)) {
            return DetectedTarget.none();
        }

        // Check for tsconfig.json
        Path tsconfig = projectRoot.resolve("tsconfig.json");
        if (!Files.exists(tsconfig)) {
            return DetectedTarget.none();
        }

        // Check for pnpm-workspace.yaml (unsupported)
        Path workspaceYaml = projectRoot.resolve("pnpm-workspace.yaml");
        if (Files.exists(workspaceYaml)) {
            return DetectedTarget.unsupported(TargetId.NODE, "pnpm workspace detected");
        }

        // Exclude React projects — they belong to ReactTargetDetector
        // If project has react or react-dom, let ReactTargetDetector decide
        JsonNode deps = pkg.get("dependencies");
        if (deps != null && (deps.has("react") || deps.has("react-dom"))) {
            return DetectedTarget.none();
        }

        return DetectedTarget.supported(TargetId.NODE, "Node project detected");
    }
}
