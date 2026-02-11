package com.bear.app;

public final class BearCli {
    private BearCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        switch (command) {
            case "validate" -> System.out.println("bear validate: placeholder");
            case "compile" -> System.out.println("bear compile: placeholder");
            case "check" -> System.out.println("bear check: placeholder");
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: bear <validate|compile|check>");
    }
}
