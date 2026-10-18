package com.example.bonus;

import java.nio.file.Path;
import java.util.Set;

/** Run each mode in a fresh JVM to observe whether scanning executes a static block. */
public final class StaticInitializationDemo {
    private StaticInitializationDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: StaticInitializationDemo reflection|reflection-no-init|asm");
        }
        String mode = args[0];
        System.clearProperty("bonus.static.initialized");
        Path root = Path.of("target/classes");
        String packageName = "com.example.bonus.initialization";
        Set<String> found = switch (mode) {
            case "reflection" -> new ReflectionScanner().scan(root, packageName, true);
            case "reflection-no-init" -> new ReflectionScanner().scan(root, packageName, false);
            case "asm" -> new AsmScanner().scan(root, packageName);
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
        if (!found.equals(Set.of(packageName + ".StaticComponent"))) {
            throw new IllegalStateException("Unexpected component results: " + found);
        }
        boolean initialized = Boolean.getBoolean("bonus.static.initialized");
        if (initialized != mode.equals("reflection")) {
            throw new IllegalStateException("Unexpected static initialization behavior for " + mode);
        }
        System.out.println("Static initialization executed: " + initialized);
        System.out.println(mode + ": found the expected component.");
    }
}
