package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeLootAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingLootAccess extends NativeLootAccess {
    @Override
    @Invoker("shouldDropLoot")
    boolean cannedmonster$shouldDropLoot(ServerLevel level);

    @Override
    @Invoker("dropEquipment")
    void cannedmonster$equipmentLoot(ServerLevel level);

    @Override
    @Invoker("dropFromLootTable")
    void cannedmonster$lootTable(ServerLevel level, DamageSource source, boolean playerKill);

    @Override
    @Invoker("dropCustomDeathLoot")
    void cannedmonster$customLoot(ServerLevel level, DamageSource source, boolean playerKill);
}
