package com.bear.kernel.target.python;

import com.bear.kernel.target.node.BoundaryDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PythonImportBoundaryResolverTest {

    @TempDir
    Path tempDir;

    private final PythonImportBoundaryResolver resolver = new PythonImportBoundaryResolver();

    @Test
    void sameBlockRelativeImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void sameBlockDeepRelativeImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("handlers/login.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".validation", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void sharedImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, sharedRoot);

        // Relative import going up and into _shared
        BoundaryDecision decision = resolver.resolve(importingFile, ".._shared.utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void generatedArtifactImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import to generated artifact
        // From src/blocks/user-auth/impl/ we need to go up 4 levels to reach project root,
        // then down into build/generated/bear/user-auth/
        // .... goes up 3 levels: impl -> user-auth -> blocks -> src
        // Then we need one more level to reach project root
        // So we use ..... (5 dots) to go up 4 levels from impl/
        BoundaryDecision decision = resolver.resolve(importingFile, ".....build.generated.bear.user_auth.user_auth_ports", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void siblingBlockImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path siblingRoot = projectRoot.resolve("src/blocks/payment");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, siblingRoot);

        // Relative import trying to reach sibling block
        BoundaryDecision decision = resolver.resolve(importingFile, "..payment.processor", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void escapingBlockRoot_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import escaping to nongoverned source
        BoundaryDecision decision = resolver.resolve(importingFile, "...utils", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void sharedImportsBlock_fail() {
        Path projectRoot = tempDir;
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = sharedRoot.resolve("utils.py");
        Set<Path> governedRoots = Set.of(sharedRoot, blockRoot);

        // _shared trying to import from a block
        BoundaryDecision decision = resolver.resolve(importingFile, "..user_auth.service", true, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("SHARED_IMPORTS_BLOCK", decision.failureReason());
    }

    @Test
    void stdlibImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of stdlib module
        BoundaryDecision decision = resolver.resolve(importingFile, "os", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void stdlibSubmoduleImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of stdlib submodule
        BoundaryDecision decision = resolver.resolve(importingFile, "os.path", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void thirdPartyImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of third-party package
        BoundaryDecision decision = resolver.resolve(importingFile, "requests", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void thirdPartySubmoduleImport_fail() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of third-party submodule
        BoundaryDecision decision = resolver.resolve(importingFile, "flask.app", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void bearGeneratedAbsoluteImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of BEAR-generated module
        BoundaryDecision decision = resolver.resolve(importingFile, "build.generated.bear.user_auth.user_auth_ports", false, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void parentRelativeImport_withinBlock_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("handlers/validators/email.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Go up two levels but stay within block
        BoundaryDecision decision = resolver.resolve(importingFile, "..utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    @Test
    void currentPackageImport_allowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Current package import (single dot)
        BoundaryDecision decision = resolver.resolve(importingFile, ".", true, governedRoots, projectRoot);

        assertTrue(decision.pass());
    }

    // ========== Deprecated stdlib modules now classified as third-party ==========

    @Test
    void distutilsImport_nowThirdParty() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, "distutils", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void impImport_nowThirdParty() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, "imp", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    @Test
    void allRemovedModules_nowThirdParty() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // All 21 modules removed in Python 3.12/3.13
        List<String> removedModules = List.of(
            "aifc", "audioop", "cgi", "cgitb", "chunk", "crypt", "distutils",
            "imghdr", "imp", "mailcap", "msilib", "nis", "nntplib", "ossaudiodev",
            "pipes", "sndhdr", "spwd", "sunau", "telnetlib", "uu", "xdrlib"
        );

        for (String module : removedModules) {
            BoundaryDecision decision = resolver.resolve(importingFile, module, false, governedRoots, projectRoot);
            assertTrue(decision.isFail(),
                "Removed module '" + module + "' should be classified as third-party");
            assertEquals("THIRD_PARTY_IMPORT", decision.failureReason(),
                "Removed module '" + module + "' should fail with THIRD_PARTY_IMPORT");
        }
    }

    @Test
    void removedModuleSubmodule_nowThirdParty() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Submodule of removed module
        BoundaryDecision decision = resolver.resolve(importingFile, "distutils.core", false, governedRoots, projectRoot);

        assertTrue(decision.isFail());
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    // ========== New stdlib modules remain classified correctly ==========

    @Test
    void tomllibImport_stillStdlib() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, "tomllib", false, governedRoots, projectRoot);

        assertTrue(decision.pass(), "tomllib should remain classified as stdlib");
    }

    @Test
    void zoneinfoImport_stillStdlib() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, "zoneinfo", false, governedRoots, projectRoot);

        assertTrue(decision.pass(), "zoneinfo should remain classified as stdlib");
    }

    // ========== Core stdlib modules still allowed (regression guard) ==========

    @Test
    void coreStdlibModules_stillAllowed() {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Spot-check core stdlib modules that should never be removed
        List<String> coreModules = List.of(
            "os", "sys", "json", "pathlib", "typing", "collections",
            "datetime", "hashlib", "logging", "subprocess", "unittest"
        );

        for (String module : coreModules) {
            BoundaryDecision decision = resolver.resolve(importingFile, module, false, governedRoots, projectRoot);
            assertTrue(decision.pass(),
                "Core stdlib module '" + module + "' should still be allowed");
        }
    }
}
