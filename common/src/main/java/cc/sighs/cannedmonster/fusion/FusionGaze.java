package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resolves the living entity under the player's crosshair. The hit is limited by blocks and cached
 * per player tick so that every member boundary and attack guard sees the same target.
 */
public final class FusionGaze {
    public static final double RANGE = 64.0;
    private static final Map<Player, Entry> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private record Entry(int tick, Level level, LivingEntity target) {}

    private FusionGaze() {
    }

    public static LivingEntity target(Player player) {
        if (player.level().isClientSide || player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        Entry cached = CACHE.get(player);
        if (cached != null && cached.tick == player.tickCount && cached.level == player.level()) {
            return cached.target;
        }
        LivingEntity target = scan(player);
        CACHE.put(player, new Entry(player.tickCount, player.level(), target));
        return target;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static LivingEntity scan(Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
        HitResult block = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, area,
                entity -> candidate(player, entity), 0.3F);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean candidate(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive() || living.isRemoved()
                || !living.isPickable() || BodyLinks.same(player, living)) {
            return false;
        }
        if (living.getRootVehicle() == player.getRootVehicle()) {
            return false;
        }
        return player.canAttack(living)
                && (!(living instanceof Player other) || player.canHarmPlayer(other));
    }
}
