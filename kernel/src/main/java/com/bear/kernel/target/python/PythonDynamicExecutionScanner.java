package com.bear.kernel.target.python;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scans governed Python source files for dynamic execution escape hatches.
 * 
 * Detects direct calls to:
 * - eval(...)
 * - exec(...)
 * - compile(...)
 * - runpy.run_module(...)
 * - runpy.run_path(...)
 * 
 * Excludes calls inside `if TYPE_CHECKING:` blocks and test files.
 * Findings map to CODE=REFLECTION_DISPATCH_FORBIDDEN, exit code 6.
 */
public class PythonDynamicExecutionScanner {

    private static final String PYTHON_SCRIPT = """
import ast
import sys
import json

ESCAPE_HATCHES = {'eval', 'exec', 'compile'}
RUNPY_METHODS = {'run_module', 'run_path'}

def collect_type_checking_lines(tree):
    \"\"\"Collect line ranges inside `if TYPE_CHECKING:` blocks.\"\"\"
    type_checking_lines = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.If):
            # Check if condition is TYPE_CHECKING
            test = node.test
            is_type_checking = False
            if isinstance(test, ast.Name) and test.id == 'TYPE_CHECKING':
                is_type_checking = True
            elif isinstance(test, ast.Attribute) and test.attr == 'TYPE_CHECKING':
                is_type_checking = True
            
            if is_type_checking:
                # Collect lines in the if-body only (not orelse which is runtime code)
                for body_stmt in node.body:
                    for body_node in ast.walk(body_stmt):
                        if hasattr(body_node, 'lineno'):
                            type_checking_lines.add(body_node.lineno)
    return type_checking_lines

def scan(source):
    findings = []
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return []
    
    type_checking_lines = collect_type_checking_lines(tree)
    
    for node in ast.walk(tree):
        if not hasattr(node, 'lineno'):
            continue
        if node.lineno in type_checking_lines:
            continue
        
        if isinstance(node, ast.Call):
            # Detect direct calls to eval, exec, compile
            if isinstance(node.func, ast.Name) and node.func.id in ESCAPE_HATCHES:
                findings.append({'surface': node.func.id, 'line': node.lineno})
            # Detect runpy.run_module(...) and runpy.run_path(...)
            elif (isinstance(node.func, ast.Attribute) and
                  node.func.attr in RUNPY_METHODS and
                  isinstance(node.func.value, ast.Name) and
                  node.func.value.id == 'runpy'):
                findings.append({'surface': 'runpy.' + node.func.attr, 'line': node.lineno})
    
    return findings

if __name__ == '__main__':
    source = sys.stdin.read()
    findings = scan(source)
    print(json.dumps(findings))
""";

    /**
     * Scans governed Python source files for dynamic execution escape hatches.
     * 
     * @param projectRoot The project root directory
     * @param wiringManifests List of wiring manifests for all blocks
     * @return List of undeclared reach findings, sorted by path then surface
     * @throws IOException if file reading or Python AST execution fails
     */
    public static List<UndeclaredReachFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        Set<Path> governedRoots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, wiringManifests);
        List<Path> governedFiles = PythonImportContainmentScanner.collectGovernedFiles(governedRoots);

        List<UndeclaredReachFinding> findings = new ArrayList<>();

        for (Path file : governedFiles) {
            String content = Files.readString(file);
            List<RawFinding> rawFindings = scanFile(content);
            
            String relativePath = projectRoot.relativize(file).toString();
            for (RawFinding raw : rawFindings) {
                findings.add(new UndeclaredReachFinding(relativePath, raw.surface()));
            }
        }

        // Sort by path then surface
        findings.sort(Comparator.comparing(UndeclaredReachFinding::path)
            .thenComparing(UndeclaredReachFinding::surface));

        return findings;
    }

    /**
     * Scans a single Python file for dynamic execution escape hatches.
     */
    private static List<RawFinding> scanFile(String content) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-c", PYTHON_SCRIPT);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Write source to stdin
            process.getOutputStream().write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().close();

            // Read JSON output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Fail closed: non-zero exit means the scanner couldn't run properly.
                // SyntaxError in the *scanned* source is already handled inside the Python
                // script (returns []), so a non-zero exit here indicates an environment or
                // script-level failure that must not silently suppress findings.
                throw new IOException("Python AST dynamic-execution scanner exited with code " + exitCode
                    + "; output: " + output);
            }

            return parseFindings(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python AST scan interrupted", e);
        }
    }

    /**
     * Parses the JSON output from the Python script.
     */
    private static List<RawFinding> parseFindings(String json) throws IOException {
        List<RawFinding> findings = new ArrayList<>();
        
        json = json.trim();
        if (json.equals("[]") || json.isEmpty()) {
            return findings;
        }

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IOException("Invalid JSON output from Python scanner: " + json);
        }

        // Remove outer brackets
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return findings;
        }

        // Split by objects
        int depth = 0;
        int start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1) {
                    start = i;
                }
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String obj = json.substring(start, i + 1);
                    findings.add(parseFindingObject(obj));
                }
            }
        }

        return findings;
    }

    /**
     * Parses a single JSON object into a RawFinding.
     */
    private static RawFinding parseFindingObject(String obj) throws IOException {
        String surface = extractJsonString(obj, "surface");
        int line = extractJsonInt(obj, "line");
        return new RawFinding(surface, line);
    }

    private static String extractJsonString(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        if (start >= json.length() || json.charAt(start) != '"') {
            throw new IOException("Expected string value for key: " + key);
        }
        
        start++;
        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Malformed JSON string for key: " + key);
        }
        return json.substring(start, end);
    }

    private static int extractJsonInt(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        if (end == -1) {
            throw new IOException("Malformed JSON int for key: " + key);
        }
        
        String value = json.substring(start, end).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer for key: " + key + ", value: " + value, e);
        }
    }

    /**
     * Internal record for raw findings from Python script.
     */
    private record RawFinding(String surface, int line) {}
}
