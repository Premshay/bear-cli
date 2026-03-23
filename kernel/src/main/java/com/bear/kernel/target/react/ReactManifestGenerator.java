package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates wiring manifest files for React blocks.
 * 
 * Output path: build/generated/bear/wiring/{blockKey}.wiring.json
 * 
 * The wiring manifest schema is target-agnostic (IR v1 contract).
 * Delegates to TargetManifestParsers for parsing.
 */
public class ReactManifestGenerator {

    /**
     * Generates wiring.json manifest file.
     */
    public void generateWiringManifest(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String content = renderWiringManifest(ir, blockKey);
        Path manifestFile = outputDir.resolve(blockKey + ".wiring.json");
        writeIfDifferent(manifestFile, content);
    }

    private String renderWiringManifest(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        String blockName = ReactArtifactGenerator.deriveBlockName(blockKey);

        sb.append("{\n");
        sb.append("  \"schemaVersion\": \"v3\",\n");
        sb.append("  \"blockKey\": \"").append(blockKey).append("\",\n");
        sb.append("  \"entrypointFqcn\": \"").append(blockName).append("FeatureWrapper\",\n");
        sb.append("  \"logicInterfaceFqcn\": \"").append(blockName).append("FeatureLogic\",\n");
        sb.append("  \"implFqcn\": \"").append(blockName).append("FeatureImpl\",\n");
        sb.append("  \"implSourcePath\": \"src/features/").append(blockKey).append("/impl/")
          .append(blockName).append("FeatureImpl.tsx\",\n");
        sb.append("  \"blockRootSourceDir\": \"src/features/").append(blockKey).append("\",\n");

        // Governed source roots
        sb.append("  \"governedSourceRoots\": [\n");
        sb.append("    \"src/features/").append(blockKey).append("\",\n");
        sb.append("    \"src/shared\"\n");
        sb.append("  ],\n");

        // Required effect ports
        sb.append("  \"requiredEffectPorts\": [\n");
        List<String> effectPorts = new ArrayList<>();
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                effectPorts.add("    \"" + port.port() + "\"");
            }
        }
        sb.append(String.join(",\n", effectPorts));
        if (!effectPorts.isEmpty()) {
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Constructor port params
        sb.append("  \"constructorPortParams\": [\n");
        List<String> constructorParams = new ArrayList<>();
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                String portName = ReactArtifactGenerator.kebabToPascal(port.port());
                constructorParams.add("    \"" + portName + "Port\"");
            }
        }
        sb.append(String.join(",\n", constructorParams));
        if (!constructorParams.isEmpty()) {
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Logic required ports
        sb.append("  \"logicRequiredPorts\": [\n");
        List<String> logicPorts = new ArrayList<>();
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                logicPorts.add("    \"" + port.port() + "\"");
            }
        }
        sb.append(String.join(",\n", logicPorts));
        if (!logicPorts.isEmpty()) {
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Wrapper owned semantic ports
        sb.append("  \"wrapperOwnedSemanticPorts\": [],\n");

        // Wrapper owned semantic checks
        sb.append("  \"wrapperOwnedSemanticChecks\": [],\n");

        // Block port bindings
        sb.append("  \"blockPortBindings\": [");
        List<String> bindings = new ArrayList<>();
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                if (port.kind() == BearIr.EffectPortKind.BLOCK && port.targetBlock() != null) {
                    String portName = ReactArtifactGenerator.kebabToPascal(port.port());
                    String targetBlockName = ReactArtifactGenerator.deriveBlockName(port.targetBlock());
                    
                    StringBuilder binding = new StringBuilder();
                    binding.append("{\n");
                    binding.append("      \"port\": \"").append(port.port()).append("\",\n");
                    binding.append("      \"targetBlock\": \"").append(port.targetBlock()).append("\",\n");
                    binding.append("      \"targetOps\": [");
                    if (port.targetOps() != null && !port.targetOps().isEmpty()) {
                        List<String> ops = port.targetOps().stream()
                            .map(op -> "\"" + op + "\"")
                            .toList();
                        binding.append(String.join(", ", ops));
                    }
                    binding.append("],\n");
                    binding.append("      \"portInterfaceFqcn\": \"").append(portName).append("Port\",\n");
                    binding.append("      \"expectedClientImplFqcn\": \"").append(targetBlockName).append("FeatureImpl\"\n");
                    binding.append("    }");
                    bindings.add("    " + binding.toString());
                }
            }
        }
        if (!bindings.isEmpty()) {
            sb.append("\n");
            sb.append(String.join(",\n", bindings));
            sb.append("\n  ");
        }
        sb.append("]\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Writes content to file only if different from existing content.
     * Uses StandardOpenOption.SYNC for byte-stable writes.
     */
    private void writeIfDifferent(Path file, String content) throws IOException {
        String normalized = ReactArtifactGenerator.normalizeLineEndings(content);
        if (Files.exists(file)) {
            String existing = Files.readString(file);
            if (existing.equals(normalized)) {
                return;
            }
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, normalized,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.SYNC);
    }
}
