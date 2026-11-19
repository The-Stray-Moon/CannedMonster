package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.platform.ProjectileAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CanProjectile {
    private final ThrownCan entity;
    private boolean settled;
    private boolean returning;

    public CanProjectile(ThrownCan entity) { this.entity = entity; }

    public boolean returning() { return returning; }
    public void restore(boolean returning) { this.returning = returning; settled = returning; }

    public void hit(HitResult hit) {
        if (!(entity.level() instanceof ServerLevel server) || settled || entity.isRemoved()) return;
        settled = true;
        ItemStack returned = entity.getItem().copyWithCount(1);
        boolean consumed = false;
        if (returned.getItem() instanceof MonsterCanItem can) {
            if (hit instanceof EntityHitResult entityHit && !can.random()) {
                Player actor = entity.getOwner() instanceof Player player ? player : null;
                if (entityHit.getEntity() instanceof Player player) {
                    if (CanContents.filled(returned)) cc.sighs.cannedmonster.fusion.PlayerFusion.fuse(player, returned);
                    else cc.sighs.cannedmonster.fusion.PlayerFusion.extract(player, returned);
                } else if (entityHit.getEntity() instanceof Mob mob) CanActions.onMob(returned, mob, actor);
            } else if (hit instanceof BlockHitResult blockHit) {
                Vec3 position = Vec3.atBottomCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()));
                if (can.random()) consumed = CanActions.randomSpawn(server, position);
                else if (CanContents.filled(returned)) CanActions.release(returned, server, position);
            }
        }
        if (consumed) entity.discard();
        else returnItem(returned, hit.getLocation());
    }

    private void returnItem(ItemStack stack, Vec3 position) {
        entity.setItem(stack);
        ProjectileAccess.position(entity, new Vec3(position.x, Math.max(ProjectileAccess.minY(entity.level()) + 1, position.y), position.z));
        if (drop(stack, entity.position())) entity.discard();
        else {
            returning = true;
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean drop(ItemStack stack, Vec3 position) {
        ItemEntity dropped = new ItemEntity(entity.level(), position.x,
                Math.max(ProjectileAccess.minY(entity.level()) + 1, position.y + 0.1), position.z, stack.copy());
        dropped.setDefaultPickUpDelay();
        return entity.level().addFreshEntity(dropped);
    }

    public boolean beforeTick() {
        if (!returning) return false;
        if (!entity.level().isClientSide() && entity.tickCount % 20 == 0 && drop(entity.getItem(), entity.position())) entity.discard();
        return true;
    }

    public void afterTick() {
        if (!entity.level().isClientSide() && !settled && !entity.isRemoved()
                && (entity.tickCount >= 1200 || entity.getY() < ProjectileAccess.minY(entity.level()))) {
            settled = true;
            returnItem(entity.getItem().copyWithCount(1), entity.position());
        }
    }
}
