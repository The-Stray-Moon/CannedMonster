package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class FusionEquipmentLootMixin {
    @Inject(method = "dropCustomDeathLoot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$equipmentOnce(ServerLevel level, DamageSource source, boolean playerKill, CallbackInfo ci) {
        if (BodyLinks.isFacet(this)) ci.cancel();
    }
}
