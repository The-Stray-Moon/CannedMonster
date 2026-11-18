package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.*;

public final class AttributeAverages {
    private static final Map<LivingEntity, Set<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>>> ADDED = new WeakHashMap<>();
    private static final Map<AttributeInstance, java.lang.ref.WeakReference<LivingEntity>> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    public static void baseChanged(AttributeInstance instance) {
        if (APPLYING.get()) return;
        var reference = OWNERS.get(instance);
        LivingEntity owner = reference == null ? null : reference.get();
        if (owner == null || owner.level().isClientSide() || OrderedCalls.currentMember() instanceof net.minecraft.world.entity.Mob
                || HurtCalls.currentMember() instanceof net.minecraft.world.entity.Mob) return;
        FusionRuntime.baseChanged(owner, instance.getAttribute().unwrapKey().orElseThrow().identifier(), instance.getBaseValue());
    }

    public record State(Map<Identifier, Double> bases, float health) {
        public void restore(LivingEntity body) {
            apply(body, List.of(bases));
            body.setHealth(Math.min(health, body.getMaxHealth()));
        }
    }

    public static State snapshot(LivingEntity body) {
        return new State(capture(body), body.getHealth());
    }

    private AttributeAverages() {}

    private static Identifier current(Identifier id) {
        if (BuiltInRegistries.ATTRIBUTE.get(id).isPresent()) return id;
        String path = id.getPath();
        int dot = path.lastIndexOf('.');
        if (dot < 0) return id;
        return Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(dot + 1));
    }

    public static Map<Identifier, Double> capture(LivingEntity member) {
        Map<Identifier, Double> values = new LinkedHashMap<>();
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(attribute -> {
            if (member.getAttributes().hasAttribute(attribute)) {
                values.put(attribute.key().identifier(), member.getAttributeBaseValue(attribute));
                AttributeInstance instance = member.getAttribute(attribute);
                if (instance != null) OWNERS.put(instance, new java.lang.ref.WeakReference<>(member));
            }
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

    public static void apply(LivingEntity body, List<Map<Identifier, Double>> members) {
        apply(body, members, Set.of());
    }

    public static void apply(LivingEntity body, List<Map<Identifier, Double>> members, Set<Identifier> excluded) {
        var access = (NativeAttributeAccess) body.getAttributes();
        var averages = mean(members);
        averages.keySet().stream().filter(id -> !excluded.contains(id)).forEach(id -> BuiltInRegistries.ATTRIBUTE.get(current(id))
                .orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id)));
        var introduced = ADDED.computeIfAbsent(body, ignored -> new HashSet<>());
        var ownOrigins = members.getLast();
        introduced.removeIf(holder -> {
            var id = holder.unwrapKey().orElseThrow().identifier();
            if (excluded.contains(id)) return false;
            if (averages.containsKey(id)) return ownOrigins.containsKey(id);
            access.cannedmonster$instances().remove(holder);
            return true;
        });
        averages.keySet().stream().filter(id -> !excluded.contains(id) && !ownOrigins.containsKey(id))
                .forEach(id -> introduced.add(BuiltInRegistries.ATTRIBUTE.get(current(id)).orElseThrow()));
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            averages.forEach((id, value) -> {
                if (excluded.contains(id)) return;
                var holder = BuiltInRegistries.ATTRIBUTE.get(current(id)).orElseThrow();
                AttributeInstance instance = body.getAttribute(holder);
                if (instance == null) {
                    instance = new AttributeInstance(holder, access::cannedmonster$modified);
                    access.cannedmonster$instances().put(holder, instance);
                    introduced.add(holder);
                }
                OWNERS.put(instance, new java.lang.ref.WeakReference<>(body));
                instance.setBaseValue(value);
                access.cannedmonster$modified(instance);
            });
        } finally {
            if (previous) APPLYING.set(true);
            else APPLYING.remove();
        }
        if (introduced.isEmpty()) ADDED.remove(body);
        if (body.getHealth() > body.getMaxHealth()) body.setHealth(body.getMaxHealth());
    }

    public static void track(LivingEntity body) {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            AttributeInstance instance = body.getAttribute(holder);
            if (instance != null) OWNERS.put(instance, new java.lang.ref.WeakReference<>(body));
        });
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

    public static ListTag save(List<Map<Identifier, Double>> members) {
        ListTag list = new ListTag();
        for (Map<Identifier, Double> member : members) {
            CompoundTag tag = new CompoundTag();
            member.forEach((key, value) -> tag.putDouble(key.toString(), value));
            list.add(tag);
        }
        return list;
    }

    public static List<Map<Identifier, Double>> load(ListTag list, int expected) {
        if (list.size() != expected) throw new IllegalArgumentException("Fusion attribute member count mismatch");
        List<Map<Identifier, Double>> members = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i).orElseThrow();
            Map<Identifier, Double> values = new LinkedHashMap<>();
            for (String key : tag.keySet()) values.put(current(Identifier.parse(key)), tag.getDoubleOr(key, 0.0));
            members.add(Map.copyOf(values));
        }
        mean(members);
        return List.copyOf(members);
    }
}
