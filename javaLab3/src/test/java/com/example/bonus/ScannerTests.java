package com.example.bonus;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerTests {
    @Test
    void bothScannersFindExactlyEveryTenthSample() throws Exception {
        Set<String> expected = new TreeSet<>();
        for (int number = 10; number <= 1000; number += 10) {
            expected.add("com.example.bonus.samples.Sample%04d".formatted(number));
        }

        Path classesRoot = Path.of("target/classes");
        String packageName = "com.example.bonus.samples";
        Set<String> reflection = new ReflectionScanner().scan(classesRoot, packageName, false);
        Set<String> asm = new AsmScanner().scan(classesRoot, packageName);
        ScannerCorrectness.verify(expected, reflection, asm);
        assertEquals(expected, ScannerCorrectness.expectedGeneratedComponents());
        assertEquals(expected, reflection);
        assertEquals(expected, new ReflectionScanner().scan(classesRoot, packageName, true));
        assertEquals(expected, asm);
    }

    @Test
    void correctnessCheckRejectsDifferentNamesWithTheSameCount() {
        Set<String> expected = Set.of("samples.ComponentA", "samples.ComponentB");
        Set<String> asm = Set.of("samples.ComponentA", "samples.ComponentC");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ScannerCorrectness.verify(expected, expected, asm));

        assertTrue(failure.getMessage().contains("ASM missing components: [samples.ComponentB]"));
        assertTrue(failure.getMessage().contains("ASM unexpected components: [samples.ComponentC]"));
    }

    @Test
    void correctnessCheckRejectsMatchingButIncorrectResults() {
        Set<String> expected = Set.of("samples.ComponentA");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ScannerCorrectness.verify(expected, Set.of(), Set.of()));

        assertTrue(failure.getMessage().contains("Reflection missing components: [samples.ComponentA]"));
        assertTrue(failure.getMessage().contains("ASM missing components: [samples.ComponentA]"));
    }
}
