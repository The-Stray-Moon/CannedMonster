package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MemberEventBoundaryTest {
    @Test
    void eventPacketLookupUsesLevelRatherThanClientLevel() throws IOException {
        var listener = read("net/minecraft/client/multiplayer/ClientPacketListener");
        var method = listener.methods.stream().filter(value -> value.name.equals("handleEntityEvent")).findFirst().orElseThrow();
        boolean found = false;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("getEntity")) {
                assertEquals("(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;", call.desc);
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    void displayTickStopsBeforeMobCommonWork() throws IOException {
        var mixin = read("cc/sighs/cannedmonster/mixin/DisplayMobMixin");
        assertTrue(mixin.methods.stream().flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("cancel")));
        var state = read("cc/sighs/cannedmonster/fusion/visual/ClientAppearance");
        assertTrue(state.methods.stream().anyMatch(method -> method.name.equals("apply")));
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = MemberEventBoundaryTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
