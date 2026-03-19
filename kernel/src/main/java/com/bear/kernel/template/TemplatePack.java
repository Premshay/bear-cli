package com.bear.kernel.template;

import java.nio.file.Path;
import java.util.List;

/**
 * The set of paths produced (or to be produced) by a single CapabilityTemplate invocation.
 *
 * <p>{@code emit()} returns a TemplatePack with {@code generatedPaths} empty.
 * After {@code target.compile()}, generated artifacts are written by the compile step;
 * TemplatePack is a value object for test assertions and future tooling.
 */
public record TemplatePack(
        Path irPath,               // spec/<block>.ir.yaml
        Path implStubPath,         // src/main/java/blocks/<block-tokens-as-path>/impl/<Block>Impl.java
        List<Path> generatedPaths  // populated after compile(); empty from emit()
) {}
