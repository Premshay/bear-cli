package com.bear.kernel.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Capability template that emits a minimal read-store block scaffold.
 *
 * <p>Emits a v1 IR with one {@code Get<BlockName>} operation backed by a
 * {@code <blockName>Store} external effect port (ops: [get]).
 */
public final class ReadStoreTemplate implements CapabilityTemplate {

    @Override
    public String id() {
        return "read-store";
    }

    @Override
    public String description() {
        return "A block with one read operation and a data-store port (get).";
    }

    @Override
    public TemplatePack emit(TemplateParams params, Path projectRoot) throws IOException {
        String blockName = params.blockName();
        String pascalName = toPascalCase(blockName);
        String camelName = toCamelCase(blockName);
        String portName = camelName + "Store";

        String yaml = buildIrYaml(pascalName, portName);

        Path specDir = projectRoot.resolve("spec");
        Files.createDirectories(specDir);

        Path irPath = specDir.resolve(blockName + ".ir.yaml");
        Files.writeString(irPath, yaml, StandardCharsets.UTF_8);

        Path implStubPath = projectRoot.resolve(
                "src/main/java/blocks/" + blockName + "/impl/" + pascalName + "Impl.java");

        return new TemplatePack(irPath, implStubPath, List.of());
    }

    // -------------------------------------------------------------------------
    // YAML construction
    // -------------------------------------------------------------------------

    private static String buildIrYaml(String pascalName, String portName) {
        return "version: v1\n"
                + "block:\n"
                + "  name: " + pascalName + "\n"
                + "  kind: logic\n"
                + "  operations:\n"
                + "    - name: Get" + pascalName + "\n"
                + "      contract:\n"
                + "        inputs:\n"
                + "          - name: id\n"
                + "            type: string\n"
                + "        outputs:\n"
                + "          - name: result\n"
                + "            type: string\n"
                + "      uses:\n"
                + "        allow:\n"
                + "          - port: " + portName + "\n"
                + "            ops: [get]\n"
                + "      idempotency:\n"
                + "        mode: none\n"
                + "  effects:\n"
                + "    allow:\n"
                + "      - port: " + portName + "\n"
                + "        ops: [get]\n";
    }

    // -------------------------------------------------------------------------
    // Case conversion helpers
    // -------------------------------------------------------------------------

    /** Converts kebab-case to PascalCase: {@code my-block} → {@code MyBlock}. */
    static String toPascalCase(String kebab) {
        return Arrays.stream(kebab.split("-"))
                .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining());
    }

    /** Converts kebab-case to camelCase: {@code my-block} → {@code myBlock}. */
    static String toCamelCase(String kebab) {
        String[] parts = kebab.split("-");
        if (parts.length == 0) {
            return kebab;
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
