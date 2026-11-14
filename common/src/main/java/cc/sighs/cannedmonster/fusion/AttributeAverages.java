package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
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
        if (owner == null || owner.level().isClientSide || OrderedCalls.currentMember() instanceof net.minecraft.world.entity.Mob
                || HurtCalls.currentMember() instanceof net.minecraft.world.entity.Mob) return;
        FusionRuntime.baseChanged(owner, instance.getAttribute().unwrapKey().orElseThrow().location(), instance.getBaseValue());
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
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
            if (member.getAttributes().hasAttribute(attribute)) {
                values.put(attribute.key().location(), member.getAttributeBaseValue(attribute));
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
        var access = (NativeAttributeAccess) body.getAttributes();
        var averages = mean(members);
        averages.keySet().forEach(id -> BuiltInRegistries.ATTRIBUTE.getHolder(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id)));
        var introduced = ADDED.computeIfAbsent(body, ignored -> new HashSet<>());
        var ownOrigins = members.getLast();
        introduced.removeIf(holder -> {
            var id = holder.unwrapKey().orElseThrow().location();
            if (averages.containsKey(id)) return ownOrigins.containsKey(id);
            access.cannedmonster$instances().remove(holder);
            return true;
        });
        averages.keySet().stream().filter(id -> !ownOrigins.containsKey(id))
                .forEach(id -> introduced.add(BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow()));
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            averages.forEach((id, value) -> {
                var holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow();
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
            for (String key : tag.getAllKeys()) values.put(ResourceLocation.parse(key), tag.getDouble(key));
            members.add(Map.copyOf(values));
        }
        mean(members);
        return List.copyOf(members);
    }
}
