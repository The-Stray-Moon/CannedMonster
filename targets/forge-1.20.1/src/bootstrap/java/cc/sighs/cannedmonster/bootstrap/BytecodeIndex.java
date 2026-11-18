package cc.sighs.cannedmonster.bootstrap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

final class BytecodeIndex {
    private record Candidate(Path path, int version) {}

    private BytecodeIndex() {}

    static Map<String, ClassNode> scan(List<Path> roots) {
        Map<String, ClassNode> index = new HashMap<>();
        for (Path root : roots) {
            try {
                if (Files.isRegularFile(root)) {
                    try (var filesystem = FileSystems.newFileSystem(root)) {
                        scanRoot(filesystem.getPath("/"), index);
                    }
                } else if (Files.isDirectory(root)) {
                    scanRoot(root, index);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot index fusion bytecode in " + root, exception);
            }
        }
        return Map.copyOf(index);
    }

    private static void scanRoot(Path root, Map<String, ClassNode> index) throws IOException {
        boolean multiRelease = false;
        Path manifest = root.resolve("META-INF/MANIFEST.MF");
        if (Files.isRegularFile(manifest)) {
            try (var input = Files.newInputStream(manifest)) {
                multiRelease = Boolean.parseBoolean(new Manifest(input).getMainAttributes().getValue("Multi-Release"));
            }
        }
        Map<String, Candidate> candidates = new HashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                int version = 0;
                if (name.startsWith("META-INF/versions/")) {
                    if (!multiRelease) continue;
                    String entry = name.substring("META-INF/versions/".length());
                    int separator = entry.indexOf('/');
                    if (separator < 0) continue;
                    try { version = Integer.parseInt(entry.substring(0, separator)); }
                    catch (NumberFormatException ignored) { continue; }
                    if (version < 9 || version > Runtime.version().feature()) continue;
                    name = entry.substring(separator + 1);
                }
                if (name.startsWith("META-INF/") || name.equals("module-info.class")) continue;
                Candidate previous = candidates.get(name);
                if (previous == null || version > previous.version) candidates.put(name, new Candidate(path, version));
            }
        }
        for (Candidate candidate : candidates.values()) {
            try (var input = Files.newInputStream(candidate.path)) {
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if ((node.access & org.objectweb.asm.Opcodes.ACC_MODULE) == 0) index.put(node.name, node);
            }
        }
    }
}
