package com.bear.kernel.target.react;

import com.bear.kernel.target.node.NodeImportSpecifierExtractor;

import java.util.List;

/**
 * Extracts import specifiers from React TypeScript files (.ts and .tsx).
 * Thin wrapper reusing NodeImportSpecifierExtractor — the existing regex-based
 * extractor already handles JSX files correctly since JSX syntax does not
 * appear in import statements.
 */
public class ReactImportSpecifierExtractor {

    private final NodeImportSpecifierExtractor delegate = new NodeImportSpecifierExtractor();

    /**
     * Extracts all import and export specifiers from TypeScript/TSX source.
     * Handles:
     * - import ... from '...'
     * - export ... from '...'
     * - import '...' (side-effect)
     * - import(...) dynamic (detected as advisory, not enforced)
     *
     * @param sourcePath the source file path (for location tracking)
     * @param content the file content
     * @return list of import specifiers with location information
     */
    public List<ImportSpecifier> extractImports(String sourcePath, String content) {
        return delegate.extractImports(sourcePath, content).stream()
            .map(spec -> new ImportSpecifier(
                spec.source(),
                spec.specifier(),
                spec.lineNumber(),
                spec.columnNumber(),
                spec.kind()
            ))
            .toList();
    }

    /**
     * Record representing an import/export specifier with location information.
     */
    public record ImportSpecifier(
        String source,
        String specifier,
        int lineNumber,
        int columnNumber,
        String kind
    ) {}
}
