package cc.sighs.cannedmonster.can;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CanEatingFlowTest {
    @Test
    void usesNormalMealDuration() throws IOException {
        ClassNode item = item();
        assertEquals(32, item.fields.stream().filter(field -> field.name.equals("EAT_DURATION"))
                .findFirst().orElseThrow().value);
    }

    @Test
    void playerFusionOnlyCommitsFromMealCompletionNotRelease() throws IOException {
        ClassNode item = item();
        assertTrue(calls(method(item, "finishUsingItem"), "PlayerFusion", "fuse"));
        assertFalse(calls(method(item, "releaseUsing"), "PlayerFusion", "fuse"));
        assertFalse(calls(method(item, "finishUsingItem"), "CanContents", "clear"));
        assertFalse(calls(method(item, "releaseUsing"), "CanContents", "clear"));
    }

    @Test
    void extractionRemainsSeparateFromMealCompletion() throws IOException {
        ClassNode item = item();
        assertTrue(calls(method(item, "releaseUsing"), "PlayerFusion", "extract"));
        assertFalse(calls(method(item, "finishUsingItem"), "PlayerFusion", "extract"));
        assertTrue(calls(method(item, "finishUsingItem"), "MonsterCanItem", "takePress"));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/" + owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode item() throws IOException {
        try (var input = CanEatingFlowTest.class.getClassLoader()
                .getResourceAsStream("cc/sighs/cannedmonster/can/MonsterCanItem.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
