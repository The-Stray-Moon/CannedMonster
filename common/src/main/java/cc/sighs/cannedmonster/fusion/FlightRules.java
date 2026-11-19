package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import cc.sighs.cannedmonster.platform.EntityAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Flight is not a vanilla capability flag. Use the shared flight infrastructure plus an explicit
 * extra list for species with custom movement (Vex, Bat, EnderDragon by default).
 */
public final class FlightRules {
    public static final Set<String> DEFAULT_EXTRA = Set.of(
            "minecraft:vex", "minecraft:bat", "minecraft:ender_dragon");
    private static volatile Set<String> extra = DEFAULT_EXTRA;
    private static final Set<Player> APPLIED = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    private FlightRules() {}

    public static void setExtraTypes(Collection<String> types) {
        extra = Set.copyOf(types);
        FusionRuntime.refreshFlights();
    }

    public static Set<String> extraTypes() {
        return extra;
    }

    public static boolean structurallyFlies(Mob mob) {
        return EntityAccess.flyingBase(mob)
                || mob instanceof FlyingAnimal
                || mob.getMoveControl() instanceof FlyingMoveControl
                || mob.getNavigation() instanceof FlyingPathNavigation
                || mob.getAttributes().hasAttribute(Attributes.FLYING_SPEED);
    }

    public static boolean flies(Mob mob) {
        return structurallyFlies(mob) || extra.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
    }

    public static boolean hasFlight(LivingEntity body) {
        for (Mob member : FusionRuntime.facets(body)) {
            if (flies(member)) return true;
        }
        return false;
    }

    public static boolean applied(Player player) {
        return APPLIED.contains(player);
    }

    public static void refresh(Player player) {
        if (hasFlight(player)) {
            APPLIED.add(player);
            player.setNoGravity(true);
        } else if (APPLIED.remove(player)) {
            player.setNoGravity(false);
        }
    }

    public static void clear(Player player) {
        if (APPLIED.remove(player)) player.setNoGravity(false);
    }

    public static void clearAll() {
        List<Player> players;
        synchronized (APPLIED) {
            players = new ArrayList<>(APPLIED);
        }
        players.forEach(FlightRules::clear);
    }
}
