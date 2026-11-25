package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.platform.EntityStorage;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * World-wide visual layouts of fusion root entities.
 *
 * <p>The server map is authoritative and is written next to the root entity under
 * {@value #SAVE_KEY}; the client map is filled from appearance snapshots and is also where a
 * detached preview host keeps its layout. Both maps are keyed by entity UUID because entity
 * equality uses the (per-level) network id.
 */
public final class VisualViews {
    public static final String SAVE_KEY = "cannedmonster:layout";

    private static final Map<UUID, VisualLayout> SERVER = new HashMap<>();
    private static final Map<UUID, VisualLayout> CLIENT = new HashMap<>();
    private static final Map<LivingEntity, Derived> DERIVED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private record Derived(long signature, VisualLayout layout) {}

    private VisualViews() {}

    /** Stored layout, or the legacy equal-band layout whose native scales encode the actual fit ratios. */
    public static VisualLayout layout(LivingEntity entity) {
        if (entity == null) return VisualLayout.legacy(List.of());
        VisualLayout stored = stored(entity);
        if (stored != null) {
            DERIVED.remove(entity);
            return stored;
        }
        List<LivingEntity> ordered = ordered(entity);
        long signature = signature(ordered);
        Derived derived = DERIVED.get(entity);
        if (derived != null && derived.signature() == signature) return derived.layout();
        VisualLayout layout = VisualLayout.legacy(ordered);
        DERIVED.put(entity, new Derived(signature, layout));
        return layout;
    }

    private static long signature(List<? extends LivingEntity> ordered) {
        long hash = ordered.size();
        for (LivingEntity member : ordered) {
            hash = hash * 31L + member.getUUID().getMostSignificantBits();
            hash = hash * 31L + member.getUUID().getLeastSignificantBits();
            hash = hash * 31L + Float.floatToIntBits(member.getBbWidth());
            hash = hash * 31L + Float.floatToIntBits(member.getBbHeight());
        }
        return hash;
    }

    /** Members in render order followed by the host, matching layout layer order. */
    public static List<LivingEntity> ordered(LivingEntity entity) {
        if (entity == null) return List.of();
        List<? extends LivingEntity> members = entity.level().isClientSide()
                ? ClientAppearance.members(entity) : FusionRuntime.facets(entity);
        List<LivingEntity> ordered = new ArrayList<>(members.size() + 1);
        ordered.addAll(members);
        ordered.add(entity);
        return List.copyOf(ordered);
    }

    public static void set(LivingEntity entity, VisualLayout layout) {
        Objects.requireNonNull(entity, "entity");
        Map<UUID, VisualLayout> views = views(entity);
        if (layout == null) views.remove(entity.getUUID());
        else views.put(entity.getUUID(), layout);
    }

    public static boolean has(LivingEntity entity) {
        return stored(entity) != null;
    }

    public static VisualLayout stored(LivingEntity entity) {
        return entity == null ? null : views(entity).get(entity.getUUID());
    }

    /**
     * Drops an explicitly stored layout when the member composition changed, so rendering and the
     * editor fall back to the legacy layout built from the actual members. Bodies without a stored
     * layout are left alone.
     */
    public static void reconcile(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        VisualLayout stored = SERVER.get(entity.getUUID());
        if (stored == null) return;
        int expected = FusionRuntime.facets(entity).size() + 1;
        if (stored.layerCount() == expected) return;
        SERVER.remove(entity.getUUID());
    }

    public static void discard(LivingEntity entity) {
        if (entity == null) return;
        UUID id = entity.getUUID();
        SERVER.remove(id);
        CLIENT.remove(id);
    }

    public static void accept(UUID bodyId, VisualLayout layout) {
        if (bodyId == null) return;
        if (layout == null) CLIENT.remove(bodyId);
        else CLIENT.put(bodyId, layout);
    }

    public static void forget(UUID bodyId) {
        if (bodyId != null) CLIENT.remove(bodyId);
    }

    public static void clearClient() {
        CLIENT.clear();
        DERIVED.clear();
    }

    public static void clear() {
        SERVER.clear();
        CLIENT.clear();
        DERIVED.clear();
    }

    public static void save(LivingEntity entity, CompoundTag tag) {
        if (entity == null || tag == null || entity.level().isClientSide()) return;
        VisualLayout layout = SERVER.get(entity.getUUID());
        if (layout == null || layout.layerCount() != FusionRuntime.facets(entity).size() + 1) return;
        tag.put(SAVE_KEY, layout.save());
    }

    public static void load(LivingEntity entity, CompoundTag tag) {
        if (entity == null || tag == null || !tag.contains(SAVE_KEY)) return;
        try {
            VisualLayout layout = VisualLayout.load(EntityStorage.compound(tag, SAVE_KEY));
            if (layout == null) SERVER.remove(entity.getUUID());
            else SERVER.put(entity.getUUID(), layout);
        } catch (RuntimeException failure) {
            SERVER.remove(entity.getUUID());
            LogUtils.getLogger().warn("Ignoring invalid visual layout for {}: {}", entity.getUUID(), failure.toString());
        }
    }

    private static Map<UUID, VisualLayout> views(LivingEntity entity) {
        return entity.level().isClientSide() ? CLIENT : SERVER;
    }

    private static int layerCount(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return 1;
        return FusionRuntime.facets(entity).size() + 1;
    }
}
