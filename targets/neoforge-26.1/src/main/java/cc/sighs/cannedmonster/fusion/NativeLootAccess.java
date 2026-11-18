package cc.sighs.cannedmonster.fusion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

public interface NativeLootAccess {
    boolean cannedmonster$shouldDropLoot(net.minecraft.server.level.ServerLevel level);
    void cannedmonster$equipmentLoot(ServerLevel level);
    void cannedmonster$lootTable(ServerLevel level, DamageSource source, boolean playerKill);
    void cannedmonster$customLoot(ServerLevel level, DamageSource source, boolean playerKill);
}
