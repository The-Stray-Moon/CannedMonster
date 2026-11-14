package cc.sighs.cannedmonster.can;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ThrownCan extends ThrowableItemProjectile {
    private boolean settled;

    public ThrownCan(EntityType<? extends ThrownCan> type, Level level) { super(type, level); }

    @Override
    protected Item getDefaultItem() { return Items.IRON_INGOT; }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel server) || settled || isRemoved()) return;
        settled = true;
        ItemStack returned = getItem().copyWithCount(1);
        boolean consumed = false;
        if (returned.getItem() instanceof MonsterCanItem can) {
            if (hit instanceof EntityHitResult entityHit && !can.random()) {
                Player actor = getOwner() instanceof Player player ? player : null;
                if (entityHit.getEntity() instanceof Player player) {
                    if (CanContents.filled(returned)) cc.sighs.cannedmonster.fusion.PlayerFusion.fuse(player, returned);
                    else cc.sighs.cannedmonster.fusion.PlayerFusion.extract(player, returned);
                } else if (entityHit.getEntity() instanceof Mob mob) {
                    CanActions.onMob(returned, mob, actor);
                }
            } else if (hit instanceof BlockHitResult blockHit) {
                Vec3 position = Vec3.atBottomCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()));
                if (can.random()) {
                    consumed = CanActions.randomSpawn(server, position);
                } else if (CanContents.filled(returned)) {
                    CanActions.release(returned, server, position);
                }
            }
        }
        if (consumed) discard();
        else returnItem(returned, hit.getLocation());
    }

    private boolean returning;

    private void returnItem(ItemStack stack, Vec3 position) {
        setItem(stack);
        setPos(position.x, Math.max(level().getMinBuildHeight() + 1, position.y), position.z);
        if (drop(stack, position())) {
            discard();
        } else {
            returning = true;
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean drop(ItemStack stack, Vec3 position) {
        ItemEntity dropped = new ItemEntity(level(), position.x, Math.max(level().getMinBuildHeight() + 1, position.y + 0.1), position.z, stack.copy());
        dropped.setDefaultPickUpDelay();
        return level().addFreshEntity(dropped);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ReturningCan", returning);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        returning = tag.getBoolean("ReturningCan");
        settled = returning;
    }

    @Override
    public void tick() {
        if (returning) {
            if (!level().isClientSide && tickCount % 20 == 0 && drop(getItem(), position())) discard();
            return;
        }
        super.tick();
        if (!level().isClientSide && !settled && !isRemoved() && (tickCount >= 1200 || getY() < level().getMinBuildHeight())) {
            settled = true;
            returnItem(getItem().copyWithCount(1), position());
        }
    }
}
