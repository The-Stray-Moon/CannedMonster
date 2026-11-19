package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.platform.LootAccess;
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
        equipment((ServerLevel) body.level(), body);
    }

    public static void equipment(ServerLevel level, LivingEntity body) {
        for (LivingEntity member : members(body)) LootAccess.equipment(level, member);
    }

    public static boolean eligible(LivingEntity body) {
        return eligible((ServerLevel) body.level(), body);
    }

    public static boolean eligible(ServerLevel level, LivingEntity body) {
        return members(body).stream().anyMatch(member -> LootAccess.eligible(level, member));
    }

    public static void tables(LivingEntity body, DamageSource source, boolean playerKill) {
        tables((ServerLevel) body.level(), body, source, playerKill);
    }

    public static void tables(ServerLevel level, LivingEntity body, DamageSource source, boolean playerKill) {
        var members = members(body);
        for (LivingEntity member : members) {
            if (members.size() == 1 || LootAccess.eligible(level, member)) LootAccess.table(level, member, source, playerKill);
        }
    }

    public static void custom(LivingEntity body, ServerLevel level, DamageSource source, boolean playerKill) {
        var members = members(body);
        for (LivingEntity member : members) {
            if (members.size() == 1 || LootAccess.eligible(level, member)) LootAccess.custom(level, member, source, playerKill);
        }
    }
}
