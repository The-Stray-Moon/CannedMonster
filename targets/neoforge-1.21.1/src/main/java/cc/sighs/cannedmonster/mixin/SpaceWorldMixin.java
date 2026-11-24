package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceDomain;
import cc.sighs.cannedmonster.space.VirtualChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Level.class)
public abstract class SpaceWorldMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$virtualBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = SpaceDomain.blockState((Level) (Object) this, pos);
        if (state != null) cir.setReturnValue(state);
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$virtualFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        FluidState state = SpaceDomain.fluidState((Level) (Object) this, pos);
        if (state != null) cir.setReturnValue(state);
    }

    /** The room is roofed: precipitation inside it must not damage or extinguish entities. */
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$roofedRain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDomain.roofed((Level) (Object) this, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$virtualBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (SpaceDomain.active((Level) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "getChunkForCollisions", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$virtualCollisionChunk(int chunkX, int chunkZ, CallbackInfoReturnable<BlockGetter> cir) {
        SpaceDomain.Frame frame = SpaceDomain.frame();
        if (frame.active() && frame.level() == (Level) (Object) this) {
            cir.setReturnValue(new VirtualChunk(frame.level(), frame.room()));
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$denySetBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDomain.active((Level) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$denySetBlockFull(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDomain.active((Level) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "removeBlock", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$denyRemoveBlock(BlockPos pos, boolean moving, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceDomain.active((Level) (Object) this)) cir.setReturnValue(false);
    }
}
