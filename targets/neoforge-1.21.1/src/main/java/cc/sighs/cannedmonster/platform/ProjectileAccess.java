package cc.sighs.cannedmonster.platform;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ProjectileAccess {
    private ProjectileAccess() {}
    public static int minY(Level level) { return level.getMinBuildHeight(); }
    public static void position(Entity entity, Vec3 position) { entity.setPos(position.x, position.y, position.z); }
}
