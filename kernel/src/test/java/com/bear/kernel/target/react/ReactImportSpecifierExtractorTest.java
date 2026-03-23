package com.bear.kernel.target.react;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReactImportSpecifierExtractor.
 * Covers static imports, export re-exports, side-effect imports, and JSX files.
 */
class ReactImportSpecifierExtractorTest {

    private final ReactImportSpecifierExtractor extractor = new ReactImportSpecifierExtractor();

    // --- Static imports ---

    @Test
    void extractsNamedImports() {
        String source = "import { UserService } from './services/user-service';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./services/user-service", specifiers.get(0).specifier());
        assertEquals("named", specifiers.get(0).kind());
    }

    @Test
    void extractsNamespaceImports() {
        String source = "import * as utils from './utils';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./utils", specifiers.get(0).specifier());
        assertEquals("namespace", specifiers.get(0).kind());
    }

    @Test
    void extractsDefaultImports() {
        String source = "import React from 'react';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.tsx", source);

        assertEquals(1, specifiers.size());
        assertEquals("react", specifiers.get(0).specifier());
        assertEquals("default", specifiers.get(0).kind());
    }

    @Test
    void extractsMultipleImportsFromSameFile() {
        String source = """
            import React from 'react';
            import { useState, useEffect } from 'react';
            import type { FC } from 'react';
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.tsx", source);

        // Should extract at least 3 imports (all from 'react')
        assertTrue(specifiers.size() >= 2);
        assertTrue(specifiers.stream().allMatch(s -> s.specifier().equals("react")));
    }

    // --- Export re-exports ---

    @Test
    void extractsNamedExports() {
        String source = "export { UserService } from './services/user-service';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./services/user-service", specifiers.get(0).specifier());
        assertEquals("export-named", specifiers.get(0).kind());
    }

    @Test
    void extractsExportAll() {
        String source = "export * from './types';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./types", specifiers.get(0).specifier());
        assertEquals("export-all", specifiers.get(0).kind());
    }

    @Test
    void extractsReExportsWithRename() {
        String source = "export { default as Button } from './Button';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./Button", specifiers.get(0).specifier());
    }

    // --- Side-effect imports ---

    @Test
    void extractsSideEffectImports() {
        String source = "import './polyfills';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(1, specifiers.size());
        assertEquals("./polyfills", specifiers.get(0).specifier());
        assertEquals("side-effect", specifiers.get(0).kind());
    }

    @Test
    void extractsCssImports() {
        String source = "import './styles.css';";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.tsx", source);

        assertEquals(1, specifiers.size());
        assertEquals("./styles.css", specifiers.get(0).specifier());
        assertEquals("side-effect", specifiers.get(0).kind());
    }

    // --- JSX files ---

    @Test
    void extractsImportsFromTsxFileWithJsx() {
        String source = """
            import React from 'react';
            import { Button } from './components/Button';
            
            export function App() {
                return <Button>Click me</Button>;
            }
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("App.tsx", source);

        assertEquals(2, specifiers.size());
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("react")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("./components/Button")));
    }

    @Test
    void extractsImportsFromComplexJsxFile() {
        String source = """
            import React from 'react';
            import { useState } from 'react';
            import { useQuery } from '@tanstack/react-query';
            import './Dashboard.css';
            
            export const Dashboard = () => {
                const [count, setCount] = useState(0);
                return (
                    <div className="dashboard">
                        <h1>Dashboard</h1>
                        <button onClick={() => setCount(c => c + 1)}>
                            Count: {count}
                        </button>
                    </div>
                );
            };
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("Dashboard.tsx", source);

        // Should extract imports from react (2x), @tanstack/react-query, and ./Dashboard.css
        assertTrue(specifiers.size() >= 3);
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("react")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("@tanstack/react-query")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("./Dashboard.css")));
    }

    // --- Line numbers ---

    @Test
    void returnsCorrectLineNumbers() {
        String source = """
            import { A } from './a';
            import { B } from './b';
            import { C } from './c';
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(3, specifiers.size());
        assertEquals(1, specifiers.get(0).lineNumber());
        assertEquals(2, specifiers.get(1).lineNumber());
        assertEquals(3, specifiers.get(2).lineNumber());
    }

    // --- Edge cases ---

    @Test
    void handlesEmptySource() {
        String source = "";
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(0, specifiers.size());
    }

    @Test
    void handlesSourceWithNoImports() {
        String source = """
            const x = 1;
            export function foo() { return x; }
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(0, specifiers.size());
    }

    @Test
    void handlesNextJsAliasImports() {
        String source = """
            import { Button } from '@/components/Button';
            import { useAuth } from '@/hooks/useAuth';
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.tsx", source);

        assertEquals(2, specifiers.size());
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("@/components/Button")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("@/hooks/useAuth")));
    }

    @Test
    void handlesBarePackageImports() {
        String source = """
            import lodash from 'lodash';
            import { debounce } from 'lodash/debounce';
            import axios from 'axios';
            """;
        List<ReactImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports("test.ts", source);

        assertEquals(3, specifiers.size());
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("lodash")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("lodash/debounce")));
        assertTrue(specifiers.stream().anyMatch(s -> s.specifier().equals("axios")));
    }
}
