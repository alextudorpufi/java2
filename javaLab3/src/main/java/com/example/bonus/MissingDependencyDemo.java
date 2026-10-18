package com.example.bonus;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Builds isolated fixtures and compares scanning with and without their superclass. */
public final class MissingDependencyDemo {
    static final String FIXTURE_PACKAGE = "com.example.bonus.missing";
    static final String MISSING_CLASS = "com/example/bonus/optional/OptionalBase";
    static final Set<String> EXPECTED = Set.of(
            FIXTURE_PACKAGE + ".BrokenComponent", FIXTURE_PACKAGE + ".HealthyComponent");

    private MissingDependencyDemo() {
    }

    public static void main(String[] args) throws Exception {
        // If fixtures were accidentally added to the application classpath, the
        // scanner's parent loader could supply the supposedly missing superclass.
        try {
            Class.forName(MISSING_CLASS.replace('/', '.'), false, MyComponent.class.getClassLoader());
            throw new IllegalStateException("OptionalBase must be absent from the application classpath.");
        } catch (ClassNotFoundException expected) {
            // The application's loader cannot provide the optional dependency.
        }

        Files.createDirectories(Path.of("target"));
        Path output = Files.createTempDirectory(Path.of("target"), "missing-dependency-");
        Fixtures fixtures = prepareFixtures(Path.of("fixtures/bonus-missing-dependency"), output);
        var reflection = new ReflectionScanner();
        var asm = new AsmScanner();

        ScannerCorrectness.verify(EXPECTED,
                reflection.scan(fixtures.withDependency(), FIXTURE_PACKAGE, false),
                asm.scan(fixtures.withDependency(), FIXTURE_PACKAGE));
        System.out.println("Dependency available: both scanners found the 2 expected components.");

        try {
            reflection.scan(fixtures.withoutDependency(), FIXTURE_PACKAGE, false);
            throw new IllegalStateException("Reflection unexpectedly succeeded without OptionalBase.");
        } catch (NoClassDefFoundError failure) {
            if (!MISSING_CLASS.equals(failure.getMessage())) {
                throw failure;
            }
            System.out.println("Dependency missing: Reflection failed with NoClassDefFoundError: "
                    + failure.getMessage());
        }

        Set<String> asmResult = asm.scan(fixtures.withoutDependency(), FIXTURE_PACKAGE);
        if (!EXPECTED.equals(asmResult)) {
            throw new IllegalStateException("Unexpected ASM results: " + asmResult);
        }
        System.out.println("Dependency missing: ASM found both components without loading them:");
        asmResult.forEach(name -> System.out.println("  " + name));
        System.out.println("Experiment passed. Compiled fixtures: " + output);
    }

    static Fixtures prepareFixtures(Path sourcesRoot, Path output) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run this experiment with a JDK, not a JRE.");
        }
        Path withDependency = output.resolve("with-dependency");
        Path withoutDependency = output.resolve("without-dependency");
        Files.createDirectories(withDependency);
        Files.createDirectories(withoutDependency);

        // Compile outside Maven's main output so OptionalBase is never added to
        // the application's runtime classpath. Only MyComponent is needed from it.
        Path annotationLocation = Path.of(MyComponent.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        int result = compiler.run(null, null, null,
                "--release", "21", "-proc:none", "-classpath", annotationLocation.toString(),
                "-d", withDependency.toString(),
                sourcesRoot.resolve(MISSING_CLASS + ".java").toString(),
                sourcesRoot.resolve("com/example/bonus/missing/BrokenComponent.java").toString(),
                sourcesRoot.resolve("com/example/bonus/missing/HealthyComponent.java").toString());
        if (result != 0) {
            throw new IOException("Fixture compilation failed with exit code " + result);
        }

        // Copy only candidates. The optional superclass stays in the other root.
        Path candidates = withDependency.resolve(FIXTURE_PACKAGE.replace('.', '/'));
        try (var files = Files.walk(candidates)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = withoutDependency.resolve(withDependency.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination);
            }
        }
        return new Fixtures(withDependency, withoutDependency);
    }

    record Fixtures(Path withDependency, Path withoutDependency) {
    }
}
