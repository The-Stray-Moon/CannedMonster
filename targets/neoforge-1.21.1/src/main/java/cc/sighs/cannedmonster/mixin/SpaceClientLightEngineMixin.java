package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Values read through the light engine (terrain light, entity light, sky checks)
 * inside the room come from the room itself. This covers both the default
 * {@code BlockAndTintGetter.getBrightness} and {@code LevelLightEngine.getRawBrightness}
 * paths, and works for RenderChunkRegion as well as the ClientLevel itself.
 */
@Mixin(LightEngine.class)
public abstract class SpaceClientLightEngineMixin {
    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomLightValue(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!SpaceClientView.active()) return;
        if (!SpaceClientView.room().contains(pos)) return;
        cir.setReturnValue((Object) this instanceof SkyLightEngine ? 0 : 14);
    }
}
