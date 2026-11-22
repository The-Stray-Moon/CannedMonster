package cc.sighs.cannedmonster.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of a virtual room. It is never written into the world; the server
 * answers block/collision queries from this geometry and the client receives a
 * synthetic chunk for the client-side room.
 */
public final class RoomGeometry {
    public static final int SIZE_X = 13;
    public static final int SIZE_Y = 8;
    public static final int SIZE_Z = 13;
    public static final int INTERIOR_MIN = 2;
    public static final int INTERIOR_MAX = 10;
    public static final int INTERIOR_TOP = 5;

    private final double x;
    private final double y;
    private final double z;

    public RoomGeometry(double x, double y, double z) {
        this.x = Math.floor(x);
        this.y = Math.floor(y);
        this.z = Math.floor(z);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }

    public boolean contains(double px, double py, double pz) {
        return px >= x && px < x + SIZE_X && py >= y && py < y + SIZE_Y && pz >= z && pz < z + SIZE_Z;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(Vec3 position) {
        return contains(position.x, position.y, position.z);
    }

    public boolean interior(double px, double py, double pz) {
        return px >= x + INTERIOR_MIN && px < x + INTERIOR_MAX + 1
                && py >= y + INTERIOR_MIN && py < y + INTERIOR_TOP + 1
                && pz >= z + INTERIOR_MIN && pz < z + INTERIOR_MAX + 1;
    }

    public BlockState blockAt(BlockPos pos) {
        int lx = pos.getX() - (int) x;
        int ly = pos.getY() - (int) y;
        int lz = pos.getZ() - (int) z;
        if (!inside(lx, ly, lz)) return Blocks.VOID_AIR.defaultBlockState();
        return blockLocal(lx, ly, lz);
    }

    public BlockState blockLocal(int lx, int ly, int lz) {
        if (lx == 0 || lx == SIZE_X - 1 || ly == 0 || ly == SIZE_Y - 1 || lz == 0 || lz == SIZE_Z - 1) {
            return Blocks.BARRIER.defaultBlockState();
        }
        if (lx == 1 || lx == SIZE_X - 2 || ly == 1 || ly == SIZE_Y - 2 || lz == 1 || lz == SIZE_Z - 2) {
            if (ly == INTERIOR_MIN - 1) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
            if (ly == INTERIOR_TOP + 1) {
                return (lx % 4 == 3 && lz % 4 == 3) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
            }
            return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    private boolean inside(int lx, int ly, int lz) {
        return lx >= 0 && lx < SIZE_X && ly >= 0 && ly < SIZE_Y && lz >= 0 && lz < SIZE_Z;
    }

    public Vec3 spawn() {
        return new Vec3(x + 6.5, y + INTERIOR_MIN, z + 6.5);
    }

    public Vec3 center() {
        return new Vec3(x + SIZE_X / 2.0, y + SIZE_Y / 2.0, z + SIZE_Z / 2.0);
    }

    public RoomGeometry offset(double dx, double dy, double dz) {
        return new RoomGeometry(x + dx, y + dy, z + dz);
    }

    public ChunkBounds chunkBounds() {
        return new ChunkBounds((int) x >> 4, ((int) x + SIZE_X - 1) >> 4,
                (int) z >> 4, ((int) z + SIZE_Z - 1) >> 4);
    }

    public record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {}
}
