package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends a new block entry to {@code bear.blocks.yaml}.
 *
 * <p>If the file does not exist, creates it with a {@code version: v1} header.
 * If it exists, reads it with {@link BlockIndexParser} to verify the block name
 * is not already present, then appends the new entry as raw YAML text.
 *
 * <p>Simple append strategy — no full YAML re-serialization to preserve existing formatting.
 */
final class BlocksYamlUpdater {

    private static final String BLOCKS_YAML = "bear.blocks.yaml";

    private BlocksYamlUpdater() {
    }

    /**
     * Appends a new block entry to {@code bear.blocks.yaml} under {@code projectRoot}.
     *
     * @param projectRoot the project root directory
     * @param blockName   the block name (e.g. {@code my-block})
     * @param irRelPath   the IR path relative to projectRoot (e.g. {@code spec/my-block.ir.yaml})
     * @throws IOException                   on I/O failure
     * @throws BlockIndexValidationException if the existing file is malformed
     * @throws BlockAlreadyExistsException   if the block name is already registered
     */
    static void appendEntry(Path projectRoot, String blockName, String irRelPath)
            throws IOException, BlockIndexValidationException, BlockAlreadyExistsException {

        Path indexPath = projectRoot.resolve(BLOCKS_YAML);
        String entry = buildEntry(blockName, irRelPath);

        if (!Files.exists(indexPath)) {
            // Create new file with version header + blocks list
            String content = "version: v1\nblocks:\n" + entry;
            Files.writeString(indexPath, content, StandardCharsets.UTF_8);
            return;
        }

        // File exists — double-check block name is not already present
        BlockIndexParser parser = new BlockIndexParser();
        BlockIndex index = parser.parse(projectRoot, indexPath);
        for (BlockIndexEntry existing : index.blocks()) {
            if (existing.name().equals(blockName)) {
                throw new BlockAlreadyExistsException(blockName);
            }
        }

        // Append the new entry
        String existing = Files.readString(indexPath, StandardCharsets.UTF_8);
        String appended = existing.endsWith("\n") ? existing + entry : existing + "\n" + entry;
        Files.writeString(indexPath, appended, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String buildEntry(String blockName, String irRelPath) {
        return "- name: " + blockName + "\n"
                + "  ir: " + irRelPath + "\n"
                + "  projectRoot: .\n";
    }

    /** Thrown when the block name is already registered in bear.blocks.yaml. */
    static final class BlockAlreadyExistsException extends Exception {
        private final String blockName;

        BlockAlreadyExistsException(String blockName) {
            super("Block already exists: " + blockName);
            this.blockName = blockName;
        }

        String blockName() {
            return blockName;
        }
    }
}
