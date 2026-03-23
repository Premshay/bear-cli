package com.bear.kernel.template;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A named, deterministic recipe that emits a complete governed scaffold
 * (IR file + paths for generated artifacts and impl stub) for a specific
 * capability shape.
 */
public interface CapabilityTemplate {

    /** Stable, kebab-case identifier (e.g. "read-store"). */
    String id();

    /** One-line human description shown by --list. */
    String description();

    /**
     * Writes the IR file to disk and returns the TemplatePack describing
     * all paths that will be populated (IR path + paths compile() will produce).
     * Does NOT call compile(); the caller is responsible for that.
     */
    TemplatePack emit(TemplateParams params, Path projectRoot) throws IOException;
}
