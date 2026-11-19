package cc.sighs.cannedmonster.can;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;

public final class ThrownCan extends ThrowableItemProjectile {
    private final CanProjectile state = new CanProjectile(this);

    public ThrownCan(EntityType<? extends ThrownCan> type, Level level) { super(type, level); }

    @Override
    protected Item getDefaultItem() { return Items.IRON_INGOT; }

    @Override
    protected void onHit(HitResult hit) { super.onHit(hit); state.hit(hit); }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("ReturningCan", state.returning());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        state.restore(input.getBooleanOr("ReturningCan", false));
    }

    @Override
    public void tick() {
        if (state.beforeTick()) return;
        super.tick();
        state.afterTick();
    }
}
