package cc.sighs.cannedmonster.can;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public final class ThrownCan extends ThrowableItemProjectile {
    private final CanProjectile state = new CanProjectile(this);

    public ThrownCan(EntityType<? extends ThrownCan> type, Level level) { super(type, level); }

    @Override
    protected Item getDefaultItem() { return Items.IRON_INGOT; }

    @Override
    protected void onHit(HitResult hit) { super.onHit(hit); state.hit(hit); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ReturningCan", state.returning());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state.restore(tag.getBoolean("ReturningCan"));
    }

    @Override
    public void tick() {
        if (state.beforeTick()) return;
        super.tick();
        state.afterTick();
    }
}
