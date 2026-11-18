package cc.sighs.cannedmonster.bootstrap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyTransformer120Test {
    private static final String BOUND = "cc/sighs/cannedmonster/fusion/BoundObject";

    @Test
    void transformsCoreClassesFor1201() {
        String path = System.getProperty("cannedmonster.test.minecraft");
        assertNotNull(path, "Minecraft artifact property is missing");
        Map<String, ClassNode> index = BytecodeIndex.scan(List.of(Path.of(path)));
        BodyTransformer transformer = new BodyTransformer(index);

        assertTrue(transformer.targets().contains("net.minecraft.world.entity.Entity"));
        assertTrue(transformer.targets().contains("net.minecraft.world.entity.LivingEntity"));
        assertTrue(transformer.targets().contains("net.minecraft.world.entity.Mob"));
        assertTrue(transformer.targets().contains("net.minecraft.world.entity.ai.navigation.PathNavigation"));

        ClassNode entity = transform(transformer, index, "net/minecraft/world/entity/Entity");
        assertTrue(entity.interfaces.contains(BOUND));
        assertNotNull(field(entity, "cannedmonster$body"));
        assertNotNull(method(entity, "cannedmonster$body", "()Ljava/lang/Object;"));

        ClassNode data = transform(transformer, index, "net/minecraft/network/syncher/SynchedEntityData");
        assertTrue(data.interfaces.contains(BOUND));
        assertNotNull(field(data, "cannedmonster$session"));

        ClassNode mob = transform(transformer, index, "net/minecraft/world/entity/Mob");
        assertTrue(hasLifecycleGuard(mob, "tick"));
        assertTrue(methods(mob).stream().anyMatch(method -> "getControllingPassenger".equals(method.name)
                && calls(method, "cc/sighs/cannedmonster/fusion/FusionMotion", "controllingPassenger")));

        ClassNode navigation = transform(transformer, index, "net/minecraft/world/entity/ai/navigation/PathNavigation");
        assertTrue(methods(navigation).stream().anyMatch(method -> "moveTo".equals(method.name)
                && method.desc.equals("(Lnet/minecraft/world/level/pathfinder/Path;D)Z")
                && calls(method, "cc/sighs/cannedmonster/fusion/FusionMotion", "guardMoveTo")));
        assertTrue(methods(navigation).stream().anyMatch(method -> "stop".equals(method.name)
                && method.desc.equals("()V")
                && calls(method, "cc/sighs/cannedmonster/fusion/FusionMotion", "guardStop")));
    }

    private static ClassNode transform(BodyTransformer transformer, Map<String, ClassNode> index, String name) {
        ClassNode source = index.get(name);
        assertNotNull(source, "Missing indexed class " + name);
        ClassNode copy = new ClassNode();
        source.accept(copy);
        return transformer.transform(copy);
    }

    private static FieldNode field(ClassNode node, String name) {
        return node.fields.stream().filter(field -> field.name.equals(name)).findFirst().orElse(null);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return methods(node).stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElse(null);
    }

    private static List<MethodNode> methods(ClassNode node) {
        return node.methods;
    }

    private static boolean hasLifecycleGuard(ClassNode node, String name) {
        return methods(node).stream().filter(method -> method.name.equals(name) && method.desc.equals("()V"))
                .anyMatch(method -> calls(method, "cc/sighs/cannedmonster/fusion/OrderedCalls", "dispatch"));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
