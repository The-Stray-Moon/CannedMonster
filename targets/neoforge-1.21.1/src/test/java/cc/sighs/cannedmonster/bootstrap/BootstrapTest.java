package cc.sighs.cannedmonster.bootstrap;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapTest {
    @TempDir
    Path temporary;

    @Test
    void indexesClassFilesWithoutLoadingThem() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "unknown/Example", null, "missing/Parent", null);
        writer.visitEnd();
        Files.createDirectories(temporary.resolve("unknown"));
        Files.write(temporary.resolve("unknown/Example.class"), writer.toByteArray());
        Files.writeString(temporary.resolve("ignored.txt"), "not bytecode");
        var index = BytecodeIndex.scan(List.of(temporary));
        assertEquals(Set.of("unknown/Example"), index.keySet());
        assertEquals("missing/Parent", index.get("unknown/Example").superName);
    }

    @Test
    void rejectsMissingIndexRoots() {
        assertThrows(IllegalStateException.class, () -> BytecodeIndex.scan(List.of(temporary.resolve("missing"))));
    }

    @Test
    void adapterIncludesExternalFieldUsersAndExcludesItsOwnRuntime() {
        Map<String, ClassNode> index = new HashMap<>();
        String parent = "java/lang/Object";
        for (String name : List.of("Entity", "LivingEntity", "Mob")) {
            ClassNode node = new ClassNode();
            node.name = "net/minecraft/world/entity/" + name;
            node.superName = parent;
            index.put(node.name, node);
            parent = node.name;
        }
        index.get("net/minecraft/world/entity/Entity").fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "state", "I", null, null));
        for (String name : List.of("other/mod/FieldUser", "cc/sighs/cannedmonster/fusion/Runtime")) {
            ClassNode node = new ClassNode();
            node.name = name;
            node.superName = "java/lang/Object";
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "read", "()I", null, null);
            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/entity/Mob", "state", "I"));
            node.methods.add(method);
            index.put(name, node);
        }
        var adapter = new FusionTransformationService.Adapter(new BodyTransformer(index));
        assertEquals(TargetType.CLASS, adapter.getTargetType());
        assertEquals(TransformerVoteResult.YES, adapter.castVote(null));
        assertTrue(adapter.targets().contains(ITransformer.Target.targetClass("other.mod.FieldUser")));
        assertFalse(adapter.targets().contains(ITransformer.Target.targetClass("cc.sighs.cannedmonster.fusion.Runtime")));
        assertEquals("cannedmonster_asm", new FusionTransformationService().name());
    }

    @Test
    void publishedJarSeparatesServiceAndGameLayers() throws Exception {
        try (ZipFile outer = new ZipFile(System.getProperty("cannedmonster.test.artifact"))) {
            assertNotNull(outer.getEntry("cc/sighs/cannedmonster/bootstrap/FusionTransformationService.class"));
            assertEquals("cc.sighs.cannedmonster.bootstrap.FusionTransformationService",
                    new String(outer.getInputStream(outer.getEntry("META-INF/services/cpw.mods.modlauncher.api.ITransformationService"))
                            .readAllBytes(), StandardCharsets.UTF_8).trim());
            assertNotNull(outer.getEntry("META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator"));
            assertNull(outer.getEntry("META-INF/neoforge.mods.toml"));
            assertTrue(outer.stream().filter(entry -> entry.getName().endsWith(".class"))
                    .allMatch(entry -> entry.getName().startsWith("cc/sighs/cannedmonster/bootstrap/")));
            Map<String, byte[]> game = new HashMap<>();
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(
                    outer.getInputStream(outer.getEntry("META-INF/cannedmonster/game.jar")).readAllBytes()))) {
                for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    if (!entry.isDirectory()) game.put(entry.getName(), input.readAllBytes());
                }
            }
            assertTrue(game.containsKey("META-INF/neoforge.mods.toml"));
            assertTrue(game.containsKey("cc/sighs/cannedmonster/fusion/FusionRuntime.class"));
            assertTrue(game.keySet().stream().anyMatch(name -> name.endsWith("mixins.json")));
            assertTrue(game.keySet().stream().anyMatch(name -> name.startsWith("assets/cannedmonster/")));
            assertTrue(game.keySet().stream().noneMatch(name -> name.contains("bootstrap") || name.endsWith(".jar")));
            assertTrue(outer.stream().noneMatch(entry -> entry.getName().toLowerCase().contains("coremod")));
            assertTrue(game.keySet().stream().noneMatch(name -> name.toLowerCase().contains("coremod")));
        }
    }
}
