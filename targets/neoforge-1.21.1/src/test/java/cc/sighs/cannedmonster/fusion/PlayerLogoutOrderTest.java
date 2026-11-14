package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLogoutOrderTest {
    @Test
    void neoForgeLogoutEventPrecedesTheFinalPlayerSave() throws IOException {
        ClassNode playerList = read("net/minecraft/server/players/PlayerList");
        var remove = playerList.methods.stream().filter(method -> method.name.equals("remove")
                && method.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V")).findFirst().orElseThrow();
        int event = -1, save = -1, index = 0;
        for (var instruction : remove.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("firePlayerLoggedOut")) event = index;
                if (call.name.equals("save") && call.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V")) save = index;
            }
            index++;
        }
        assertTrue(event >= 0 && save > event, "Logout cleanup must not run from the pre-save event");
    }

    @Test
    void fusionCleanupRunsAtRemoveReturnRatherThanLogoutEvent() throws IOException {
        ClassNode mixin = read("cc/sighs/cannedmonster/mixin/PlayerFusionLogoutMixin");
        var handler = mixin.methods.stream().filter(method -> method.name.equals("cannedmonster$afterSave")).findFirst().orElseThrow();
        assertNotNull(handler.visibleAnnotations);
        assertTrue(handler.visibleAnnotations.stream().anyMatch(annotation -> containsReturn(annotation.values)));
        ClassNode main = read("cc/sighs/cannedmonster/CannedMonsterNeoForge121");
        for (var method : main.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode call) {
                    for (Object argument : call.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals("cc/sighs/cannedmonster/fusion/PlayerFusion")) {
                            assertNotEquals("logout", handle.getName());
                        }
                    }
                }
            }
        }
    }

    private static boolean containsReturn(Object value) {
        if ("RETURN".equals(value)) return true;
        if (value instanceof AnnotationNode annotation) return containsReturn(annotation.values);
        return value instanceof List<?> list && list.stream().anyMatch(PlayerLogoutOrderTest::containsReturn);
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = PlayerLogoutOrderTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
