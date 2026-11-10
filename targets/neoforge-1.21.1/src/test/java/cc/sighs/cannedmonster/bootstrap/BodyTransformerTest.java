package cc.sighs.cannedmonster.bootstrap;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BodyTransformerTest {
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String MOB = "net/minecraft/world/entity/Mob";
    private static final String SPECIES = "example/mod/UnknownSpecies";

    @Test
    void executesTransformedInheritedReadsAndWritesOnOneBody() throws Exception {
        Map<String, ClassNode> index = fixture();
        var transformer = new FusionCoreMod.BodyTransformer(index);
        ClassLoader loader = transformedLoader(index, transformer);
        Class<?> type = loader.loadClass(SPECIES.replace('/', '.'));
        Object host = type.getConstructor().newInstance();
        Object facet = type.getConstructor().newInstance();
        Object data = new Object();
        type.getMethod("writeFire", int.class).invoke(host, 10);
        type.getMethod("writeFire", int.class).invoke(facet, 20);
        assertEquals(20, type.getMethod("readFire").invoke(facet));
        BodyLinks.bind(facet, host, data, new Object());
        try {
            assertEquals(10, type.getMethod("readFire").invoke(facet));
            type.getMethod("writeFire", int.class).invoke(facet, 40);
            assertEquals(40, type.getMethod("readFire").invoke(host));
            assertEquals(20, type.getField("remainingFireTicks").get(facet));
            type.getField("speciesCounter").set(facet, 7);
            assertEquals(0, type.getField("speciesCounter").get(host));
            assertTrue(type.isInstance(facet));
            type.getMethod("tick").invoke(host);
            assertEquals(0, type.getField("baseTicks").get(facet));
            assertEquals(1, type.getField("baseTicks").get(host));
        } finally {
            BodyLinks.unbind(facet, data);
        }
        assertEquals(20, type.getMethod("readFire").invoke(facet));
    }

    @Test
    void doesNotRedirectShadowingFieldsFromUnknownMods() throws Exception {
        Map<String, ClassNode> index = fixture();
        index.get(SPECIES).fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "remainingFireTicks", "I", null, null));
        var transformer = new FusionCoreMod.BodyTransformer(index);
        Class<?> type = transformedLoader(index, transformer).loadClass(SPECIES.replace('/', '.'));
        Object host = type.getConstructor().newInstance();
        Object facet = type.getConstructor().newInstance();
        Object data = new Object();
        BodyLinks.bind(facet, host, data, new Object());
        try {
            type.getMethod("writeFire", int.class).invoke(facet, 99);
            assertEquals(99, type.getMethod("readFire").invoke(facet));
            assertEquals(0, type.getMethod("readFire").invoke(host));
        } finally {
            BodyLinks.unbind(facet, data);
        }
    }

    @Test
    void rejectsAnIncompleteMinecraftClassIndex() {
        assertThrows(IllegalStateException.class, () -> new FusionCoreMod.BodyTransformer(Map.of()));
    }

    @Test
    void unknownOverrideContinuesAtSuperAndUnwindsAfterBody() throws Exception {
        Map<String, ClassNode> index = fixture();
        ClassNode mob = index.get(MOB);
        MethodNode ai = new MethodNode(Opcodes.ACC_PUBLIC, "aiStep", "()V", null, null);
        ai.instructions.add(new InsnNode(Opcodes.RETURN));
        mob.methods.add(ai);
        ClassNode species = index.get(SPECIES);
        species.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "afterBody", "I", null, null));
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new InsnNode(Opcodes.DUP));
        tick.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, SPECIES, "speciesCounter", "I"));
        tick.instructions.add(new InsnNode(Opcodes.ICONST_1));
        tick.instructions.add(new InsnNode(Opcodes.IADD));
        tick.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, SPECIES, "speciesCounter", "I"));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MOB, "tick", "()V", false));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, MOB, "baseTicks", "I"));
        tick.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, SPECIES, "afterBody", "I"));
        tick.instructions.add(new InsnNode(Opcodes.RETURN));
        species.methods.add(tick);
        Class<?> type = transformedLoader(index, new FusionCoreMod.BodyTransformer(index)).loadClass(SPECIES.replace('/', '.'));
        Object host = type.getConstructor().newInstance();
        Object facet = type.getConstructor().newInstance();
        Object data = new Object();
        BodyLinks.bind(facet, host, data, new Object());
        cc.sighs.cannedmonster.fusion.OrderedCalls.bind(host, java.util.List.of(facet), ignored -> {});
        try {
            type.getMethod("tick").invoke(host);
            assertEquals(1, type.getField("speciesCounter").get(facet));
            assertEquals(1, type.getField("speciesCounter").get(host));
            assertEquals(1, type.getField("afterBody").get(facet));
            assertEquals(1, type.getField("afterBody").get(host));
            assertEquals(1, type.getField("baseTicks").get(host));
            assertInstanceOf(cc.sighs.cannedmonster.fusion.BoundObject.class, facet);
        } finally {
            cc.sighs.cannedmonster.fusion.OrderedCalls.unbind(host);
            BodyLinks.unbind(facet, data);
        }
    }

    @Test
    void discoversUnlistedCoreFieldsAndPreservesFinalModifiers() {
        Map<String, ClassNode> index = fixture();
        ClassNode entity = index.get(ENTITY);
        entity.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "unknownModInjectedEnergy", "D", null, null));
        entity.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "unknownOwnedState", "Ljava/util/List;", null, null));
        var transformer = new FusionCoreMod.BodyTransformer(index);
        transformer.transform(entity, null);
        assertTrue(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$read$unknownModInjectedEnergy")));
        assertTrue(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$write$unknownModInjectedEnergy")));
        assertTrue(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$read$unknownOwnedState")));
        assertFalse(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$write$unknownOwnedState")));
        assertTrue(entity.fields.stream().filter(field -> field.name.equals("unknownOwnedState"))
                .allMatch(field -> (field.access & Opcodes.ACC_FINAL) != 0));
    }

    @Test
    void registrationAndMotionOwnershipAreDerivedFromMethodsNotFieldNames() {
        Map<String, ClassNode> index = fixture();
        ClassNode entity = index.get(ENTITY);
        entity.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "arbitraryMembershipFlag", "Z", null, null));
        entity.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "arbitraryMotionInput", "F", null, null));
        MethodNode membership = new MethodNode(Opcodes.ACC_PUBLIC, "isAddedToLevel", "()Z", null, null);
        membership.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        membership.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY, "arbitraryMembershipFlag", "Z"));
        membership.instructions.add(new InsnNode(Opcodes.IRETURN));
        entity.methods.add(membership);
        MethodNode motion = new MethodNode(Opcodes.ACC_PUBLIC, "setXxa", "(F)V", null, null);
        motion.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        motion.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        motion.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY, "arbitraryMotionInput", "F"));
        motion.instructions.add(new InsnNode(Opcodes.RETURN));
        entity.methods.add(motion);
        var transformer = new FusionCoreMod.BodyTransformer(index);
        transformer.transform(entity, null);
        assertFalse(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$read$arbitraryMembershipFlag")));
        assertFalse(entity.methods.stream().anyMatch(method -> method.name.equals("cannedmonster$write$arbitraryMotionInput")));
    }

    @Test
    void removalDispatchCoversUnknownOverridesAndTheCommonBoundary() {
        Map<String, ClassNode> index = fixture();
        String descriptor = "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V";
        for (String name : java.util.List.of(LIVING, SPECIES)) {
            MethodNode remove = new MethodNode(Opcodes.ACC_PUBLIC, "remove", descriptor, null, null);
            remove.instructions.add(new InsnNode(Opcodes.RETURN));
            index.get(name).methods.add(remove);
        }
        var transformer = new FusionCoreMod.BodyTransformer(index);
        for (String name : java.util.List.of(LIVING, SPECIES)) {
            ClassNode node = transformer.transform(index.get(name), null);
            MethodNode remove = node.methods.stream().filter(method -> method.name.equals("remove")).findFirst().orElseThrow();
            boolean guarded = false;
            for (AbstractInsnNode instruction : remove.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("cc/sighs/cannedmonster/fusion/RemovalCalls")
                        && call.name.equals("dispatch")) {
                    guarded = true;
                    assertEquals(name.equals(LIVING) ? Opcodes.ICONST_1 : Opcodes.ICONST_0, call.getPrevious().getOpcode());
                }
            }
            assertTrue(guarded);
        }
    }

    @Test
    void abstractAndNativeRemovalDeclarationsRemainWithoutBytecode() {
        for (int access : java.util.List.of(Opcodes.ACC_ABSTRACT, Opcodes.ACC_NATIVE)) {
            Map<String, ClassNode> index = fixture();
            MethodNode declaration = new MethodNode(Opcodes.ACC_PUBLIC | access, "remove",
                    "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", null, null);
            index.get(SPECIES).methods.add(declaration);
            new FusionCoreMod.BodyTransformer(index).transform(index.get(SPECIES), null);
            assertEquals(0, declaration.instructions.size());
        }
    }

    private static ClassLoader transformedLoader(Map<String, ClassNode> index, FusionCoreMod.BodyTransformer transformer) {
        Map<String, byte[]> bytecode = new HashMap<>();
        index.forEach((name, node) -> {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            transformer.transform(node, null).accept(writer);
            bytecode.put(name.replace('/', '.'), writer.toByteArray());
        });
        return new ClassLoader(BodyTransformerTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    byte[] bytes = bytecode.get(name);
                    if (bytes == null) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
    }

    private static Map<String, ClassNode> fixture() {
        Map<String, ClassNode> index = new HashMap<>();
        ClassNode entity = type(ENTITY, "java/lang/Object");
        entity.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "remainingFireTicks", "I", null, null));
        index.put(ENTITY, entity);
        index.put(LIVING, type(LIVING, ENTITY));
        ClassNode mob = type(MOB, LIVING);
        mob.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "baseTicks", "I", null, null));
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        tick.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tick.instructions.add(new InsnNode(Opcodes.DUP));
        tick.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, MOB, "baseTicks", "I"));
        tick.instructions.add(new InsnNode(Opcodes.ICONST_1));
        tick.instructions.add(new InsnNode(Opcodes.IADD));
        tick.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, MOB, "baseTicks", "I"));
        tick.instructions.add(new InsnNode(Opcodes.RETURN));
        mob.methods.add(tick);
        index.put(MOB, mob);
        ClassNode species = type(SPECIES, MOB);
        species.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "speciesCounter", "I", null, null));
        MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC, "readFire", "()I", null, null);
        read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        read.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, SPECIES, "remainingFireTicks", "I"));
        read.instructions.add(new InsnNode(Opcodes.IRETURN));
        species.methods.add(read);
        MethodNode write = new MethodNode(Opcodes.ACC_PUBLIC, "writeFire", "(I)V", null, null);
        write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        write.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        write.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, SPECIES, "remainingFireTicks", "I"));
        write.instructions.add(new InsnNode(Opcodes.RETURN));
        species.methods.add(write);
        index.put(SPECIES, species);
        return index;
    }

    private static ClassNode type(String name, String parent) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = name;
        node.superName = parent;
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(constructor);
        return node;
    }
}
