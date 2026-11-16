package cc.sighs.cannedmonster.bootstrap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BytecodeIndex {
    private BytecodeIndex() {}

    static Map<String, ClassNode> scan(List<Path> roots) {
        Map<String, ClassNode> index = new HashMap<>();
        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    try (var input = Files.newInputStream(path)) {
                        ClassNode node = new ClassNode();
                        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        index.put(node.name, node);
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot index fusion bytecode in " + root, exception);
            }
        }
        return Map.copyOf(index);
    }
}
