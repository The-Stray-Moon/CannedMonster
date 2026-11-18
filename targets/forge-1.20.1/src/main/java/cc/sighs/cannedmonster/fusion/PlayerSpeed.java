package cc.sighs.cannedmonster.fusion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player speed is not the raw movement-speed attribute of a mob. Standard ground mobs feed the
 * movement control value back into setSpeed, so their per-tick drive scales with the attribute
 * squared; the player's own input is normalized and scales only with the attribute. Keep the
 * player's base untouched and expose the fused difference as one removable modifier.
 */
public final class PlayerSpeed {
    static final ResourceLocation ATTRIBUTE = ResourceLocation.tryParse("minecraft:generic.movement_speed");
    static final UUID MODIFIER = UUID.fromString("6d0f6c9e-4ea4-4f57-9b30-71c9f9a1c5a4");
    private static final String MODIFIER_NAME = "cannedmonster:fusion_speed";
    private static final double EPSILON = 1.0E-9;
    private static final Map<Player, double[]> EQUIVALENTS = Collections.synchronizedMap(new IdentityHashMap<>());

    private PlayerSpeed() {}

    public static void apply(Player player, List<Mob> members, List<Map<ResourceLocation, Double>> origins) {
        AttributeAverages.track(player);
        migrate(player, origins);
        double[] equivalents = new double[members.size()];
        for (int i = 0; i < members.size(); i++) {
            Double value = origins.get(i).get(ATTRIBUTE);
            equivalents[i] = supported(members.get(i)) && value != null ? equivalentGround(value) : Double.NaN;
        }
        EQUIVALENTS.put(player, equivalents);
        refresh(player);
    }

    public static void refresh(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double[] equivalents = EQUIVALENTS.get(player);
        if (instance == null || equivalents == null) {
            if (instance != null) instance.removeModifier(MODIFIER);
            return;
        }
        double base = instance.getBaseValue();
        double sum = base;
        for (double equivalent : equivalents) sum += Double.isNaN(equivalent) ? base : equivalent;
        double target = sum / (equivalents.length + 1);
        instance.removeModifier(MODIFIER);
        if (Math.abs(target - base) > EPSILON) {
            instance.addTransientModifier(new AttributeModifier(MODIFIER, MODIFIER_NAME, target - base,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    public static void clear(Player player) {
        EQUIVALENTS.remove(player);
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null) instance.removeModifier(MODIFIER);
    }

    static boolean supported(Mob member) {
        if (member instanceof net.minecraft.world.entity.animal.WaterAnimal
                || member.getNavigation() == null || member.getNavigation().getClass() != GroundPathNavigation.class
                || member.getMoveControl() == null || member.getMoveControl().getClass() != MoveControl.class) return false;
        try {
            return member.getClass().getMethod("travel", net.minecraft.world.phys.Vec3.class)
                    .getDeclaringClass() == net.minecraft.world.entity.LivingEntity.class;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    static double equivalentGround(double attribute) {
        return attribute * attribute;
    }

    static double target(double base, double[] equivalents) {
        double sum = base;
        for (double equivalent : equivalents) sum += Double.isNaN(equivalent) ? base : equivalent;
        return sum / (equivalents.length + 1);
    }

    static boolean needsMigration(double current, double own) {
        return Math.abs(current - own) > EPSILON;
    }

    static boolean matchesMean(double current, double mean) {
        return Math.abs(current - mean) < EPSILON;
    }

    private static void migrate(Player player, List<Map<ResourceLocation, Double>> origins) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null || origins.isEmpty()) return;
        Double own = origins.get(origins.size() - 1).get(ATTRIBUTE);
        Double mean = AttributeAverages.mean(origins).get(ATTRIBUTE);
        if (own == null || mean == null) return;
        double current = instance.getBaseValue();
        if (needsMigration(current, own) && matchesMean(current, mean)) {
            AttributeAverages.quiet(() -> instance.setBaseValue(own));
        }
    }
}
