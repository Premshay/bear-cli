package com.bear.kernel.target.react;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Computes dependency governance delta for pr-check.
 * Checks whether package.json or pnpm-lock.yaml differ between base and head snapshots.
 * 
 * <p>Finding structure:
 * <ul>
 *   <li>code: BOUNDARY_EXPANDING</li>
 *   <li>path: relative file path (package.json or pnpm-lock.yaml)</li>
 *   <li>detail: "dependency governance: file changed"</li>
 * </ul>
 * 
 * <p>Exit code: 5 when any findings present (handled by app/ layer).
 */
public class ReactPrCheckContributor {

    private static final List<String> DEPENDENCY_FILES = List.of("package.json", "pnpm-lock.yaml");

    /**
     * Represents a dependency governance finding.
     * 
     * @param code the finding code (BOUNDARY_EXPANDING)
     * @param path the relative file path
     * @param detail description of the finding
     */
    public record DependencyDeltaFinding(String code, String path, String detail) {}

    /**
     * Computes dependency governance delta between base and head snapshots.
     * 
     * <p>Algorithm:
     * <pre>
     * for each file in ["package.json", "pnpm-lock.yaml"]:
     *   basePath = baseRoot / file
     *   headPath = headRoot / file
     *   if both exist AND bytes differ → findings.add(BOUNDARY_EXPANDING, file)
     *   if headPath exists AND basePath missing → findings.add(BOUNDARY_EXPANDING, file)
     * return findings
     * </pre>
     *
     * @param baseRoot the base snapshot root directory
     * @param headRoot the head snapshot root directory
     * @return list of dependency delta findings
     * @throws IOException if file reading fails
     */
    public List<DependencyDeltaFinding> computeDelta(Path baseRoot, Path headRoot) throws IOException {
        List<DependencyDeltaFinding> findings = new ArrayList<>();

        for (String fileName : DEPENDENCY_FILES) {
            Path basePath = baseRoot.resolve(fileName);
            Path headPath = headRoot.resolve(fileName);

            boolean baseExists = Files.isRegularFile(basePath);
            boolean headExists = Files.isRegularFile(headPath);

            if (headExists && baseExists) {
                // Both exist — check if bytes differ
                byte[] baseBytes = Files.readAllBytes(basePath);
                byte[] headBytes = Files.readAllBytes(headPath);

                if (!Arrays.equals(baseBytes, headBytes)) {
                    findings.add(new DependencyDeltaFinding(
                        "BOUNDARY_EXPANDING",
                        fileName,
                        "dependency governance: file changed"
                    ));
                }
            } else if (headExists && !baseExists) {
                // Head has file, base doesn't — new file added
                findings.add(new DependencyDeltaFinding(
                    "BOUNDARY_EXPANDING",
                    fileName,
                    "dependency governance: file changed"
                ));
            }
            // If base exists but head doesn't, or neither exists — no finding
            // (file removal is not boundary-expanding in this context)
        }

        return findings;
    }
}
