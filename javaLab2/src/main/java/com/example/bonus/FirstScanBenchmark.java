package com.example.bonus;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Times one first scan, with no warm-up; launch a fresh JVM for each measurement. */
public final class FirstScanBenchmark {
    private FirstScanBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2
                || (!args[0].equals("reflection") && !args[0].equals("asm"))) {
            throw new IllegalArgumentException(
                    "Usage: FirstScanBenchmark reflection|asm [classesRoot]");
        }
        String mode = args[0];
        Path classesRoot = Path.of(args.length > 1 ? args[1] : "target/classes");

        long start = System.nanoTime();
        Set<String> components = switch (mode) {
            case "reflection" -> new ReflectionScanner()
                    .scan(classesRoot, "com.example.bonus.samples", false);
            case "asm" -> new AsmScanner().scan(classesRoot, "com.example.bonus.samples");
            default -> throw new IllegalStateException("Invalid scanner mode");
        };
        long elapsed = System.nanoTime() - start;

        // No scanner runs before measurement; correctness and output follow it.
        Set<String> expected = ScannerCorrectness.expectedGeneratedComponents();
        if (!components.equals(expected)) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(components);
            Set<String> unexpected = new TreeSet<>(components);
            unexpected.removeAll(expected);
            throw new IllegalStateException("Incorrect " + mode + " results: missing="
                    + missing + ", unexpected=" + unexpected);
        }
        System.out.printf(Locale.ROOT, "%s first scan: %.3f ms (%d ns); %d components%n",
                mode, elapsed / 1_000_000.0, elapsed, components.size());
        System.out.println("No warm-up; includes scanner/library loading inside the timed interval.");
        System.out.println("Excludes JVM launch time; filesystem caches may already be warm.");
    }
}
