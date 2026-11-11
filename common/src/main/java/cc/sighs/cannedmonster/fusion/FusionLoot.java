package cc.sighs.cannedmonster.fusion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

public final class FusionLoot {
    private FusionLoot() {}

    private static List<? extends LivingEntity> members(LivingEntity entity) {
        if (!(entity instanceof Mob body) || BodyLinks.isFacet(body)) return List.of(entity);
        List<LivingEntity> members = new ArrayList<>(FusionRuntime.facets(body));
        members.add(body);
        return members;
    }

    public static void equipment(LivingEntity body) {
        for (LivingEntity member : members(body)) ((NativeLootAccess) member).cannedmonster$equipmentLoot();
    }

    public static boolean eligible(LivingEntity body) {
        return members(body).stream().anyMatch(member -> ((NativeLootAccess) member).cannedmonster$shouldDropLoot());
    }

    public static void tables(LivingEntity body, DamageSource source, boolean playerKill) {
        var members = members(body);
        for (LivingEntity member : members) {
            NativeLootAccess access = (NativeLootAccess) member;
            if (members.size() == 1 || access.cannedmonster$shouldDropLoot()) access.cannedmonster$lootTable(source, playerKill);
        }
    }

    public static void custom(LivingEntity body, ServerLevel level, DamageSource source, boolean playerKill) {
        var members = members(body);
        for (LivingEntity member : members) {
            NativeLootAccess access = (NativeLootAccess) member;
            if (members.size() == 1 || access.cannedmonster$shouldDropLoot()) access.cannedmonster$customLoot(level, source, playerKill);
        }
    }
}
