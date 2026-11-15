package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMemberRotationTest {
    @Test
    void bothLookAtImplementationsHaveCancellationGuards() throws IOException {
        assertCancels("PlayerMemberRotationMixin", "cannedmonster$noAutoAim");
        assertCancels("PlayerMemberLookMixin", "cannedmonster$noLivingAutoAim");
    }

    @Test
    void viewQueriesDelegateToPlayerRatherThanStaleMemberFields() throws IOException {
        assertDelegates("PlayerMemberRotationMixin", "cannedmonster$pitch", "getXRot");
        assertDelegates("PlayerMemberRotationMixin", "cannedmonster$yaw", "getYRot");
        assertDelegates("PlayerMemberRotationMixin", "cannedmonster$viewPitch", "getViewXRot");
        assertDelegates("PlayerMemberLookMixin", "cannedmonster$viewYaw", "getViewYRot");
    }

    private static void assertCancels(String type, String method) throws IOException {
        assertTrue(calls(type, method, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel"));
    }

    private static void assertDelegates(String type, String method, String target) throws IOException {
        assertTrue(calls(type, method, "net/minecraft/world/entity/player/Player", target));
    }

    private static boolean calls(String type, String method, String owner, String name) throws IOException {
        try (var stream = PlayerMemberRotationTest.class.getClassLoader().getResourceAsStream("cc/sighs/cannedmonster/mixin/" + type + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var handler = node.methods.stream().filter(value -> value.name.equals(method)).findFirst().orElseThrow();
            for (var instruction : handler.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            }
            return false;
        }
    }
}
