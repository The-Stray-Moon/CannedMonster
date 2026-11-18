package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.*;

public final class AttributeAverages {
    private static final Map<LivingEntity, Set<Attribute>> ADDED = new WeakHashMap<>();
    private static final Map<AttributeInstance, java.lang.ref.WeakReference<LivingEntity>> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    public static void baseChanged(AttributeInstance instance) {
        if (APPLYING.get()) return;
        var reference = OWNERS.get(instance);
        LivingEntity owner = reference == null ? null : reference.get();
        if (owner == null || owner.level().isClientSide || OrderedCalls.currentMember() instanceof net.minecraft.world.entity.Mob
                || HurtCalls.currentMember() instanceof net.minecraft.world.entity.Mob) return;
        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(instance.getAttribute());
        if (id != null) FusionRuntime.baseChanged(owner, id, instance.getBaseValue());
    }

    public record State(Map<ResourceLocation, Double> bases, float health) {
        public void restore(LivingEntity body) {
            apply(body, List.of(bases));
            body.setHealth(Math.min(health, body.getMaxHealth()));
        }
    }

    public static State snapshot(LivingEntity body) {
        return new State(capture(body), body.getHealth());
    }

    private AttributeAverages() {}

    public static Map<ResourceLocation, Double> capture(LivingEntity member) {
        Map<ResourceLocation, Double> values = new LinkedHashMap<>();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            Attribute attribute = holder.value();
            if (member.getAttributes().hasAttribute(attribute)) {
                ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
                if (id == null) return;
                values.put(id, member.getAttributeBaseValue(attribute));
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

    public static void apply(LivingEntity body, List<Map<ResourceLocation, Double>> members) {
        apply(body, members, Set.of());
    }

    public static void apply(LivingEntity body, List<Map<ResourceLocation, Double>> members, Set<ResourceLocation> excluded) {
        var access = (NativeAttributeAccess) body.getAttributes();
        var averages = mean(members);
        averages.keySet().stream().filter(id -> !excluded.contains(id)).forEach(id -> attribute(id));
        var introduced = ADDED.computeIfAbsent(body, ignored -> new HashSet<>());
        var ownOrigins = members.get(members.size() - 1);
        introduced.removeIf(attribute -> {
            ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
            if (id == null || excluded.contains(id)) return false;
            if (averages.containsKey(id)) return ownOrigins.containsKey(id);
            access.cannedmonster$instances().remove(attribute);
            return true;
        });
        averages.keySet().stream().filter(id -> !excluded.contains(id) && !ownOrigins.containsKey(id))
                .forEach(id -> introduced.add(attribute(id)));
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            averages.forEach((id, value) -> {
                if (excluded.contains(id)) return;
                Attribute attribute = attribute(id);
                AttributeInstance instance = body.getAttribute(attribute);
                if (instance == null) {
                    instance = new AttributeInstance(attribute, access::cannedmonster$modified);
                    access.cannedmonster$instances().put(attribute, instance);
                    introduced.add(attribute);
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

    private static Attribute attribute(ResourceLocation id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceKey.create(Registries.ATTRIBUTE, id))
                .orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id)).value();
    }

    public static void track(LivingEntity body) {
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            AttributeInstance instance = body.getAttribute(holder.value());
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

    public static ListTag save(List<Map<ResourceLocation, Double>> members) {
        ListTag list = new ListTag();
        for (Map<ResourceLocation, Double> member : members) {
            CompoundTag tag = new CompoundTag();
            member.forEach((key, value) -> tag.putDouble(key.toString(), value));
            list.add(tag);
        }
        return list;
    }

    public static List<Map<ResourceLocation, Double>> load(ListTag list, int expected) {
        if (list.size() != expected) throw new IllegalArgumentException("Fusion attribute member count mismatch");
        List<Map<ResourceLocation, Double>> members = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            Map<ResourceLocation, Double> values = new LinkedHashMap<>();
            for (String key : tag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) values.put(id, tag.getDouble(key));
            }
            members.add(Map.copyOf(values));
        }
        mean(members);
        return List.copyOf(members);
    }
}
