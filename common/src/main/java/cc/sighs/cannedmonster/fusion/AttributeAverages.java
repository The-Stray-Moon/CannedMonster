package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.platform.AttributeAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.lang.ref.WeakReference;
import java.util.*;

public final class AttributeAverages {
    private static final Map<LivingEntity, Set<String>> ADDED = new WeakHashMap<>();
    private static final Map<AttributeInstance, WeakReference<LivingEntity>> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private AttributeAverages() {}

    public static void baseChanged(AttributeInstance instance) {
        if (APPLYING.get()) return;
        var reference = OWNERS.get(instance);
        LivingEntity owner = reference == null ? null : reference.get();
        if (owner == null || owner.level().isClientSide() || OrderedCalls.currentMember() instanceof Mob
                || HurtCalls.currentMember() instanceof Mob) return;
        FusionRuntime.baseChanged(owner, AttributeAccess.id(instance), instance.getBaseValue());
    }

    public record State(Map<String, Double> bases, float health) {
        public void restore(LivingEntity body) {
            apply(body, List.of(bases));
            body.setHealth(Math.min(health, body.getMaxHealth()));
        }
    }

    public static State snapshot(LivingEntity body) {
        return new State(capture(body), body.getHealth());
    }

    public static Map<String, Double> capture(LivingEntity member) {
        Map<String, Double> values = new LinkedHashMap<>();
        AttributeAccess.each(member, instance -> {
            values.put(AttributeAccess.id(instance), instance.getBaseValue());
            OWNERS.put(instance, new WeakReference<>(member));
        });
        return Map.copyOf(values);
    }

    public static <K> Map<K, Double> mean(List<Map<K, Double>> members) {
        Map<K, Double> averages = new LinkedHashMap<>();
        Map<K, Integer> counts = new HashMap<>();
        for (Map<K, Double> member : members) {
            member.forEach((key, value) -> {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite fusion attribute: " + key);
                int count = counts.merge(key, 1, Integer::sum);
                double previous = averages.getOrDefault(key, 0.0);
                averages.put(key, previous * ((count - 1.0) / count) + value / count);
            });
        }
        return Map.copyOf(averages);
    }

    public static void apply(LivingEntity body, List<Map<String, Double>> members) {
        apply(body, members, Set.of());
    }

    public static void apply(LivingEntity body, List<Map<String, Double>> members, Set<String> excluded) {
        var averages = mean(members);
        averages.keySet().stream().filter(id -> !excluded.contains(id)).forEach(AttributeAccess::validate);
        var introduced = ADDED.computeIfAbsent(body, ignored -> new HashSet<>());
        var ownOrigins = members.get(members.size() - 1);
        introduced.removeIf(id -> {
            if (excluded.contains(id)) return false;
            if (averages.containsKey(id)) return ownOrigins.containsKey(id);
            AttributeAccess.remove(body, id);
            return true;
        });
        averages.keySet().stream().filter(id -> !excluded.contains(id) && !ownOrigins.containsKey(id))
                .forEach(introduced::add);
        quiet(() -> averages.forEach((id, value) -> {
            if (excluded.contains(id)) return;
            if (!AttributeAccess.has(body, id)) introduced.add(id);
            AttributeInstance instance = AttributeAccess.ensure(body, id);
            OWNERS.put(instance, new WeakReference<>(body));
            instance.setBaseValue(value);
            AttributeAccess.modified(body, instance);
        }));
        if (introduced.isEmpty()) ADDED.remove(body);
        if (body.getHealth() > body.getMaxHealth()) body.setHealth(body.getMaxHealth());
    }

    public static void track(LivingEntity body) {
        AttributeAccess.each(body, instance -> OWNERS.put(instance, new WeakReference<>(body)));
    }

    public static void quiet(Runnable action) {
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            action.run();
        } finally {
            if (previous) APPLYING.set(true);
            else APPLYING.remove();
        }
    }

    public static ListTag save(List<Map<String, Double>> members) {
        ListTag list = new ListTag();
        for (Map<String, Double> member : members) {
            CompoundTag tag = new CompoundTag();
            member.forEach(tag::putDouble);
            list.add(tag);
        }
        return list;
    }

    public static List<Map<String, Double>> load(ListTag list, int expected) {
        if (list.size() != expected) throw new IllegalArgumentException("Fusion attribute member count mismatch");
        List<Map<String, Double>> members = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = cc.sighs.cannedmonster.platform.TagAccess.compound(list, i);
            Map<String, Double> values = new LinkedHashMap<>();
            for (String key : cc.sighs.cannedmonster.platform.TagAccess.keys(tag)) {
                values.put(AttributeAccess.normalize(key), cc.sighs.cannedmonster.platform.TagAccess.number(tag, key));
            }
            members.add(Map.copyOf(values));
        }
        mean(members);
        return List.copyOf(members);
    }
}
