package cc.sighs.cannedmonster.bootstrap;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public final class FusionCoreMod implements ICoreMod {
    @Override
    public Iterable<? extends ITransformer<?>> getTransformers() {
        Map<String, ClassNode> index = new HashMap<>();
        for (var file : LoadingModList.get().getModFiles()) {
            var root = file.getFile().getSecureJar().getRootPath();
            try (var paths = Files.walk(root)) {
                for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                    try (var input = Files.newInputStream(path)) {
                        ClassNode node = new ClassNode();
                        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        index.put(node.name, node);
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot index fusion bytecode in " + file.getFile().getFileName(), exception);
            }
        }
        return List.of(new BodyTransformer(index));
    }

    public static final class BodyTransformer implements ITransformer<ClassNode> {
        private static final String ENTITY = "net/minecraft/world/entity/Entity";
        private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
        private static final String MOB = "net/minecraft/world/entity/Mob";
        private static final String DATA = "net/minecraft/network/syncher/SynchedEntityData";
        private static final String LINKS = "cc/sighs/cannedmonster/fusion/BodyLinks";
        private static final String BOUND = "cc/sighs/cannedmonster/fusion/BoundObject";
        private static final String CALLS = "cc/sighs/cannedmonster/fusion/OrderedCalls";
        private static final String MOTION = "cc/sighs/cannedmonster/fusion/FusionMotion";
        private static final String NAVIGATION = "net/minecraft/world/entity/ai/navigation/PathNavigation";
        private static final Set<String> CORE = Set.of(ENTITY, LIVING, MOB);
        private static final Set<String> LOCAL_TYPES = Set.of(
                "net/minecraft/world/entity/EntityType", DATA, "net/minecraft/util/RandomSource",
                "net/minecraft/world/entity/ai/Brain", "net/minecraft/world/entity/ai/control/Control",
                "net/minecraft/world/entity/ai/control/BodyRotationControl",
                "net/minecraft/world/entity/ai/navigation/PathNavigation", "net/minecraft/world/entity/ai/goal/GoalSelector",
                "net/minecraft/world/entity/ai/sensing/Sensing");
        private static final Set<String> INPUT_METHODS = Set.of("setSpeed(F)V", "setXxa(F)V", "setYya(F)V",
                "setZza(F)V", "setJumping(Z)V", "setYRot(F)V", "setXRot(F)V");
        private final Map<String, ClassNode> index;
        private final Map<String, FieldNode> fields = new HashMap<>();
        private final Map<String, String> declarations = new java.util.concurrent.ConcurrentHashMap<>();
        private final Set<String> velocityFields = new HashSet<>();
        private final Set<String> localInputs = new HashSet<>();
        private final Set<String> targetNames = new HashSet<>();

        public BodyTransformer(Map<String, ClassNode> index) {
            this.index = Map.copyOf(index);
            for (String owner : CORE) {
                ClassNode node = index.get(owner);
                if (node == null) {
                    throw new IllegalStateException("Missing Minecraft class " + owner);
                }
                for (MethodNode method : node.methods) {
                    if (owner.equals(ENTITY) && method.name.equals("setDeltaMovement")) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                                velocityFields.add(key(declaringOwner(field.owner, field.name, field.desc), field.name, field.desc));
                            }
                        }
                    }
                    if (INPUT_METHODS.contains(method.name + method.desc)) {
                        collectInputWrites(node.name, method, new HashSet<>());
                    }
                    if (method.name.equals("isAddedToLevel") && method.desc.equals("()Z")
                            || owner.equals(MOB) && (method.name.equals("getLootTable") || method.name.equals("getLootTableSeed"))) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD) {
                                localInputs.add(key(declaringOwner(field.owner, field.name, field.desc), field.name, field.desc));
                            }
                        }
                    }
                }
            }
            for (String owner : CORE) {
                for (FieldNode field : index.get(owner).fields) {
                    if (shared(owner, field)) {
                        fields.put(key(owner, field.name, field.desc), field);
                    }
                }
            }
            for (ClassNode node : index.values()) {
                if (CORE.contains(node.name) || node.name.equals(DATA) || node.name.equals(NAVIGATION) || subtype(node.name, MOB)) {
                    targetNames.add(node.name);
                } else {
                    for (MethodNode method : node.methods) {
                        for (AbstractInsnNode instruction : method.instructions) {
                            if (instruction instanceof FieldInsnNode field && sharedAccess(field)) {
                                targetNames.add(node.name);
                            }
                        }
                    }
                }
            }
        }

        private static String key(String owner, String name, String descriptor) {
            return owner + "." + name + descriptor;
        }

        private void collectInputWrites(String owner, MethodNode method, Set<String> visited) {
            if (!visited.add(owner + "." + method.name + method.desc)) {
                return;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                    localInputs.add(key(declaringOwner(field.owner, field.name, field.desc), field.name, field.desc));
                } else if (instruction instanceof MethodInsnNode call && CORE.contains(call.owner)) {
                    for (MethodNode child : index.get(call.owner).methods) {
                        if (child.name.equals(call.name) && child.desc.equals(call.desc)) {
                            collectInputWrites(call.owner, child, visited);
                        }
                    }
                }
            }
        }

        private boolean shared(String owner, FieldNode field) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 || field.name.startsWith("cannedmonster$")
                    || localInputs.contains(key(owner, field.name, field.desc))) {
                return false;
            }
            Type type = Type.getType(field.desc);
            return type.getSort() != Type.OBJECT || LOCAL_TYPES.stream().noneMatch(root -> subtype(type.getInternalName(), root));
        }

        private boolean sharedAccess(FieldInsnNode field) {
            return fields.containsKey(key(declaringOwner(field.owner, field.name, field.desc), field.name, field.desc));
        }

        private boolean subtype(String name, String parent) {
            return subtype(name, parent, new HashSet<>());
        }

        private boolean subtype(String name, String parent, Set<String> visited) {
            if (parent.equals(name)) {
                return true;
            }
            ClassNode node = index.get(name);
            if (node == null || !visited.add(name)) {
                return false;
            }
            return subtype(node.superName, parent, visited)
                    || node.interfaces.stream().anyMatch(type -> subtype(type, parent, visited));
        }

        @Override
        public ClassNode transform(ClassNode node, ITransformerVotingContext context) {
            for (MethodNode method : List.copyOf(node.methods)) {
                if (method.name.equals("<init>") || method.name.equals("<clinit>") || method.name.startsWith("cannedmonster$")) {
                    continue;
                }
                Set<MethodInsnNode> creations = method.name.equals("remove") && subtype(node.name, MOB)
                        ? CreationOrigins.fromReceiver(node.name, method) : Set.of();
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (subtype(node.name, MOB) && !CORE.contains(node.name) && (method.access & Opcodes.ACC_STATIC) == 0
                            && (method.name.equals("aiStep") || method.name.equals("tick"))
                            && instruction instanceof MethodInsnNode velocity && velocity.name.equals("setDeltaMovement")
                            && velocity.getOpcode() != Opcodes.INVOKESTATIC && subtype(velocity.owner, ENTITY)
                            && (velocity.desc.equals("(Lnet/minecraft/world/phys/Vec3;)V") || velocity.desc.equals("(DDD)V"))) {
                        method.instructions.insertBefore(velocity, new VarInsnNode(Opcodes.ALOAD, 0));
                        String arguments = velocity.desc.substring(1, velocity.desc.indexOf(')'));
                        method.instructions.set(velocity, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "cc/sighs/cannedmonster/fusion/FusionRiding", "autonomousVelocity",
                                "(Lnet/minecraft/world/entity/Entity;" + arguments + "Lnet/minecraft/world/entity/Entity;)V", false));
                    }
                    if (instruction instanceof MethodInsnNode factory && creations.contains(factory)) {
                        method.instructions.insertBefore(factory, new VarInsnNode(Opcodes.ALOAD, 0));
                        method.instructions.set(factory, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "cc/sighs/cannedmonster/fusion/RemovalCalls", "create",
                                "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity;", false));
                    }
                    if (instruction instanceof FieldInsnNode field
                            && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.PUTFIELD) && sharedAccess(field)) {
                        String owner = declaringOwner(field.owner, field.name, field.desc);
                        boolean read = field.getOpcode() == Opcodes.GETFIELD;
                        if (!read && velocityFields.contains(key(owner, field.name, field.desc))
                                && field.desc.equals("Lnet/minecraft/world/phys/Vec3;") && subtype(node.name, MOB)
                                && !CORE.contains(node.name) && (method.access & Opcodes.ACC_STATIC) == 0
                                && (method.name.equals("tick") || method.name.equals("aiStep"))) {
                            method.instructions.insertBefore(field, new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "cc/sighs/cannedmonster/fusion/FusionRiding", "autonomousVelocity",
                                    "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)V", false));
                        } else {
                            method.instructions.set(field, new MethodInsnNode(Opcodes.INVOKESTATIC, owner,
                                    bridgeName(field.name, read), read ? "(L" + owner + ";)" + field.desc : "(L" + owner + ";" + field.desc + ")V", false));
                        }
                    }
                    if (node.name.equals(MOB) && method.name.equals("serverAiStep")
                            && instruction instanceof MethodInsnNode call && call.name.equals("tick") && call.desc.equals("()V")
                            && (subtype(call.owner, "net/minecraft/world/entity/ai/control/Control")
                            || subtype(call.owner, "net/minecraft/world/entity/ai/navigation/PathNavigation"))) {
                        InsnList guard = new InsnList();
                        LabelNode execute = new LabelNode();
                        LabelNode end = new LabelNode();
                        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LINKS, "isFacet", "(Ljava/lang/Object;)Z", false));
                        guard.add(new JumpInsnNode(Opcodes.IFEQ, execute));
                        guard.add(new InsnNode(Opcodes.POP));
                        guard.add(new JumpInsnNode(Opcodes.GOTO, end));
                        guard.add(execute);
                        method.instructions.insertBefore(call, guard);
                        method.instructions.insert(call, end);
                        if (subtype(call.owner, NAVIGATION)) {
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, MOTION,
                                    "navigationTick", "(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;)V", false));
                        }
                    }
                }
                if (subtype(node.name, MOB) && method.desc.equals("()V")
                        && (method.name.equals("tick") || method.name.equals("aiStep"))
                        && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) == 0) {
                    LabelNode original = new LabelNode();
                    InsnList guard = new InsnList();
                    guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    guard.add(new LdcInsnNode(method.name));
                    guard.add(new InsnNode(node.name.equals(MOB) ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CALLS, "dispatch", "(Ljava/lang/Object;Ljava/lang/String;Z)Z", false));
                    guard.add(new JumpInsnNode(Opcodes.IFEQ, original));
                    guard.add(new InsnNode(Opcodes.RETURN));
                    guard.add(original);
                    method.instructions.insert(guard);
                }
                if (subtype(node.name, LIVING) && method.name.equals("remove")
                        && method.desc.equals("(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")
                        && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) == 0) {
                    LabelNode original = new LabelNode();
                    InsnList guard = new InsnList();
                    guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    guard.add(new InsnNode(node.name.equals(LIVING) ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "cc/sighs/cannedmonster/fusion/RemovalCalls", "dispatch",
                            "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;Z)Z", false));
                    guard.add(new JumpInsnNode(Opcodes.IFEQ, original));
                    guard.add(new InsnNode(Opcodes.RETURN));
                    guard.add(original);
                    method.instructions.insert(guard);
                }
                if (node.name.equals(MOB) && method.name.equals("getNavigation")
                        && method.desc.equals("()Lnet/minecraft/world/entity/ai/navigation/PathNavigation;")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getOpcode() == Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, MOTION,
                                    "navigation", "(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/ai/navigation/PathNavigation;", false));
                        }
                    }
                }
                if (node.name.equals(MOB) && method.name.equals("getControllingPassenger")
                        && method.desc.equals("()Lnet/minecraft/world/entity/LivingEntity;")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getOpcode() == Opcodes.ARETURN) {
                            method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESTATIC, MOTION,
                                    "controllingPassenger", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/LivingEntity;", false));
                        }
                    }
                }
                if (node.name.equals(ENTITY) && method.name.equals("startRiding")
                        && method.desc.equals("(Lnet/minecraft/world/entity/Entity;Z)Z")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LINKS, "body", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
                    hook.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/entity/Entity"));
                    hook.add(new VarInsnNode(Opcodes.ASTORE, 1));
                    method.instructions.insert(hook);
                }

                if (node.name.equals(NAVIGATION) && method.name.equals("moveTo")
                        && method.desc.equals("(Lnet/minecraft/world/level/pathfinder/Path;D)Z")) {
                    LabelNode original = new LabelNode();
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new FieldInsnNode(Opcodes.GETFIELD, NAVIGATION, "mob", "Lnet/minecraft/world/entity/Mob;"));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.DLOAD, 2));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MOTION, "guardMoveTo",
                            "(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/pathfinder/Path;D)Ljava/lang/Boolean;", false));
                    hook.add(new InsnNode(Opcodes.DUP));
                    hook.add(new JumpInsnNode(Opcodes.IFNULL, original));
                    hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                    hook.add(new InsnNode(Opcodes.IRETURN));
                    hook.add(original);
                    hook.add(new InsnNode(Opcodes.POP));
                    method.instructions.insert(hook);
                }
                if (node.name.equals(NAVIGATION) && method.name.equals("stop") && method.desc.equals("()V")) {
                    LabelNode original = new LabelNode();
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new FieldInsnNode(Opcodes.GETFIELD, NAVIGATION, "mob", "Lnet/minecraft/world/entity/Mob;"));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MOTION, "guardStop",
                            "(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;Lnet/minecraft/world/entity/Mob;)Z", false));
                    hook.add(new JumpInsnNode(Opcodes.IFEQ, original));
                    hook.add(new InsnNode(Opcodes.RETURN));
                    hook.add(original);
                    method.instructions.insert(hook);
                }
                if ((node.name.equals(LIVING) && (method.name.equals("travel") || method.name.equals("jumpFromGround")))
                        && Type.getReturnType(method.desc).equals(Type.VOID_TYPE)) {
                    guardFacet(method);
                }
                if (node.name.equals(DATA) && (method.name.equals("getItem") || method.name.equals("set"))
                        && method.desc.startsWith("(Lnet/minecraft/network/syncher/EntityDataAccessor;")) {
                    InsnList redirect = new InsnList();
                    redirect.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    redirect.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    redirect.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LINKS, "data", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
                    redirect.add(new TypeInsnNode(Opcodes.CHECKCAST, DATA));
                    redirect.add(new VarInsnNode(Opcodes.ASTORE, 0));
                    method.instructions.insert(redirect);
                }
            }
            for (FieldNode field : node.fields) {
                if (fields.containsKey(key(node.name, field.name, field.desc))) {
                    node.methods.add(bridge(node.name, field, true));
                    if ((field.access & Opcodes.ACC_FINAL) == 0) {
                        node.methods.add(bridge(node.name, field, false));
                    }
                }
            }
            if (node.name.equals(ENTITY) || node.name.equals(DATA)) {
                injectBinding(node);
            }
            if (node.name.equals(ENTITY)) {
                injectSession(node);
            }
            return node;
        }

        private static void injectSession(ClassNode node) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$session", "Ljava/lang/Object;", null, null));
            MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$session", "()Ljava/lang/Object;", null, null);
            read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            read.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "cannedmonster$session", "Ljava/lang/Object;"));
            read.instructions.add(new InsnNode(Opcodes.ARETURN));
            read.maxLocals = 1;
            read.maxStack = 1;
            node.methods.add(read);
            MethodNode write = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$session", "(Ljava/lang/Object;)V", null, null);
            write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            write.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "cannedmonster$session", "Ljava/lang/Object;"));
            write.instructions.add(new InsnNode(Opcodes.RETURN));
            write.maxLocals = 2;
            write.maxStack = 2;
            node.methods.add(write);
        }

        private static void guardFacet(MethodNode method) {
            LabelNode original = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "cc/sighs/cannedmonster/fusion/FusionRiding", "blocked", "(Lnet/minecraft/world/entity/Entity;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, original));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(original);
            method.instructions.insert(guard);
        }

        private static void injectBinding(ClassNode node) {
            node.interfaces.add(BOUND);
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$body", "Ljava/lang/Object;", null, null));
            MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$body", "()Ljava/lang/Object;", null, null);
            read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            read.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "cannedmonster$body", "Ljava/lang/Object;"));
            read.instructions.add(new InsnNode(Opcodes.ARETURN));
            read.maxLocals = 1;
            read.maxStack = 1;
            node.methods.add(read);
            MethodNode write = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    "cannedmonster$body", "(Ljava/lang/Object;)V", null, null);
            write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            write.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            write.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "cannedmonster$body", "Ljava/lang/Object;"));
            write.instructions.add(new InsnNode(Opcodes.RETURN));
            write.maxLocals = 2;
            write.maxStack = 2;
            node.methods.add(write);
        }

        private String declaringOwner(String owner, String name, String descriptor) {
            String query = key(owner, name, descriptor);
            return declarations.computeIfAbsent(query, ignored -> {
                String current = owner;
                Set<String> visited = new HashSet<>();
                while (current != null && visited.add(current)) {
                    ClassNode node = index.get(current);
                    if (node == null) {
                        break;
                    }
                    for (FieldNode field : node.fields) {
                        if (field.name.equals(name) && field.desc.equals(descriptor)) {
                            return current;
                        }
                    }
                    current = node.superName;
                }
                return "";
            });
        }

        private static String bridgeName(String field, boolean read) {
            return "cannedmonster$" + (read ? "read$" : "write$") + field;
        }

        private MethodNode bridge(String owner, FieldNode field, boolean read) {
            String descriptor = read ? "(L" + owner + ";)" + field.desc : "(L" + owner + ";" + field.desc + ")V";
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    bridgeName(field.name, read), descriptor, null, null);
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            boolean velocity = velocityFields.contains(key(owner, field.name, field.desc));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    velocity ? "cc/sighs/cannedmonster/fusion/FusionRiding" : LINKS,
                    velocity ? "velocityTarget" : "body", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
            Type type = Type.getType(field.desc);
            if (!read) {
                method.instructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), 1));
            }
            method.instructions.add(new FieldInsnNode(read ? Opcodes.GETFIELD : Opcodes.PUTFIELD, owner, field.name, field.desc));
            method.instructions.add(new InsnNode(read ? type.getOpcode(Opcodes.IRETURN) : Opcodes.RETURN));
            method.maxLocals = read ? 1 : 1 + type.getSize();
            method.maxStack = 1 + type.getSize();
            return method;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public cpw.mods.modlauncher.api.TargetType<ClassNode> getTargetType() {
            return cpw.mods.modlauncher.api.TargetType.CLASS;
        }

        @Override
        public Set<Target<ClassNode>> targets() {
            Set<Target<ClassNode>> targets = new HashSet<>();
            for (String name : targetNames) {
                if (!name.startsWith("cc/sighs/cannedmonster/")) {
                    targets.add(Target.targetClass(name.replace('/', '.')));
                }
            }
            return targets;
        }
    }
}
