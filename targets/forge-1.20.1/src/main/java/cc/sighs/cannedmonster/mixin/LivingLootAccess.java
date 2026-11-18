package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeLootAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public abstract class LivingLootAccess implements NativeLootAccess {
    @Override
    @Invoker("shouldDropLoot")
    public abstract boolean cannedmonster$shouldDropLoot();

    @Override
    @Invoker("dropEquipment")
    public abstract void cannedmonster$equipmentLoot();

    @Override
    @Invoker("dropFromLootTable")
    public abstract void cannedmonster$lootTable(DamageSource source, boolean playerKill);

    @Override
    public void cannedmonster$customLoot(ServerLevel level, DamageSource source, boolean playerKill) {
        LivingEntity body = (LivingEntity) (Object) this;
        cannedmonster$dropCustomLoot(source, ForgeHooks.getLootingLevel(body, source.getEntity(), source), playerKill);
    }

    @Invoker("dropCustomDeathLoot")
    public abstract void cannedmonster$dropCustomLoot(DamageSource source, int looting, boolean playerKill);
}
