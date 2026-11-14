package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class HurtCalls {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ClassValue<MethodHandle> METHODS = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                MethodType signature = MethodType.methodType(boolean.class, DamageSource.class, float.class);
                if (Player.class.isAssignableFrom(type)) {
                    return MethodHandles.privateLookupIn(Player.class, MethodHandles.lookup())
                            .findSpecial(Player.class, "hurt", signature, Player.class);
                }
                return MethodHandles.publicLookup().findVirtual(type, "hurt", signature);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot bind native hurt for " + type.getName(), failure);
            }
        }
    };

    private HurtCalls() {}

    public static Object currentMember() {
        Frame frame = FRAMES.get().peek();
        return frame == null ? null : frame.current;
    }

    public static Boolean dispatch(LivingEntity receiver, DamageSource source, float amount, boolean boundary) {
        if (!(BodyLinks.body(receiver) instanceof Player player) || player.level().isClientSide) return null;
        var facets = FusionRuntime.facets(player);
        if (facets.isEmpty()) return null;
        Deque<Frame> frames = FRAMES.get();
        Frame active = frames.peek();
        if (active != null && active.player == player && active.current == receiver) {
            if (!boundary || receiver == player) return null;
            if (!active.advanced) {
                active.advanced = true;
                active.source = source;
                active.amount = amount;
                advance(active, active.index + 1);
            }
            return active.result;
        }
        if (receiver != player) return boundary ? Boolean.FALSE : null;
        List<LivingEntity> members = new ArrayList<>(facets);
        members.add(player);
        Frame frame = new Frame(player, List.copyOf(members), source, amount);
        frames.push(frame);
        try {
            advance(frame, 0);
            return frame.result;
        } finally {
            frames.pop();
            if (frames.isEmpty()) FRAMES.remove();
        }
    }

    private static void advance(Frame frame, int index) {
        if (index >= frame.members.size()) return;
        LivingEntity previous = frame.current;
        int previousIndex = frame.index;
        boolean previousAdvanced = frame.advanced;
        frame.current = frame.members.get(index);
        frame.index = index;
        frame.advanced = false;
        try {
            boolean result = (boolean) METHODS.get(frame.current.getClass()).invoke(frame.current, frame.source, frame.amount);
            if (frame.current == frame.player) frame.result = result;
            if (!frame.advanced && frame.current != frame.player) advance(frame, index + 1);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Native fusion hurt failed", failure);
        } finally {
            frame.current = previous;
            frame.index = previousIndex;
            frame.advanced = previousAdvanced;
        }
    }

    private static final class Frame {
        final Player player;
        final List<LivingEntity> members;
        DamageSource source;
        float amount;
        LivingEntity current;
        int index;
        boolean advanced;
        boolean result;

        Frame(Player player, List<LivingEntity> members, DamageSource source, float amount) {
            this.player = player;
            this.members = members;
            this.source = source;
            this.amount = amount;
        }
    }
}
