package cc.sighs.cannedmonster.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * BlockGetter returned by the server collision path for an entity inside a
 * virtual room. It never falls back to the real chunk behind the coordinate.
 */
public final class VirtualChunk implements BlockGetter {
    private final Level level;
    private final RoomGeometry room;

    public VirtualChunk(Level level, RoomGeometry room) {
        this.level = level;
        this.room = room;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return room.blockAt(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
