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
                if (!REQUESTS.contains(method.getName()) || Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
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

    public static boolean redirects(Mob member) {
        return BodyLinks.isFacet(member) && FusionMotion.of(member) != null;
    }

    public static Result dispatch(PathNavigation navigation, Mob member, String signature, Object[] arguments) {
        FusionMotion motion = FusionMotion.of(member);
        if (motion == null || !BodyLinks.isFacet(member)) return null;
        Call call = CALLS.get(signature);
        if (call == null) throw new IllegalArgumentException("Unknown navigation request " + signature);
        String name = signature.substring(0, signature.indexOf('('));
        if (name.equals("tick")) return EMPTY;
        if (name.equals("stop")) {
            motion.stop(member);
            return EMPTY;
        }
        if (name.equals("moveTo") && arguments.length == 2 && signature.startsWith("moveTo(Lnet/minecraft/world/level/pathfinder/Path;")) {
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
