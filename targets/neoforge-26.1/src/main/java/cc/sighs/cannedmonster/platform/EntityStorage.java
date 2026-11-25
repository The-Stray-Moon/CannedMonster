package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.fusion.visual.VisualViews;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class EntityStorage {
    private EntityStorage() {}

    public static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    public static ListTag list(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }

    public static ListTag stringList(CompoundTag tag, String key) {
        return tag.getListOrEmpty(key);
    }

    public static boolean bool(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static int integer(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static String string(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static double decimal(CompoundTag tag, String key) {
        return tag.getDoubleOr(key, 0.0);
    }

    public static CompoundTag save(Mob member) {
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(member.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, member.registryAccess());
            member.saveWithoutId(output);
            tag = output.buildResult();
        }
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(member.getType()).toString());
        return tag;
    }

    public static CompoundTag save(Entity entity) {
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            tag = output.buildResult();
        }
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }

    public static void load(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(new ProblemReporter.Collector(), entity.registryAccess(), tag));
    }

    public static void position(Entity entity, net.minecraft.world.phys.Vec3 position, float pitch) {
        entity.snapTo(position.x, position.y, position.z, entity.getYRot(), pitch);
    }

    public static Entity create(String rawId, Level level) {
        Identifier id = Identifier.tryParse(rawId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) throw new IllegalArgumentException("Missing fusion entity type: " + rawId);
        return create(type, level);
    }

    public static Entity create(EntityType<?> type, Level level) {
        return type.create(level, EntitySpawnReason.LOAD);
    }

    public static void load(Mob member, CompoundTag tag) {
        member.load(TagValueInput.create(new ProblemReporter.Collector(), member.registryAccess(), tag));
    }

    public static void position(Mob member, Entity body) {
        member.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
    }

    public static void position(Mob member, net.minecraft.world.phys.Vec3 position, float pitch) {
        member.snapTo(position.x, position.y, position.z, member.getYRot(), pitch);
    }

    public static java.util.UUID identity(CompoundTag tag) {
        return tag.read("UUID", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    public static Entity spawn(EntityType<?> type, net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        return type.create(level, null, net.minecraft.core.BlockPos.containing(position), EntitySpawnReason.TRIGGERED, false, false);
    }

    public static boolean added(Mob member) {
        return member.isAddedToLevel();
    }

    public static void saveFusion(Entity entity, ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        FusionRuntime.save(entity, tag);
        if (tag.contains("NoGravity")) output.putBoolean("NoGravity", false);
        tag.getCompound("cannedmonster:fusion").ifPresent(fusion -> output.store("cannedmonster:fusion", CompoundTag.CODEC, fusion));
        tag.getCompound(VisualViews.SAVE_KEY).ifPresent(layout -> output.store(VisualViews.SAVE_KEY, CompoundTag.CODEC, layout));
    }

    public static void loadFusion(Entity entity, ValueInput input) {
        CompoundTag tag = new CompoundTag();
        input.read("cannedmonster:fusion", CompoundTag.CODEC).ifPresent(fusion -> tag.put("cannedmonster:fusion", fusion));
        input.read(VisualViews.SAVE_KEY, CompoundTag.CODEC).ifPresent(layout -> tag.put(VisualViews.SAVE_KEY, layout));
        if (!tag.contains("cannedmonster:fusion") && !tag.contains(VisualViews.SAVE_KEY)) return;
        FusionRuntime.load(entity, tag);
    }
}
