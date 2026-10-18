package com.example.bonus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissingDependencyTests {
    @TempDir
    Path output;

    @Test
    void asmDiscoversComponentsWhenReflectionCannotLoadTheirSuperclass() throws Exception {
        var fixtures = MissingDependencyDemo.prepareFixtures(
                Path.of("fixtures/bonus-missing-dependency"), output);
        var reflection = new ReflectionScanner();
        var asm = new AsmScanner();
        String packageName = MissingDependencyDemo.FIXTURE_PACKAGE;

        assertEquals(MissingDependencyDemo.EXPECTED,
                reflection.scan(fixtures.withDependency(), packageName, false));
        assertEquals(MissingDependencyDemo.EXPECTED,
                asm.scan(fixtures.withDependency(), packageName));
        assertFalse(Files.exists(fixtures.withoutDependency()
                .resolve(MissingDependencyDemo.MISSING_CLASS + ".class")));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                MissingDependencyDemo.MISSING_CLASS.replace('/', '.'), false,
                MyComponent.class.getClassLoader()));

        NoClassDefFoundError failure = assertThrows(NoClassDefFoundError.class,
                () -> reflection.scan(fixtures.withoutDependency(), packageName, false));
        assertEquals(MissingDependencyDemo.MISSING_CLASS, failure.getMessage());
        assertEquals(MissingDependencyDemo.EXPECTED,
                asm.scan(fixtures.withoutDependency(), packageName));
    }
}
