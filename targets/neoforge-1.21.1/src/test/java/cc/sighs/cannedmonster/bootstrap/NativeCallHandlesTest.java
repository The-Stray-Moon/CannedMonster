package cc.sighs.cannedmonster.bootstrap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeCallHandlesTest {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";

    @Test
    void hiddenMembersUseVirtualOverridesAndPlayersUseExactBaseCalls() throws Throwable {
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("net/minecraft/world/damagesource/DamageSource", entity("net/minecraft/world/damagesource/DamageSource", "java/lang/Object", true, 0));
        classes.put(LIVING, entity(LIVING, "java/lang/Object", true, 1));
        classes.put("example/HiddenMember", entity("example/HiddenMember", LIVING, false, 2));
        classes.put(PLAYER, entity(PLAYER, LIVING, true, 3));
        classes.put("example/HiddenPlayer", entity("example/HiddenPlayer", PLAYER, false, 4));
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    String internal = name.replace('.', '/');
                    byte[] bytes = classes.get(internal);
                    if (bytes == null && name.startsWith("cc.sighs.cannedmonster.fusion.")
                            && (name.contains("OrderedCalls") || name.contains("HurtCalls"))) {
                        try (var input = getParent().getResourceAsStream(internal + ".class")) {
                            if (input != null) bytes = input.readAllBytes();
                        } catch (java.io.IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (bytes == null) return super.loadClass(name, resolve);
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        Class<?> hidden = loader.loadClass("example.HiddenMember");
        assertFalse(Modifier.isPublic(hidden.getModifiers()));
        assertThrows(IllegalAccessException.class, () -> MethodHandles.publicLookup().findVirtual(hidden, "tick", MethodType.methodType(void.class)));
        var constructor = hidden.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object member = constructor.newInstance();
        var ordered = handles(loader, "OrderedCalls");
        @SuppressWarnings("unchecked")
        Map<String, MethodHandle> lifecycle = (Map<String, MethodHandle>) ordered.get(hidden);
        lifecycle.get("tick").invoke(member);
        lifecycle.get("aiStep").invoke(member);
        MethodHandle hurt = (MethodHandle) handles(loader, "HurtCalls").get(hidden);
        assertTrue((boolean) hurt.invoke(member, null, 1.0F));
        assertEquals(6, loader.loadClass(LIVING.replace('/', '.')).getField("calls").getInt(member));
        Class<?> hiddenPlayer = loader.loadClass("example.HiddenPlayer");
        var playerConstructor = hiddenPlayer.getDeclaredConstructor();
        playerConstructor.setAccessible(true);
        Object player = playerConstructor.newInstance();
        @SuppressWarnings("unchecked")
        Map<String, MethodHandle> playerLifecycle = (Map<String, MethodHandle>) ordered.get(hiddenPlayer);
        playerLifecycle.get("tick").invoke(player);
        playerLifecycle.get("aiStep").invoke(player);
        assertTrue((boolean) ((MethodHandle) handles(loader, "HurtCalls").get(hiddenPlayer)).invoke(player, null, 1.0F));
        assertEquals(9, loader.loadClass(LIVING.replace('/', '.')).getField("calls").getInt(player));
    }

    private static ClassValue<?> handles(ClassLoader loader, String name) throws Exception {
        var field = loader.loadClass("cc.sighs.cannedmonster.fusion." + name).getDeclaredField("METHODS");
        field.setAccessible(true);
        return (ClassValue<?>) field.get(null);
    }

    private static byte[] entity(String name, String parent, boolean accessible, int amount) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, (accessible ? Opcodes.ACC_PUBLIC : 0) | Opcodes.ACC_SUPER, name, null, parent, null);
        if (name.equals(LIVING)) writer.visitField(Opcodes.ACC_PUBLIC, "calls", "I", null, null).visitEnd();
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        if (amount > 0) {
            for (String method : new String[]{"tick", "aiStep", "hurt"}) {
                String descriptor = method.equals("hurt") ? HURT : "()V";
                var code = writer.visitMethod(Opcodes.ACC_PUBLIC, method, descriptor, null, null);
                code.visitCode();
                code.visitVarInsn(Opcodes.ALOAD, 0);
                code.visitInsn(Opcodes.DUP);
                code.visitFieldInsn(Opcodes.GETFIELD, LIVING, "calls", "I");
                code.visitLdcInsn(amount);
                code.visitInsn(Opcodes.IADD);
                code.visitFieldInsn(Opcodes.PUTFIELD, LIVING, "calls", "I");
                if (Type.getReturnType(descriptor).equals(Type.BOOLEAN_TYPE)) {
                    code.visitInsn(Opcodes.ICONST_1);
                    code.visitInsn(Opcodes.IRETURN);
                } else code.visitInsn(Opcodes.RETURN);
                code.visitMaxs(0, 0);
                code.visitEnd();
            }
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
