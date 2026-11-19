package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.NativeAttributeAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class AttributeAccess {
    public static final String MOVEMENT_SPEED = "minecraft:generic.movement_speed";
    private static final UUID SPEED_MODIFIER = UUID.fromString("6d0f6c9e-4ea4-4f57-9b30-71c9f9a1c5a4");

    private AttributeAccess() {}

    public static void each(LivingEntity entity, Consumer<AttributeInstance> action) {
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            AttributeInstance instance = entity.getAttribute(holder.value());
            if (instance != null) action.accept(instance);
        });
    }

    public static String id(AttributeInstance instance) {
        return BuiltInRegistries.ATTRIBUTE.getKey(instance.getAttribute()).toString();
    }

    public static String normalize(String id) {
        return new ResourceLocation(id).toString();
    }

    public static void validate(String id) {
        attribute(id);
    }

    private static Attribute attribute(String id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceKey.create(Registries.ATTRIBUTE, new ResourceLocation(id)))
                .orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id)).value();
    }

    public static AttributeInstance ensure(LivingEntity entity, String id) {
        Attribute attribute = attribute(id);
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            var access = (NativeAttributeAccess) entity.getAttributes();
            instance = new AttributeInstance(attribute, access::cannedmonster$modified);
            access.cannedmonster$instances().put(attribute, instance);
        }
        return instance;
    }

    public static boolean has(LivingEntity entity, String id) {
        return entity.getAttribute(attribute(id)) != null;
    }

    public static void remove(LivingEntity entity, String id) {
        ((NativeAttributeAccess) entity.getAttributes()).cannedmonster$instances().remove(attribute(id));
    }

    public static void modified(LivingEntity entity, AttributeInstance instance) {
        ((NativeAttributeAccess) entity.getAttributes()).cannedmonster$modified(instance);
    }

    public static AttributeInstance speed(LivingEntity entity) {
        return entity.getAttribute(Attributes.MOVEMENT_SPEED);
    }

    public static void removeSpeed(AttributeInstance instance) {
        instance.removeModifier(SPEED_MODIFIER);
    }

    public static void addSpeed(AttributeInstance instance, double amount) {
        instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, "cannedmonster:fusion_speed", amount,
                AttributeModifier.Operation.ADDITION));
    }

    public static boolean aquatic(Mob member) {
        return member instanceof net.minecraft.world.entity.animal.WaterAnimal;
    }
}
