package cc.sighs.cannedmonster.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public final class EntityStorage {
    private EntityStorage() {}

    public static CompoundTag compound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    public static ListTag list(CompoundTag tag, String key) {
        return tag.getList(key, Tag.TAG_COMPOUND);
    }

    public static int integer(CompoundTag tag, String key) {
        return tag.getInt(key);
    }

    public static String string(CompoundTag tag, String key) {
        return tag.getString(key);
    }

    public static CompoundTag save(Mob member) {
        CompoundTag tag = member.saveWithoutId(new CompoundTag());
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(member.getType()).toString());
        return tag;
    }

    public static Entity create(String rawId, Level level) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) throw new IllegalArgumentException("Missing fusion entity type: " + rawId);
        return create(type, level);
    }

    public static Entity create(EntityType<?> type, Level level) {
        return type.create(level);
    }

    public static void load(Mob member, CompoundTag tag) {
        member.load(tag);
    }

    public static void position(Mob member, Entity body) {
        member.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
    }

    public static void position(Mob member, net.minecraft.world.phys.Vec3 position, float pitch) {
        member.moveTo(position.x, position.y, position.z, member.getYRot(), pitch);
    }

    public static java.util.UUID identity(CompoundTag tag) {
        return tag.hasUUID("UUID") ? tag.getUUID("UUID") : null;
    }

    public static Entity spawn(EntityType<?> type, net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        return type.create(level, null, net.minecraft.core.BlockPos.containing(position),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, false, false);
    }

    public static boolean added(Mob member) {
        return member.isAddedToLevel();
    }
}
