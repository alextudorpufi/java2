package com.example.bonus;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Discovers components from class-file metadata without loading candidates. */
public final class AsmScanner {
    // A descriptor is the annotation's binary name wrapped in L...;.
    private static final String COMPONENT_DESCRIPTOR = "Lcom/example/bonus/MyComponent;";

    public Set<String> scan(Path classesRoot, String packageName) throws IOException {
        Set<String> components = new TreeSet<>();

        for (Path classFile : ClassFiles.find(classesRoot, packageName)) {
            try (var input = Files.newInputStream(classFile)) {
                ClassReader reader = new ClassReader(input);
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (visible && COMPONENT_DESCRIPTOR.equals(descriptor)) {
                            components.add(reader.getClassName().replace('/', '.'));
                        }
                        // No annotation values need to be inspected.
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return components;
    }
}
