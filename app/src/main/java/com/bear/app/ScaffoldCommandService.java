package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.Target;
import com.bear.kernel.target.TargetRegistry;
import com.bear.kernel.template.CapabilityTemplate;
import com.bear.kernel.template.CapabilityTemplateRegistry;
import com.bear.kernel.template.TemplateParams;
import com.bear.kernel.template.TemplatePack;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the {@code bear scaffold} subcommands:
 * <ul>
 *   <li>{@code --template <id> --block <name> [--project <path>]} — emit scaffold</li>
 *   <li>{@code --list} — print sorted template ids</li>
 * </ul>
 */
final class ScaffoldCommandService {

    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final TargetRegistry TARGET_REGISTRY = TargetRegistry.defaultRegistry();
    private static final String BLOCKS_YAML = "bear.blocks.yaml";

    private ScaffoldCommandService() {
    }

    /**
     * Main entry point. Returns the exit code.
     *
     * @param args the full args array (args[0] is "scaffold")
     * @param out  stdout
     * @param err  stderr
     * @return exit code
     */
    static int execute(String[] args, PrintStream out, PrintStream err) {
        ScaffoldResult result = executeInternal(args);
        return emitResult(result, out, err);
    }

    // -------------------------------------------------------------------------
    // Internal execution — returns a ScaffoldResult
    // -------------------------------------------------------------------------

    static ScaffoldResult executeInternal(String[] args) {
        // Parse args (skip args[0] which is "scaffold")
        boolean list = false;
        String templateId = null;
        String blockName = null;
        String projectArg = null;

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--list" -> list = true;
                case "--template" -> {
                    if (i + 1 >= args.length) {
                        return usageFailure("expected value after --template", "scaffold.args");
                    }
                    templateId = args[++i];
                }
                case "--block" -> {
                    if (i + 1 >= args.length) {
                        return usageFailure("expected value after --block", "scaffold.args");
                    }
                    blockName = args[++i];
                }
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        return usageFailure("expected value after --project", "scaffold.args");
                    }
                    projectArg = args[++i];
                }
                default -> {
                    return usageFailure("unexpected argument: " + token, "scaffold.args");
                }
            }
        }

        // --list flow
        if (list) {
            List<String> lines = new ArrayList<>();
            for (CapabilityTemplate t : CapabilityTemplateRegistry.list()) {
                lines.add(t.id());
            }
            return new ScaffoldResult(CliCodes.EXIT_OK, lines, List.of(), null, null, null);
        }

        // Emit flow — step 1: validate required flags
        if (templateId == null) {
            return usageFailure("missing required flag: --template", "scaffold.args");
        }
        if (blockName == null) {
            return usageFailure("missing required flag: --block", "scaffold.args");
        }

        // Step 1b: validate blockName format (prevent path traversal)
        if (!blockName.matches("^[a-z][a-z0-9-]*$")) {
            return usageFailure("invalid block name: '" + blockName + "' (must match [a-z][a-z0-9-]*)", "scaffold.args");
        }

        // Step 2: resolve projectRoot
        Path projectRoot = projectArg != null
                ? Path.of(projectArg).toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath();

        // Step 3: find template
        Optional<CapabilityTemplate> templateOpt = CapabilityTemplateRegistry.find(templateId);
        if (templateOpt.isEmpty()) {
            return scaffoldFailure(
                    CliCodes.EXIT_USAGE,
                    List.of("usage: " + CliCodes.USAGE_UNKNOWN_TEMPLATE + ": unknown template: " + templateId),
                    CliCodes.USAGE_UNKNOWN_TEMPLATE,
                    "scaffold.template",
                    "Run `bear scaffold --list` to see available templates."
            );
        }
        CapabilityTemplate template = templateOpt.get();

        // Step 4: check bear.blocks.yaml for existing block name
        Path indexPath = projectRoot.resolve(BLOCKS_YAML);
        if (Files.exists(indexPath)) {
            try {
                BlockIndexParser parser = new BlockIndexParser();
                BlockIndex index = parser.parse(projectRoot, indexPath);
                for (BlockIndexEntry entry : index.blocks()) {
                    if (entry.name().equals(blockName)) {
                        return scaffoldFailure(
                                CliCodes.EXIT_USAGE,
                                List.of("usage: " + CliCodes.BLOCK_ALREADY_EXISTS + ": block already exists: " + blockName),
                                CliCodes.BLOCK_ALREADY_EXISTS,
                                BLOCKS_YAML,
                                "Choose a different block name or remove the existing entry from bear.blocks.yaml."
                        );
                    }
                }
            } catch (BlockIndexValidationException e) {
                return scaffoldFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of("validation: " + CliCodes.IR_VALIDATION + ": malformed bear.blocks.yaml: " + e.getMessage()),
                        CliCodes.IR_VALIDATION,
                        BLOCKS_YAML,
                        "Fix bear.blocks.yaml and rerun `bear scaffold`."
                );
            } catch (IOException e) {
                return scaffoldFailure(
                        CliCodes.EXIT_IO,
                        List.of("io: " + CliCodes.IO_ERROR + ": " + e.getMessage()),
                        CliCodes.IO_ERROR,
                        BLOCKS_YAML,
                        "Ensure bear.blocks.yaml is readable and rerun `bear scaffold`."
                );
            }
        }

        // Step 5: resolve target
        Target target;
        try {
            target = TARGET_REGISTRY.resolve(projectRoot);
        } catch (com.bear.kernel.target.TargetResolutionException e) {
            return scaffoldFailure(
                    e.exitCode(),
                    List.of(e.code() + ": " + e.path()),
                    e.code(),
                    e.path(),
                    e.remediation()
            );
        }

        // Step 6: emit IR (check for pre-existing IR file first)
        Path expectedIrPath = projectRoot.resolve("spec").resolve(blockName + ".ir.yaml");
        if (Files.exists(expectedIrPath)) {
            return scaffoldFailure(
                    CliCodes.EXIT_USAGE,
                    List.of("usage: " + CliCodes.BLOCK_ALREADY_EXISTS + ": IR file already exists: " + expectedIrPath),
                    CliCodes.BLOCK_ALREADY_EXISTS,
                    projectRoot.relativize(expectedIrPath).toString().replace('\\', '/'),
                    "Remove the existing IR file or choose a different block name."
            );
        }

        TemplateParams params = new TemplateParams(blockName, target.targetId());
        TemplatePack pack;
        try {
            pack = template.emit(params, projectRoot);
        } catch (IOException e) {
            Path irPath = projectRoot.resolve("spec").resolve(blockName + ".ir.yaml");
            return scaffoldFailure(
                    CliCodes.EXIT_IO,
                    List.of("io: " + CliCodes.IO_ERROR + ": " + e.getMessage()),
                    CliCodes.IO_ERROR,
                    projectRoot.relativize(irPath).toString().replace('\\', '/'),
                    "Ensure the project directory is writable and rerun `bear scaffold`."
            );
        }

        Path irPath = pack.irPath();

        // Step 6b: check if IR file was actually written (guard against overwrite)
        if (!Files.exists(irPath)) {
            return scaffoldFailure(
                    CliCodes.EXIT_IO,
                    List.of("io: " + CliCodes.IO_ERROR + ": emitted IR file not found: " + irPath),
                    CliCodes.IO_ERROR,
                    projectRoot.relativize(irPath).toString().replace('\\', '/'),
                    "Ensure the project directory is writable and rerun `bear scaffold`."
            );
        }

        // Step 7: parse and validate the emitted IR
        BearIr ir;
        try {
            ir = IR_PIPELINE.parseValidateNormalize(irPath);
        } catch (BearIrValidationException e) {
            return scaffoldFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of(e.formatLine()),
                    CliCodes.IR_VALIDATION,
                    e.path(),
                    "Fix the IR issue at the reported path."
            );
        } catch (IOException e) {
            return scaffoldFailure(
                    CliCodes.EXIT_IO,
                    List.of("io: " + CliCodes.IO_ERROR + ": " + e.getMessage()),
                    CliCodes.IO_ERROR,
                    projectRoot.relativize(irPath).toString().replace('\\', '/'),
                    "Ensure the emitted IR file is readable."
            );
        }

        // Step 8: compile
        try {
            target.compile(ir, projectRoot, blockName);
        } catch (IOException e) {
            return scaffoldFailure(
                    CliCodes.EXIT_IO,
                    List.of("io: " + CliCodes.IO_ERROR + ": " + e.getMessage()),
                    CliCodes.IO_ERROR,
                    "project.root",
                    "Ensure the project directory is writable and rerun `bear scaffold`."
            );
        }

        // Step 9: update bear.blocks.yaml
        String irRelPath = projectRoot.relativize(irPath).toString().replace('\\', '/');
        try {
            BlocksYamlUpdater.appendEntry(projectRoot, blockName, irRelPath);
        } catch (BlocksYamlUpdater.BlockAlreadyExistsException e) {
            // Race condition — block was added between our check and now
            return scaffoldFailure(
                    CliCodes.EXIT_USAGE,
                    List.of("usage: " + CliCodes.BLOCK_ALREADY_EXISTS + ": block already exists: " + blockName),
                    CliCodes.BLOCK_ALREADY_EXISTS,
                    BLOCKS_YAML,
                    "Choose a different block name or remove the existing entry from bear.blocks.yaml."
            );
        } catch (BlockIndexValidationException e) {
            return scaffoldFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of("validation: " + CliCodes.IR_VALIDATION + ": malformed bear.blocks.yaml: " + e.getMessage()),
                    CliCodes.IR_VALIDATION,
                    BLOCKS_YAML,
                    "Fix bear.blocks.yaml and rerun `bear scaffold`."
            );
        } catch (IOException e) {
            return scaffoldFailure(
                    CliCodes.EXIT_IO,
                    List.of("io: " + CliCodes.IO_ERROR + ": " + e.getMessage()),
                    CliCodes.IO_ERROR,
                    BLOCKS_YAML,
                    "Ensure bear.blocks.yaml is writable and rerun `bear scaffold`."
            );
        }

        // Step 10: success
        return new ScaffoldResult(CliCodes.EXIT_OK, List.of("scaffold: OK"), List.of(), null, null, null);
    }

    // -------------------------------------------------------------------------
    // Result emission
    // -------------------------------------------------------------------------

    static int emitResult(ScaffoldResult result, PrintStream out, PrintStream err) {
        for (String line : result.stdoutLines()) {
            out.println(line);
        }
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return CliCodes.EXIT_OK;
        }
        for (String line : result.stderrLines()) {
            err.println(line);
        }
        return BearCli.fail(err, result.exitCode(), result.failureCode(), result.failurePath(), result.failureRemediation());
    }

    // -------------------------------------------------------------------------
    // Failure helpers
    // -------------------------------------------------------------------------

    private static ScaffoldResult usageFailure(String message, String path) {
        return scaffoldFailure(
                CliCodes.EXIT_USAGE,
                List.of("usage: " + CliCodes.USAGE_INVALID_ARGS + ": " + message),
                CliCodes.USAGE_INVALID_ARGS,
                path,
                "Run `bear scaffold --template <id> --block <name> [--project <path>]` or `bear scaffold --list`."
        );
    }

    private static ScaffoldResult scaffoldFailure(
            int exitCode,
            List<String> stderrLines,
            String failureCode,
            String failurePath,
            String failureRemediation
    ) {
        return new ScaffoldResult(exitCode, List.of(), List.copyOf(stderrLines), failureCode, failurePath, failureRemediation);
    }
}
