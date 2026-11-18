package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class FusionNavigation {
    private static final Set<String> REQUESTS = Set.of("createPath", "moveTo", "stop", "tick", "getPath", "isDone",
            "isInProgress", "isStuck", "recomputePath", "setSpeedModifier", "getTargetPos", "shouldRecomputePath", "getMaxDistanceToWaypoint");
    private static final Map<String, String> SRG = Map.ofEntries(
            Map.entry("m_26524_", "createPath"), Map.entry("m_26556_", "createPath"), Map.entry("m_26548_", "createPath"),
            Map.entry("m_7864_", "createPath"), Map.entry("m_148218_", "createPath"), Map.entry("m_6570_", "createPath"),
            Map.entry("m_26519_", "moveTo"), Map.entry("m_5624_", "moveTo"), Map.entry("m_26536_", "moveTo"),
            Map.entry("m_26573_", "stop"), Map.entry("m_26566_", "tick"), Map.entry("m_26570_", "getPath"),
            Map.entry("m_26571_", "isDone"), Map.entry("m_26572_", "isInProgress"), Map.entry("m_26577_", "isStuck"),
            Map.entry("m_26517_", "recomputePath"), Map.entry("m_26529_", "setSpeedModifier"),
            Map.entry("m_26567_", "getTargetPos"), Map.entry("m_200903_", "shouldRecomputePath"),
            Map.entry("m_148228_", "getMaxDistanceToWaypoint"));
    private record Call(MethodHandle handle, Class<?> result) {}
    public record Result(Object value) {}
    private static final Result EMPTY = new Result(null);
    private static final Result YES = new Result(true);
    private static final Result NO = new Result(false);
    private static final Map<String, Call> CALLS = calls();

    private FusionNavigation() {}

    private static Map<String, Call> calls() {
        Map<String, Call> calls = new HashMap<>();
        try {
            var lookup = MethodHandles.privateLookupIn(PathNavigation.class, MethodHandles.lookup());
            for (var method : PathNavigation.class.getDeclaredMethods()) {
                if (!REQUESTS.contains(canonical(method.getName())) || Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
                MethodType signature = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                MethodHandle handle = lookup.findVirtual(PathNavigation.class, method.getName(), signature)
                        .asSpreader(Object[].class, signature.parameterCount())
                        .asType(MethodType.methodType(Object.class, PathNavigation.class, Object[].class));
                calls.put(method.getName() + signature.descriptorString(), new Call(handle, method.getReturnType()));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot bind navigation request contracts", failure);
        }
        return Map.copyOf(calls);
    }

    private static String canonical(String name) {
        return SRG.getOrDefault(name, name);
    }

    public static boolean redirects(Mob member) {
        return BodyLinks.isFacet(member) && FusionMotion.of(member) != null;
    }

    public static Result dispatch(PathNavigation navigation, Mob member, String signature, Object[] arguments) {
        FusionMotion motion = FusionMotion.of(member);
        if (motion == null || !BodyLinks.isFacet(member)) return null;
        Call call = CALLS.get(signature);
        if (call == null) throw new IllegalArgumentException("Unknown navigation request " + signature);
        String name = canonical(signature.substring(0, signature.indexOf('(')));
        if (name.equals("tick")) return EMPTY;
        if (name.equals("stop")) {
            motion.stop(member);
            return EMPTY;
        }
        if (name.equals("moveTo") && arguments.length == 2
                && (arguments[0] == null || arguments[0] instanceof net.minecraft.world.level.pathfinder.Path)) {
            if (arguments[0] == null) { motion.stop(member); return NO; }
        }
        boolean request = name.equals("createPath") || name.equals("moveTo");
        if (request ? !motion.mayPath(member) : motion.pathOwner() != member) {
            if (name.equals("isDone")) return YES;
            if (call.result == boolean.class) return NO;
            if (call.result == float.class) return new Result(0.5F);
            return EMPTY;
        }
        return motion.query(member, () -> {
            try {
                Object result = (Object) call.handle.invokeExact(motion.bodyNavigation(), arguments);
                return result == null ? EMPTY : new Result(result);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Native navigation request failed: " + signature, failure);
            }
        });
    }
}
