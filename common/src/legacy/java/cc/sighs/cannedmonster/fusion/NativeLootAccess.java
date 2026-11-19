package cc.sighs.cannedmonster.fusion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

public interface NativeLootAccess {
    boolean cannedmonster$shouldDropLoot();
    void cannedmonster$equipmentLoot();
    void cannedmonster$lootTable(DamageSource source, boolean playerKill);
    void cannedmonster$customLoot(ServerLevel level, DamageSource source, boolean playerKill);
}
