package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.platform.EntityStorage;
import cc.sighs.cannedmonster.platform.PlayerAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CanActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CanActions() {}

    public static boolean onMob(ItemStack stack, Mob target, Player actor) {
        if (!(stack.getItem() instanceof MonsterCanItem item) || !item.storesMobs()
                || !(target.level() instanceof ServerLevel level) || BodyLinks.isFacet(target)
                || !target.isAlive() || target.isRemoved() || target.isPassenger() || target.isVehicle() || target.isLeashed()) return false;
        if (cc.sighs.cannedmonster.space.SpaceManager.spaceOf(target) != null) return false;
        try {
            if (!CanContents.filled(stack)) {
                if (!FusionRuntime.facets(target).isEmpty() && !item.storesFusion()) return peel(stack, target, actor);
                CompoundTag data = EntityStorage.save(target);
                CompoundTag contents = CanContents.build(data, target.getName().getString(), FusionRuntime.memberCount(data));
                target.setRemoved(Entity.RemovalReason.DISCARDED);
                CanContents.write(stack, contents);
                return true;
            }
            Mob captured = restore(stack, level);
            if (captured == null) return false;
            try {
                if (captured.getUUID().equals(target.getUUID())) return false;
                FusionRuntime.prepend(target, captured);
                CanContents.clear(stack);
                return true;
            } finally {
                if (!BodyLinks.isFacet(captured)) FusionRuntime.detach(captured);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Cannot apply monster can to {}: {}", target.getType(), failure.toString());
            message(actor, "message.cannedmonster.failed");
            return false;
        }
    }

    private static boolean peel(ItemStack stack, Mob target, Player actor) {
        Mob member = FusionRuntime.facets(target).get(0);
        CompoundTag contents = CanContents.build(FusionRuntime.independentSave(member), member.getName().getString(), 1);
        FusionRuntime.peel(target);
        CanContents.write(stack, contents);
        message(actor, "message.cannedmonster.peeled", member.getName());
        return true;
    }

    public static boolean release(ItemStack stack, ServerLevel level, Vec3 position) {
        Mob mob = null;
        boolean added = false;
        try {
            mob = restore(stack, level);
            if (mob == null) return false;
            EntityStorage.position(mob, position, mob.getXRot());
            mob.setDeltaMovement(Vec3.ZERO);
            if (!level.getWorldBorder().isWithinBounds(mob.getBoundingBox()) || !level.noCollision(mob)) return false;
            added = level.addFreshEntity(mob);
            if (!added) return false;
            CanContents.clear(stack);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.warn("Cannot release monster can: {}", failure.toString());
            return false;
        } finally {
            if (mob != null && !added) FusionRuntime.detach(mob);
        }
    }

    public static Mob restore(ItemStack stack, ServerLevel level) {
        if (!CanContents.valid(stack)) return null;
        CompoundTag data = CanContents.entity(stack);
        UUID identity = EntityStorage.identity(data);
        if (identity == null) return null;
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(identity) != null) return null;
        }
        Entity created = EntityStorage.create(EntityStorage.string(data, "id"), level);
        if (!(created instanceof Mob mob)) return null;
        try {
            EntityStorage.load(mob, data);
            if (!mob.isAlive() || mob.isRemoved()) { FusionRuntime.detach(mob); return null; }
            return mob;
        } catch (RuntimeException failure) {
            FusionRuntime.detach(mob);
            throw failure;
        }
    }

    public static boolean randomSpawn(ServerLevel level, Vec3 position) {
        List<EntityType<?>> candidates = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.canSummon() && type.getCategory() != MobCategory.MISC) candidates.add(type);
        }
        for (int attempt = 0; attempt < 16 && !candidates.isEmpty(); attempt++) {
            EntityType<?> type = candidates.remove(level.getRandom().nextInt(candidates.size()));
            Entity entity = null;
            try {
                entity = EntityStorage.spawn(type, level, position);
                if (!(entity instanceof Mob mob)) continue;
                EntityStorage.position(mob, position, 0);
                if (mob.isRemoved() || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox()) || !level.noCollision(mob)) continue;
                if (level.addFreshEntity(mob)) return true;
            } catch (RuntimeException failure) {
                LOGGER.debug("Random can skipped {}: {}", type, failure.toString());
            } finally {
                if (entity instanceof Mob mob && !EntityStorage.added(mob)) FusionRuntime.detach(mob);
            }
        }
        return false;
    }

    private static void message(Player player, String key, Object... args) {
        if (player != null) PlayerAccess.message(player, net.minecraft.network.chat.Component.translatable(key, args));
    }
}
