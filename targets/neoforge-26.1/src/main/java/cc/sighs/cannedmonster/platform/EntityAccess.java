package cc.sighs.cannedmonster.platform;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public final class EntityAccess {
    private EntityAccess() {}

    public static boolean clientSide(Level level) {
        return level.isClientSide();
    }

    public static boolean flyingBase(Mob member) {
        return false;
    }

    public static void markMotion(Entity entity) {
        entity.hurtMarked = true;
    }

    public static boolean invulnerable(Mob member, DamageSource source) {
        return member.level() instanceof ServerLevel level && member.isInvulnerableTo(level, source);
    }
}
