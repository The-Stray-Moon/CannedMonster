package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.*;

public final class AttributeAverages {
    private AttributeAverages() {}

    public static Map<ResourceLocation, Double> capture(Mob member) {
        Map<ResourceLocation, Double> values = new LinkedHashMap<>();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
            if (member.getAttributes().hasAttribute(attribute)) {
                values.put(attribute.key().location(), member.getAttributeBaseValue(attribute));
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

    public static void apply(Mob body, List<Map<ResourceLocation, Double>> members) {
        var access = (NativeAttributeAccess) body.getAttributes();
        mean(members).forEach((id, value) -> {
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id));
            AttributeInstance instance = body.getAttribute(holder);
            if (instance == null) {
                instance = new AttributeInstance(holder, access::cannedmonster$modified);
                access.cannedmonster$instances().put(holder, instance);
            }
            instance.setBaseValue(value);
            access.cannedmonster$modified(instance);
        });
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
