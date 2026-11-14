package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;
import java.util.function.Predicate;

public final class FusionTraits {
    private static final ThreadLocal<Boolean> QUERY = ThreadLocal.withInitial(() -> false);

    private FusionTraits() {}

    private static List<Mob> members(LivingEntity body) {
        return body.level().isClientSide
                ? cc.sighs.cannedmonster.fusion.visual.ClientAppearance.members(body) : FusionRuntime.facets(body);
    }

    private static boolean any(Entity entity, Predicate<Mob> query) {
        if (!(entity instanceof Player player) || BodyLinks.isFacet(entity) || QUERY.get()) return false;
        QUERY.set(true);
        try {
            for (Mob member : members(player)) if (query.test(member)) return true;
            return false;
        } finally { QUERY.remove(); }
    }

    public static boolean fireImmune(Entity entity, boolean original) {
        return original || any(entity, Mob::fireImmune);
    }

    public static boolean underwater(LivingEntity entity, boolean original) {
        return original || any(entity, Mob::canBreatheUnderwater);
    }

    public static boolean canDrown(LivingEntity entity, FluidType type, boolean original) {
        return original && !any(entity, member -> !member.canDrownInFluidType(type));
    }

    public static boolean allowedTarget(Player player, Entity target) {
        return target instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                && living.level() == player.level() && !BodyLinks.same(player, living)
                && player.getLastHurtMob() == living && player.tickCount - player.getLastHurtMobTimestamp() <= 100
                && player.canAttack(living) && (!(living instanceof Player other) || player.canHarmPlayer(other));
    }

    public static boolean allowMemberAttack(Entity member, Entity target) {
        return !(BodyLinks.body(member) instanceof Player player) || member == player || allowedTarget(player, target);
    }

    public static boolean invulnerable(Entity entity, net.minecraft.world.damagesource.DamageSource source, boolean original) {
        return original || any(entity, member -> member.isInvulnerableTo(source));
    }

    public static boolean affected(LivingEntity entity, net.minecraft.world.effect.MobEffectInstance effect, boolean original) {
        return original && !any(entity, member -> !member.canBeAffected(effect));
    }

    public static boolean playerMember(Entity entity) {
        return BodyLinks.isFacet(entity) && BodyLinks.body(entity) instanceof Player;
    }

    public static boolean autonomous(Entity entity) {
        Object current = OrderedCalls.currentMember();
        return current instanceof Mob member && playerMember(member) && BodyLinks.same(member, entity);
    }
}
