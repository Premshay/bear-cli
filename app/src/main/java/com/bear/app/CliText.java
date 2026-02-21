package com.bear.app;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

final class CliText {
    private CliText() {
    }

    static String squash(String text) {
        if (text == null) {
            return "no details";
        }
        String squashed = normalizeLf(text).replace('\n', ' ').trim();
        return squashed.isEmpty() ? "no details" : squashed;
    }

    static List<String> tailLines(String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        return new ArrayList<>(lines.subList(start, lines.size()));
    }

    static void printLines(PrintStream stream, List<String> lines) {
        for (String line : lines) {
            stream.println(line);
        }
    }

    static String shortTailSummary(String output, int maxLines) {
        List<String> lines = normalizeLf(output).lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
        if (lines.isEmpty()) {
            return null;
        }
        int start = Math.max(0, lines.size() - Math.max(1, maxLines));
        List<String> tail = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > 180) {
                line = line.substring(0, 177) + "...";
            }
            tail.add(line);
        }
        return String.join(" | ", tail);
    }

    static String normalizeLf(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
