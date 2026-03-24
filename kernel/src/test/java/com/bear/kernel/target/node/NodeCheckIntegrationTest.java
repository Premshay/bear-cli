package com.bear.kernel.target.node;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C integration tests for Node check pipeline.
 * Tests the 6 new fixtures covering dynamic import enforcement,
 * project verification, and @/* alias resolution scenarios.
 *
 * Exit code mapping:
 * - 0: success (clean project, alias to same block)
 * - 4: project verification failure (tsc type error)
 * - 7: boundary bypass (dynamic import, alias to sibling block, no tsconfig paths)
 *
 * Note: All containment findings use CODE=BOUNDARY_BYPASS in the error envelope.
 * The specific rule (e.g., DYNAMIC_IMPORT_FORBIDDEN, BOUNDARY_BYPASS) is available
 * via BoundaryBypassFinding.rule() and appears in the RULE= diagnostic line.
 */
class NodeCheckIntegrationTest {

    // ---------------------------------------------------------------
    // check-clean → exit 0 (all pipeline stages pass)
    // ---------------------------------------------------------------

    @Test
    void checkClean_noFindings_exit0() throws Exception {
        Path fixture = getFixturePath("check-clean");

        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("my-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertTrue(findings.isEmpty(),
            "check-clean should have no containment violations (exit 0)");
    }

    // ---------------------------------------------------------------
    // check-dynamic-import → exit 7, DYNAMIC_IMPORT_FORBIDDEN
    // ---------------------------------------------------------------

    @Test
    void checkDynamicImport_producesFindings_exit7() throws Exception {
        Path fixture = getFixturePath("check-dynamic-import");

        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("my-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(),
            "check-dynamic-import should produce findings (exit 7)");
        assertTrue(findings.stream().anyMatch(f ->
                "DYNAMIC_IMPORT_FORBIDDEN".equals(f.rule())),
            "finding should have rule DYNAMIC_IMPORT_FORBIDDEN");
        assertTrue(findings.stream().anyMatch(f ->
                f.path().contains("my-block") && f.detail().contains("./other")),
            "finding should reference the dynamic import specifier");
    }

    // ---------------------------------------------------------------
    // check-project-verification-failure → exit 4 (tsc type error)
    // Note: This test validates the fixture structure; actual tsc
    // execution requires pnpm/tsc on PATH (tested in CI environment)
    // ---------------------------------------------------------------

    @Test
    void checkProjectVerificationFailure_fixtureHasTypeError() throws Exception {
        Path fixture = getFixturePath("check-project-verification-failure");

        // Verify fixture exists and has the expected structure
        assertTrue(fixture.resolve("src/blocks/my-block/index.ts").toFile().exists(),
            "fixture should have index.ts with type error");
        assertTrue(fixture.resolve("tsconfig.json").toFile().exists(),
            "fixture should have tsconfig.json");

        // Containment scan should pass (no boundary violations)
        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("my-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertTrue(findings.isEmpty(),
            "check-project-verification-failure should have no containment violations; " +
            "the type error is caught by tsc --noEmit (exit 4), not containment scan");
    }

    // ---------------------------------------------------------------
    // check-alias-same-block → exit 0 (@/* alias to same block passes)
    // ---------------------------------------------------------------

    @Test
    void checkAliasSameBlock_noFindings_exit0() throws Exception {
        Path fixture = getFixturePath("check-alias-same-block");

        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("my-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertTrue(findings.isEmpty(),
            "check-alias-same-block should have no containment violations (exit 0); " +
            "@/* alias resolving within same block is allowed");
    }

    // ---------------------------------------------------------------
    // check-alias-sibling-block → exit 7, BOUNDARY_BYPASS
    // ---------------------------------------------------------------

    @Test
    void checkAliasSiblingBlock_producesFindings_exit7() throws Exception {
        Path fixture = getFixturePath("check-alias-sibling-block");

        NodeTarget target = new NodeTarget();
        WiringManifest myBlockManifest = makeManifest("my-block");
        WiringManifest otherBlockManifest = makeManifest("other-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(myBlockManifest, otherBlockManifest), Set.of());

        assertFalse(findings.isEmpty(),
            "check-alias-sibling-block should produce findings (exit 7)");
        assertTrue(findings.stream().anyMatch(f ->
                "BOUNDARY_BYPASS".equals(f.rule())),
            "finding should have rule BOUNDARY_BYPASS");
        assertTrue(findings.stream().anyMatch(f ->
                f.path().contains("my-block") && f.detail().contains("@/blocks/other-block")),
            "finding should reference the @/* alias to sibling block");
    }

    // ---------------------------------------------------------------
    // check-alias-no-tsconfig-paths → exit 7, BOUNDARY_BYPASS
    // ---------------------------------------------------------------

    @Test
    void checkAliasNoTsconfigPaths_producesFindings_exit7() throws Exception {
        Path fixture = getFixturePath("check-alias-no-tsconfig-paths");

        NodeTarget target = new NodeTarget();
        WiringManifest manifest = makeManifest("my-block");
        List<BoundaryBypassFinding> findings =
            target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(),
            "check-alias-no-tsconfig-paths should produce findings (exit 7)");
        assertTrue(findings.stream().anyMatch(f ->
                "BOUNDARY_BYPASS".equals(f.rule())),
            "finding should have rule BOUNDARY_BYPASS");
        assertTrue(findings.stream().anyMatch(f ->
                f.detail().contains("@/")),
            "finding should reference the @/ import that cannot be resolved");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Path getFixturePath(String fixtureName) throws URISyntaxException {
        return Paths.get(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/node/" + fixtureName)
            ).toURI()
        );
    }

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "Impl.ts",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
