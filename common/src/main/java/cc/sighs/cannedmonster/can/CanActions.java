package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class CanActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CanActions() {
    }

    public static boolean onMob(ItemStack stack, Mob target, Player actor) {
        if (!(stack.getItem() instanceof MonsterCanItem item) || !item.storesMobs()
                || !(target.level() instanceof ServerLevel level) || BodyLinks.isFacet(target)
                || !target.isAlive() || target.isRemoved() || target.isPassenger() || target.isVehicle() || target.isLeashed()) return false;
        try {
            if (!CanContents.filled(stack)) {
                if (!item.storesFusion() && !FusionRuntime.facets(target).isEmpty()) {
                    message(actor, "message.cannedmonster.large_required");
                    return false;
                }
                CompoundTag data = target.saveWithoutId(new CompoundTag());
                data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
                ItemStack prepared = stack.copyWithCount(1);
                CanContents.store(prepared, data, target.getName().getString());
                target.setRemoved(Entity.RemovalReason.DISCARDED);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, prepared.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
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

    public static boolean release(ItemStack stack, ServerLevel level, Vec3 position) {
        Mob mob = null;
        boolean added = false;
        try {
            mob = restore(stack, level);
            if (mob == null) return false;
            mob.moveTo(position.x, position.y, position.z, mob.getYRot(), mob.getXRot());
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

    private static Mob restore(ItemStack stack, ServerLevel level) {
        if (!CanContents.valid(stack)) return null;
        CompoundTag data = CanContents.entity(stack);
        if (!data.hasUUID("UUID")) return null;
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(data.getUUID("UUID")) != null) return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(data.getString("id"))).orElse(null);
        if (type == null) return null;
        Entity created = type.create(level);
        if (!(created instanceof Mob mob)) return null;
        try {
            mob.load(data);
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
            EntityType<?> type = candidates.remove(level.random.nextInt(candidates.size()));
            Entity entity = null;
            try {
                entity = type.create(level, null, BlockPos.containing(position), MobSpawnType.TRIGGERED, false, false);
                if (!(entity instanceof Mob mob)) continue;
                mob.moveTo(position.x, position.y, position.z, mob.getYRot(), 0);
                if (mob.isRemoved() || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox()) || !level.noCollision(mob)) continue;
                if (level.addFreshEntity(mob)) return true;
            } catch (RuntimeException failure) {
                LOGGER.debug("Random can skipped {}: {}", type, failure.toString());
            } finally {
                if (entity instanceof Mob mob && !mob.isAddedToLevel()) FusionRuntime.detach(mob);
            }
        }
        return false;
    }

    private static void message(Player player, String key) {
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }
}
