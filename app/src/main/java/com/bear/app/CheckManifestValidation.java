package com.bear.app;

import java.util.List;

final class CheckManifestValidation {
    private CheckManifestValidation() {
    }

    static CheckResult validateWiringManifestSemantics(WiringManifest manifest, String path) {
        if (manifest.logicInterfaceFqcn() == null || manifest.logicInterfaceFqcn().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing logicInterfaceFqcn";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                line
            );
        }
        if (manifest.implFqcn() == null || manifest.implFqcn().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing implFqcn";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                line
            );
        }
        if (!"v2".equals(manifest.schemaVersion())) {
            String line = "check: MANIFEST_INVALID: unsupported wiring schema version: " + manifest.schemaVersion();
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with `bear compile` so v2 wiring metadata is present, then rerun `bear check`.",
                line
            );
        }
        if (manifest.blockRootSourceDir() == null || manifest.blockRootSourceDir().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing blockRootSourceDir";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed block root metadata and rerun `bear check`.",
                line
            );
        }
        if (manifest.governedSourceRoots() == null || manifest.governedSourceRoots().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing governedSourceRoots";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed source root metadata and rerun `bear check`.",
                line
            );
        }
        for (String semanticPort : manifest.wrapperOwnedSemanticPorts()) {
            if (manifest.logicRequiredPorts().contains(semanticPort)) {
                String line = "check: MANIFEST_INVALID: wrapperOwnedSemanticPorts overlaps logicRequiredPorts: " + semanticPort;
                return checkFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    CliCodes.MANIFEST_INVALID,
                    path,
                    "Regenerate wiring so semantic ports are wrapper-owned only, then rerun `bear check`.",
                    line
                );
            }
        }
        return null;
    }

    static boolean isManifestSemanticFieldError(ManifestParseException e) {
        String code = e.reasonCode();
        return "MISSING_KEY_logicInterfaceFqcn".equals(code)
            || "MISSING_KEY_implFqcn".equals(code)
            || "MISSING_KEY_logicRequiredPorts".equals(code)
            || "MISSING_KEY_wrapperOwnedSemanticPorts".equals(code)
            || "MISSING_KEY_wrapperOwnedSemanticChecks".equals(code)
            || "MISSING_KEY_blockRootSourceDir".equals(code)
            || "MISSING_KEY_governedSourceRoots".equals(code)
            || "MALFORMED_ARRAY_logicRequiredPorts".equals(code)
            || "MALFORMED_ARRAY_wrapperOwnedSemanticPorts".equals(code)
            || "MALFORMED_ARRAY_wrapperOwnedSemanticChecks".equals(code)
            || "MALFORMED_ARRAY_governedSourceRoots".equals(code)
            || "INVALID_STRING_ARRAY".equals(code)
            || "UNSUPPORTED_WIRING_SCHEMA_VERSION".equals(code)
            || "INVALID_GOVERNED_SOURCE_ROOTS".equals(code)
            || PortImplContainmentScanner.AMBIGUOUS_PORT_OWNER_REASON_CODE.equals(code)
            || "INVALID_ROOT_PATH_blockRootSourceDir".equals(code)
            || "INVALID_ROOT_PATH_governedSourceRoots".equals(code);
    }

    static String boundaryBypassRemediation(String rule) {
        if ("PORT_IMPL_OUTSIDE_GOVERNED_ROOT".equals(rule)) {
            return "Move the port implementation under the owning block governed roots (block root or blocks/_shared) or refactor so app layer calls wrappers without implementing generated ports.";
        }
        if ("MULTI_BLOCK_PORT_IMPL_FORBIDDEN".equals(rule)) {
            return "Split generated-port adapters so each class implements one generated block package, or move the adapter under blocks/_shared and add `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above the class declaration.";
        }
        if ("SHARED_PURITY_VIOLATION".equals(rule)) {
            return "Keep `_shared.pure` deterministic: remove mutable static state/synchronized usage, move stateful code to `blocks/**/adapter/**` or `blocks/_shared/state/**`, and use allowlisted immutable constants only.";
        }
        if ("IMPL_PURITY_VIOLATION".equals(rule)) {
            return "Keep impl lane pure: remove mutable static state and synchronized usage from `blocks/**/impl/**`; route cross-call state through generated ports and adapter/state lanes.";
        }
        if ("IMPL_STATE_DEPENDENCY_BYPASS".equals(rule)) {
            return "Remove `blocks._shared.state.*` dependencies from impl lane and access state through generated port adapters.";
        }
        if ("SCOPED_IMPORT_POLICY_BYPASS".equals(rule)) {
            return "Remove forbidden package usage from guarded lane (`impl` or `_shared.pure`) and move IO/network/filesystem/concurrency integration into adapter/state lanes.";
        }
        if ("SHARED_LAYOUT_POLICY_VIOLATION".equals(rule)) {
            return "Move shared Java files under `src/main/java/blocks/_shared/pure/**` or `src/main/java/blocks/_shared/state/**`; root-level `_shared` Java files are not allowed.";
        }
        if ("STATE_STORE_OP_MISUSE".equals(rule)) {
            return "In adapter lane, do not mix update-path logic with state-create calls in the same method; split create vs update semantics and preserve explicit not-found behavior.";
        }
        if ("STATE_STORE_NOOP_UPDATE".equals(rule)) {
            return "In `_shared/state`, update-path methods must not silently return on missing state; raise explicit not-found behavior instead.";
        }
        return "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.";
    }

    private static CheckResult checkFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new CheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail
        );
    }
}
