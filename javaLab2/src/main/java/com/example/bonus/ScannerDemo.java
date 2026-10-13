package com.example.bonus;

import java.nio.file.Path;
import java.util.Set;

/** Standalone entry point; does not start the Spring application. */
public final class ScannerDemo {
    private static final String SAMPLE_PACKAGE = "com.example.bonus.samples";

    private ScannerDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: ScannerDemo [compare|reflection|reflection-no-init|asm] [classesRoot]");
        }
        String mode = args.length > 0 ? args[0] : "compare";
        Path classesRoot = Path.of(args.length > 1 ? args[1] : "target/classes");

        switch (mode) {
            case "reflection" -> printResults("Reflection (initialization enabled)",
                    new ReflectionScanner().scan(classesRoot, SAMPLE_PACKAGE, true));
            case "reflection-no-init" -> printResults("Reflection (initialization disabled)",
                    new ReflectionScanner().scan(classesRoot, SAMPLE_PACKAGE, false));
            case "asm" -> printResults("ASM",
                    new AsmScanner().scan(classesRoot, SAMPLE_PACKAGE));
            case "compare" -> {
                Set<String> reflection = new ReflectionScanner().scan(classesRoot, SAMPLE_PACKAGE, false);
                Set<String> asm = new AsmScanner().scan(classesRoot, SAMPLE_PACKAGE);
                printResults("Reflection (initialization disabled)", reflection);
                printResults("ASM", asm);
                ScannerCorrectness.verify(
                        ScannerCorrectness.expectedGeneratedComponents(), reflection, asm);
                System.out.println("Correctness check passed: both scanners found exactly the 100 expected components.");
            }
            default -> throw new IllegalArgumentException("Unknown scanner mode: " + mode);
        }
    }

    private static void printResults(String label, Set<String> components) {
        System.out.printf("%s: %d components%n", label, components.size());
        components.stream().limit(5).forEach(name -> System.out.println("  " + name));
    }
}
