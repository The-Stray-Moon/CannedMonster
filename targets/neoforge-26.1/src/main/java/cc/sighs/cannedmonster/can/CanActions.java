package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                if (!FusionRuntime.facets(target).isEmpty() && !item.storesFusion()) {
                    return peel(stack, target, actor);
                }
                CompoundTag data = save(target, level);
                data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
                ItemStack prepared = stack.copyWithCount(1);
                CanContents.store(prepared, data, target.getName().getString(), FusionRuntime.memberCount(data));
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

    private static CompoundTag save(Mob mob, ServerLevel level) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mob.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            mob.saveWithoutId(output);
            return output.buildResult();
        }
    }

    private static boolean peel(ItemStack stack, Mob target, Player actor) {
        Mob member = FusionRuntime.facets(target).getFirst();
        CompoundTag data = FusionRuntime.independentSave(member);
        ItemStack prepared = stack.copyWithCount(1);
        CanContents.store(prepared, data, member.getName().getString());
        FusionRuntime.peel(target);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, prepared.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        message(actor, "message.cannedmonster.peeled", member.getName());
        return true;
    }

    public static boolean release(ItemStack stack, ServerLevel level, Vec3 position) {
        Mob mob = null;
        boolean added = false;
        try {
            mob = restore(stack, level);
            if (mob == null) return false;
            mob.snapTo(position.x, position.y, position.z, mob.getYRot(), mob.getXRot());
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
        Optional<UUID> stored = data.read("UUID", UUIDUtil.CODEC);
        if (stored.isEmpty()) return null;
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            if (dimension.getEntity(stored.get()) != null) return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(data.getStringOr("id", ""))).orElse(null);
        if (type == null) return null;
        Entity created = type.create(level, EntitySpawnReason.LOAD);
        if (!(created instanceof Mob mob)) return null;
        try {
            ValueInput input = TagValueInput.create(new ProblemReporter.Collector(), level.registryAccess(), data);
            mob.load(input);
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
                entity = type.create(level, null, BlockPos.containing(position), EntitySpawnReason.TRIGGERED, false, false);
                if (!(entity instanceof Mob mob)) continue;
                mob.snapTo(position.x, position.y, position.z, mob.getYRot(), 0);
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

    private static void message(Player player, String key, Object... args) {
        if (player == null) return;
        var message = net.minecraft.network.chat.Component.translatable(key, args);
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
    }
}
