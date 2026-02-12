package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.ir.BearIrYamlEmitter;
import com.bear.kernel.target.JvmTarget;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class BearCli {
    private BearCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String command = args.length == 0 ? "help" : args[0];
        return switch (command) {
            case "help", "-h", "--help" -> {
                printUsage(out);
                yield ExitCode.OK;
            }
            case "validate" -> runValidate(args, out, err);
            case "compile" -> runCompile(args, out, err);
            case "check" -> {
                out.println("bear check: placeholder");
                yield ExitCode.OK;
            }
            default -> {
                err.println("usage: UNKNOWN_COMMAND: unknown command: " + command);
                yield ExitCode.USAGE;
            }
        };
    }

    private static int runValidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 2) {
            err.println("usage: INVALID_ARGS: expected: bear validate <file>");
            return ExitCode.USAGE;
        }

        Path file = Path.of(args[1]);
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIrYamlEmitter emitter = new BearIrYamlEmitter();

            BearIr ir = parser.parse(file);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);

            out.print(emitter.toCanonicalYaml(normalized));
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("io: IO_ERROR: " + file);
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        }
    }

    private static int runCompile(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            err.println("usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path>");
            return ExitCode.USAGE;
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);

        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            target.compile(normalized, projectRoot);

            out.println("compiled: OK");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            err.println(e.formatLine());
            return ExitCode.VALIDATION;
        } catch (IOException e) {
            err.println("io: IO_ERROR: " + e.getMessage());
            return ExitCode.IO;
        } catch (Exception e) {
            err.println("internal: INTERNAL_ERROR:");
            return ExitCode.INTERNAL;
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path>");
        out.println("       bear check");
        out.println("       bear --help");
    }

    private static final class ExitCode {
        private static final int OK = 0;
        private static final int VALIDATION = 2;
        private static final int USAGE = 64;
        private static final int IO = 74;
        private static final int INTERNAL = 70;
    }
}
