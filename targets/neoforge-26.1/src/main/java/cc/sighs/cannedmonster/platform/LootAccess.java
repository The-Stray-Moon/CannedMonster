package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.NativeLootAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class LootAccess {
    private LootAccess() {}
    public static boolean eligible(ServerLevel level, LivingEntity member) {
        return ((NativeLootAccess) member).cannedmonster$shouldDropLoot(level);
    }
    public static void equipment(ServerLevel level, LivingEntity member) {
        ((NativeLootAccess) member).cannedmonster$equipmentLoot(level);
    }
    public static void table(ServerLevel level, LivingEntity member, DamageSource source, boolean playerKill) {
        ((NativeLootAccess) member).cannedmonster$lootTable(level, source, playerKill);
    }
    public static void custom(ServerLevel level, LivingEntity member, DamageSource source, boolean playerKill) {
        ((NativeLootAccess) member).cannedmonster$customLoot(level, source, playerKill);
    }
}
