package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.NativeAttributeAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;
import java.util.function.Consumer;

public final class AttributeAccess {
    public static final String MOVEMENT_SPEED = "minecraft:movement_speed";
    private static final Identifier SPEED_MODIFIER = Identifier.fromNamespaceAndPath("cannedmonster", "fusion_speed");

    private AttributeAccess() {}

    public static void each(LivingEntity entity, Consumer<AttributeInstance> action) {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            AttributeInstance instance = entity.getAttribute(holder);
            if (instance != null) action.accept(instance);
        });
    }

    public static String id(AttributeInstance instance) {
        return instance.getAttribute().unwrapKey().orElseThrow().identifier().toString();
    }

    public static String normalize(String value) {
        Identifier id = Identifier.parse(value);
        if (BuiltInRegistries.ATTRIBUTE.get(id).isPresent()) return id.toString();
        String path = id.getPath();
        int dot = path.lastIndexOf('.');
        return dot < 0 ? id.toString() : Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(dot + 1)).toString();
    }

    public static void validate(String id) {
        attribute(id);
    }

    private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute(String id) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(normalize(id)))
                .orElseThrow(() -> new IllegalArgumentException("Missing fusion attribute: " + id));
    }

    public static AttributeInstance ensure(LivingEntity entity, String id) {
        var holder = attribute(id);
        AttributeInstance instance = entity.getAttribute(holder);
        if (instance == null) {
            var access = (NativeAttributeAccess) entity.getAttributes();
            instance = new AttributeInstance(holder, access::cannedmonster$modified);
            access.cannedmonster$instances().put(holder, instance);
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
        instance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    public static boolean aquatic(Mob member) {
        return member instanceof net.minecraft.world.entity.animal.fish.WaterAnimal;
    }
}
