package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionLoot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class FusionLootMixin {
    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;shouldDropLoot()Z"))
    private boolean cannedmonster$eligible(LivingEntity body) {
        return FusionLoot.eligible(body);
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment()V"))
    private void cannedmonster$equipment(LivingEntity body) {
        FusionLoot.equipment(body);
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V"))
    private void cannedmonster$tables(LivingEntity body, DamageSource source, boolean playerKill) {
        FusionLoot.tables(body, source, playerKill);
    }

    @Redirect(method = "dropAllDeathLoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropCustomDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V"))
    private void cannedmonster$custom(LivingEntity body, ServerLevel level, DamageSource source, boolean playerKill) {
        FusionLoot.custom(body, level, source, playerKill);
    }
}
