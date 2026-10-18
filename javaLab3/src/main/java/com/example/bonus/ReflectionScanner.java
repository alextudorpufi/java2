package com.example.bonus;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Discovers components by loading each candidate and querying its annotation. */
public final class ReflectionScanner {
    public Set<String> scan(Path classesRoot, String packageName, boolean initialize)
            throws IOException, ClassNotFoundException {
        Set<String> components = new TreeSet<>();

        // The parent supplies MyComponent so annotation identity is shared.
        try (var loader = new URLClassLoader(
                new java.net.URL[]{classesRoot.toUri().toURL()},
                MyComponent.class.getClassLoader())) {
            for (Path classFile : ClassFiles.find(classesRoot, packageName)) {
                String className = ClassFiles.className(classesRoot, classFile);
                // true behaves like Class.forName(name); false avoids static initialization.
                Class<?> candidate = Class.forName(className, initialize, loader);
                if (candidate.isAnnotationPresent(MyComponent.class)) {
                    components.add(className);
                }
            }
        }
        return components;
    }
}
