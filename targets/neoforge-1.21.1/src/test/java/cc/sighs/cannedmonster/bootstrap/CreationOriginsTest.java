package cc.sighs.cannedmonster.bootstrap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class CreationOriginsTest {
    private static final String OWNER = "example/UnknownMob";
    private static final String TYPE = "net/minecraft/world/entity/EntityType";

    @Test
    void followsLocalAliasesOfTheReceiversType() {
        MethodNode method = method();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "getType", "()L" + TYPE + ";", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        MethodInsnNode factory = finish(method);
        assertTrue(CreationOrigins.fromReceiver(OWNER, method).contains(factory));
    }

    @Test
    void rejectsRegistryFactoriesAndAnotherReceiversType() {
        MethodNode registry = method();
        registry.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, TYPE, "SOME_TYPE", "L" + TYPE + ";"));
        finish(registry);
        assertTrue(CreationOrigins.fromReceiver(OWNER, registry).isEmpty());
        MethodNode other = method();
        other.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "OTHER", "L" + OWNER + ";"));
        other.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "getType", "()L" + TYPE + ";", false));
        finish(other);
        assertTrue(CreationOrigins.fromReceiver(OWNER, other).isEmpty());
    }

    @Test
    void rejectsAmbiguousControlFlowBetweenSelfTypeAndRegistryType() {
        MethodNode method = method();
        LabelNode registry = new LabelNode(), merge = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, registry));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "getType", "()L" + TYPE + ";", false));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, merge));
        method.instructions.add(registry);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, TYPE, "OTHER_TYPE", "L" + TYPE + ";"));
        method.instructions.add(merge);
        finish(method);
        assertTrue(CreationOrigins.fromReceiver(OWNER, method).isEmpty());
    }

    private static MethodNode method() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "remove", "(Ljava/lang/Object;)V", null, null);
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private static MethodInsnNode finish(MethodNode method) {
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        MethodInsnNode factory = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TYPE, "create", "(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;", false);
        method.instructions.add(factory);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return factory;
    }
}
