package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.platform.AttributeAccess;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerSpeed {
    static final String ATTRIBUTE = AttributeAccess.MOVEMENT_SPEED;
    private static final double EPSILON = 1.0E-9;
    private static final Map<Player, double[]> EQUIVALENTS = Collections.synchronizedMap(new IdentityHashMap<>());

    private PlayerSpeed() {}

    public static void apply(Player player, List<Mob> members, List<Map<String, Double>> origins) {
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
        AttributeInstance instance = AttributeAccess.speed(player);
        double[] equivalents = EQUIVALENTS.get(player);
        if (instance == null) return;
        AttributeAccess.removeSpeed(instance);
        if (equivalents == null) return;
        double base = instance.getBaseValue();
        double target = target(base, equivalents);
        if (Math.abs(target - base) > EPSILON) AttributeAccess.addSpeed(instance, target - base);
    }

    public static void clear(Player player) {
        EQUIVALENTS.remove(player);
        AttributeInstance instance = AttributeAccess.speed(player);
        if (instance != null) AttributeAccess.removeSpeed(instance);
    }

    static boolean supported(Mob member) {
        if (AttributeAccess.aquatic(member)
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

    private static void migrate(Player player, List<Map<String, Double>> origins) {
        AttributeInstance instance = AttributeAccess.speed(player);
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
