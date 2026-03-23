package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Generates React-flavored TypeScript artifacts for governed blocks.
 * 
 * Generated file layout for block key "user-dashboard":
 * <pre>
 * build/generated/bear/types/user-dashboard/
 *   UserDashboardFeaturePorts.ts
 *   UserDashboardFeatureLogic.ts
 *   UserDashboardFeatureWrapper.ts
 * build/generated/bear/wiring/
 *   user-dashboard.wiring.json
 * src/features/user-dashboard/impl/
 *   UserDashboardFeatureImpl.tsx   ← created once, never overwritten
 * </pre>
 */
public class ReactArtifactGenerator {

    /**
     * Generates FeaturePorts.ts file.
     */
    public void generatePorts(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String blockName = deriveBlockName(blockKey);
        String content = renderPorts(ir, blockKey);
        Path portsFile = outputDir.resolve(blockName + "FeaturePorts.ts");
        writeIfDifferent(portsFile, content);
    }

    /**
     * Generates FeatureLogic.ts file.
     */
    public void generateLogic(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String blockName = deriveBlockName(blockKey);
        String content = renderLogic(ir, blockKey);
        Path logicFile = outputDir.resolve(blockName + "FeatureLogic.ts");
        writeIfDifferent(logicFile, content);
    }

    /**
     * Generates FeatureWrapper.ts file.
     */
    public void generateWrapper(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String blockName = deriveBlockName(blockKey);
        String content = renderWrapper(ir, blockKey);
        Path wrapperFile = outputDir.resolve(blockName + "FeatureWrapper.ts");
        writeIfDifferent(wrapperFile, content);
    }

    /**
     * Generates user implementation skeleton (only if file doesn't exist).
     * Uses .tsx extension for React components.
     */
    public void generateUserImplSkeleton(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String blockName = deriveBlockName(blockKey);
        Path implFile = outputDir.resolve(blockName + "FeatureImpl.tsx");

        if (!Files.exists(implFile)) {
            String content = normalizeLineEndings(renderUserImplSkeleton(ir, blockKey));
            Files.createDirectories(outputDir);
            Files.writeString(implFile, content);
        }
    }

    // --- Rendering methods ---

    private String renderPorts(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("// BEAR-generated — do not edit\n\n");

        String blockName = deriveBlockName(blockKey);

        // Generate port interfaces from block effects
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                String portName = kebabToPascal(port.port());
                sb.append("export interface ").append(portName).append("Port {\n");
                for (String op : port.ops()) {
                    sb.append("  ").append(op).append("(input: unknown): unknown;\n");
                }
                sb.append("}\n\n");
            }
        }

        // Generate main FeaturePorts interface
        sb.append("export interface ").append(blockName).append("FeaturePorts {\n");
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                String portName = kebabToPascal(port.port());
                String fieldName = kebabToCamel(port.port());
                sb.append("  ").append(fieldName).append(": ").append(portName).append("Port;\n");
            }
        }
        sb.append("}\n");

        return sb.toString();
    }

    private String renderLogic(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("// BEAR-generated — do not edit\n\n");

        String blockName = deriveBlockName(blockKey);

        sb.append("import type { ").append(blockName).append("FeaturePorts } from './")
          .append(blockName).append("FeaturePorts';\n\n");

        // Generate request/result types for each operation
        for (BearIr.Operation op : ir.block().operations()) {
            String opName = op.name();
            String requestName = blockName + opName + "Request";
            String resultName = blockName + opName + "Result";

            sb.append("export interface ").append(requestName).append(" {\n");
            if (op.contract().inputs() != null) {
                for (BearIr.Field field : op.contract().inputs()) {
                    String tsType = mapType(field.type());
                    String memberName = kebabToCamel(field.name());
                    sb.append("  ").append(memberName).append(": ").append(tsType).append(";\n");
                }
            }
            sb.append("}\n\n");

            sb.append("export interface ").append(resultName).append(" {\n");
            if (op.contract().outputs() != null) {
                for (BearIr.Field field : op.contract().outputs()) {
                    String tsType = mapType(field.type());
                    String memberName = kebabToCamel(field.name());
                    sb.append("  ").append(memberName).append(": ").append(tsType).append(";\n");
                }
            }
            sb.append("}\n\n");
        }

        // Generate FeatureLogic interface
        sb.append("export interface ").append(blockName).append("FeatureLogic {\n");
        for (BearIr.Operation op : ir.block().operations()) {
            String opName = op.name();
            String requestName = blockName + opName + "Request";
            String resultName = blockName + opName + "Result";

            sb.append("  ").append(opName).append("(request: ").append(requestName)
              .append(", ports: ").append(blockName).append("FeaturePorts): ")
              .append(resultName).append(";\n");
        }
        sb.append("}\n");

        return sb.toString();
    }

    private String renderWrapper(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("// BEAR-generated — do not edit\n\n");

        String blockName = deriveBlockName(blockKey);

        sb.append("import type { ").append(blockName).append("FeatureLogic } from './")
          .append(blockName).append("FeatureLogic';\n");
        sb.append("import type { ").append(blockName).append("FeaturePorts } from './")
          .append(blockName).append("FeaturePorts';\n\n");

        sb.append("export function create").append(blockName).append("FeatureWrapper(\n");
        sb.append("  impl: ").append(blockName).append("FeatureLogic\n");
        sb.append("): ").append(blockName).append("FeatureLogic {\n");
        sb.append("  return impl;\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String renderUserImplSkeleton(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("// User-owned — BEAR will not overwrite this file\n\n");

        String blockName = deriveBlockName(blockKey);

        sb.append("import type { ").append(blockName).append("FeatureLogic } from '../../../../build/generated/bear/types/")
          .append(blockKey).append("/").append(blockName).append("FeatureLogic';\n");
        sb.append("import type { ").append(blockName).append("FeaturePorts } from '../../../../build/generated/bear/types/")
          .append(blockKey).append("/").append(blockName).append("FeaturePorts';\n\n");

        // Generate request/result type imports
        for (BearIr.Operation op : ir.block().operations()) {
            String requestName = blockName + op.name() + "Request";
            String resultName = blockName + op.name() + "Result";
            sb.append("import type { ").append(requestName).append(", ").append(resultName)
              .append(" } from '../../../../build/generated/bear/types/").append(blockKey)
              .append("/").append(blockName).append("FeatureLogic';\n");
        }
        sb.append("\n");

        sb.append("export class ").append(blockName).append("FeatureImpl implements ")
          .append(blockName).append("FeatureLogic {\n");

        for (BearIr.Operation op : ir.block().operations()) {
            String opName = op.name();
            String requestName = blockName + opName + "Request";
            String resultName = blockName + opName + "Result";

            sb.append("  ").append(opName).append("(request: ").append(requestName)
              .append(", ports: ").append(blockName).append("FeaturePorts): ")
              .append(resultName).append(" {\n");
            sb.append("    // TODO: implement\n");
            sb.append("    throw new Error('Not implemented');\n");
            sb.append("  }\n\n");
        }

        sb.append("}\n");

        return sb.toString();
    }

    // --- Lexical support helpers (public for use by ReactManifestGenerator and tests) ---

    /**
     * Converts a kebab-case string to PascalCase.
     * Example: "user-dashboard" → "UserDashboard"
     */
    public static String kebabToPascal(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        return Arrays.stream(kebab.split("-"))
                .map(part -> part.isEmpty() ? "" : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }

    /**
     * Converts a kebab-case string to camelCase.
     * Example: "user-dashboard" → "userDashboard"
     */
    public static String kebabToCamel(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        String pascal = kebabToPascal(kebab);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /**
     * Derives block name from block key (kebab-case → PascalCase).
     * Example: "user-dashboard" → "UserDashboard"
     */
    public static String deriveBlockName(String blockKey) {
        return kebabToPascal(blockKey);
    }

    /**
     * Maps BearIr FieldType to TypeScript type.
     */
    private static String mapType(BearIr.FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> "string";
            case INT -> "number";
            case DECIMAL -> "string";
            case BOOL -> "boolean";
            case ENUM -> "string";
        };
    }

    // --- File I/O helpers ---

    /**
     * Normalizes line endings to LF (Unix-style) for consistent output
     * across platforms.
     */
    public static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Writes content to file only if different from existing content.
     * Uses StandardOpenOption.SYNC for byte-stable writes (WSL2 filesystem caching).
     */
    void writeIfDifferent(Path file, String content) throws IOException {
        String normalized = normalizeLineEndings(content);
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
