package com.example.bonus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared file discovery so both scanners inspect the same input package. */
final class ClassFiles {
    private ClassFiles() {
    }

    static List<Path> find(Path classesRoot, String packageName) throws IOException {
        Path packageDirectory = classesRoot.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(packageDirectory)) {
            throw new IOException("Compiled package directory not found: " + packageDirectory);
        }
        try (var paths = Files.walk(packageDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
        }
    }

    static String className(Path classesRoot, Path classFile) {
        String relativePath = classesRoot.relativize(classFile).toString();
        return relativePath.substring(0, relativePath.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }
}
