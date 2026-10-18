package com.example.bonus;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/** Checks discovered names against each other and an independently known result. */
public final class ScannerCorrectness {
    private ScannerCorrectness() {
    }

    public static Set<String> expectedGeneratedComponents() {
        Set<String> expected = new TreeSet<>();
        for (int number = 10; number <= 1000; number += 10) {
            expected.add("com.example.bonus.samples.Sample%04d".formatted(number));
        }
        return expected;
    }

    public static void verify(Set<String> expected, Set<String> reflection, Set<String> asm) {
        if (reflection.equals(asm) && reflection.equals(expected)) {
            return;
        }

        var failures = new ArrayList<String>();
        describeDifferences("Reflection", expected, reflection, failures);
        describeDifferences("ASM", expected, asm, failures);
        throw new IllegalStateException("Component correctness check failed:\n"
                + String.join("\n", failures));
    }

    private static void describeDifferences(String scanner, Set<String> expected,
                                            Set<String> actual, ArrayList<String> failures) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new TreeSet<>(actual);
        unexpected.removeAll(expected);

        if (!missing.isEmpty()) {
            failures.add(scanner + " missing components: " + missing);
        }
        if (!unexpected.isEmpty()) {
            failures.add(scanner + " unexpected components: " + unexpected);
        }
    }
}
