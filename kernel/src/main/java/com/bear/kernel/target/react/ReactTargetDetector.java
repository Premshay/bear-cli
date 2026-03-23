package com.bear.kernel.target.react;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.TargetDetector;
import com.bear.kernel.target.TargetId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects React projects (Vite+React and Next.js App Router).
 * Detection is purely file-system based — no process execution.
 */
public class ReactTargetDetector implements TargetDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public DetectedTarget detect(Path projectRoot) {
        // Check for package.json
        Path packageJson = projectRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            return DetectedTarget.none();
        }

        // Parse package.json (strict JSON)
        JsonNode pkg;
        try {
            pkg = OBJECT_MAPPER.readTree(packageJson.toFile());
        } catch (Exception e) {
            return DetectedTarget.none();
        }

        // Check "type": "module"
        JsonNode typeNode = pkg.get("type");
        if (typeNode == null || !"module".equals(typeNode.asText())) {
            return DetectedTarget.none();
        }

        // Check "packageManager" starts with "pnpm"
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

        // Check for react and react-dom in dependencies
        JsonNode deps = pkg.get("dependencies");
        if (deps == null || !deps.has("react") || !deps.has("react-dom")) {
            return DetectedTarget.none();
        }

        // Check for pnpm-workspace.yaml (unsupported)
        Path workspaceYaml = projectRoot.resolve("pnpm-workspace.yaml");
        if (Files.exists(workspaceYaml)) {
            return DetectedTarget.unsupported(TargetId.REACT, "pnpm workspace detected");
        }

        // Check for vite.config.ts and next.config.*
        boolean hasVite = Files.exists(projectRoot.resolve("vite.config.ts"));
        boolean hasNext = Files.exists(projectRoot.resolve("next.config.js"))
                || Files.exists(projectRoot.resolve("next.config.mjs"))
                || Files.exists(projectRoot.resolve("next.config.ts"));

        // Ambiguous: both Vite and Next.js config present
        if (hasVite && hasNext) {
            return DetectedTarget.unsupported(TargetId.REACT, 
                "ambiguous React project shape: both Vite and Next.js config detected");
        }

        // Next.js App Router
        if (hasNext) {
            return DetectedTarget.supported(TargetId.REACT, ReactProjectShape.NEXTJS_APP_ROUTER.value());
        }

        // Vite + React
        if (hasVite) {
            return DetectedTarget.supported(TargetId.REACT, ReactProjectShape.VITE_REACT.value());
        }

        // Neither Vite nor Next.js config found
        return DetectedTarget.none();
    }
}
