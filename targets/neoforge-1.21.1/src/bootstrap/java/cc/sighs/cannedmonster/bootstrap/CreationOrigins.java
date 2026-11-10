package cc.sighs.cannedmonster.bootstrap;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.HashSet;
import java.util.Set;

public final class CreationOrigins {
    private CreationOrigins() {
    }

    public static Set<MethodInsnNode> fromReceiver(String owner, MethodNode method) {
        Set<MethodInsnNode> result = new HashSet<>();
        boolean candidate = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && factory(call)) candidate = true;
        }
        if (!candidate) return result;
        try {
            Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter(Opcodes.ASM9) {
                private final AbstractInsnNode self = new LabelNode();

                @Override
                public SourceValue newParameterValue(boolean instanceMethod, int local, Type type) {
                    return instanceMethod && local == 0 ? new SourceValue(1, self) : new SourceValue(type.getSize());
                }

                @Override
                public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                    return value;
                }
            });
            Frame<SourceValue>[] frames = analyzer.analyze(owner, method);
            for (int i = 0; i < method.instructions.size(); i++) {
                if (!(method.instructions.get(i) instanceof MethodInsnNode call) || !factory(call) || frames[i] == null) continue;
                SourceValue receiver = frames[i].getStack(frames[i].getStackSize() - 2);
                if (receiver.insns.isEmpty()) continue;
                boolean proven = true;
                for (AbstractInsnNode origin : receiver.insns) {
                    if (!(origin instanceof MethodInsnNode getType) || !getType.name.equals("getType")
                            || !getType.desc.equals("()Lnet/minecraft/world/entity/EntityType;")
                            || getType.getOpcode() == Opcodes.INVOKESTATIC) {
                        proven = false;
                        break;
                    }
                    Frame<SourceValue> before = frames[method.instructions.indexOf(getType)];
                    SourceValue target = before.getStack(before.getStackSize() - 1);
                    if (!target.equals(before.getLocal(0))) {
                        proven = false;
                        break;
                    }
                }
                if (proven) result.add(call);
            }
        } catch (AnalyzerException exception) {
            throw new IllegalStateException("Cannot prove creation origin in " + owner + "." + method.name + method.desc, exception);
        }
        return result;
    }

    private static boolean factory(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals("net/minecraft/world/entity/EntityType")
                && call.name.equals("create") && call.desc.equals("(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;");
    }
}
