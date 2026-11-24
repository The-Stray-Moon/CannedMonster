package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Biome sampling while the room is active resolves to plains. Fog, water fog and
 * block tint therefore cannot come from the real world; the synthetic room chunk
 * carries the same biome.
 */
@Mixin(BiomeManager.class)
public abstract class SpaceClientBiomeMixin {
    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!SpaceClientView.active()) return;
        Holder<Biome> biome = SpaceClientView.viewBiome();
        if (biome != null) cir.setReturnValue(biome);
    }

    @Inject(method = "getNoiseBiomeAtQuart", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roomNoiseBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!SpaceClientView.active()) return;
        Holder<Biome> biome = SpaceClientView.viewBiome();
        if (biome != null) cir.setReturnValue(biome);
    }
}
