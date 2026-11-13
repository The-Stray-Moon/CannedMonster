package cc.sighs.cannedmonster.fusion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Modifier;
import java.util.*;

public final class FusionRuntime {
    private static final String SAVE_KEY = "cannedmonster:fusion";
    private static final Map<Mob, List<Mob>> SESSIONS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static boolean initialized;

    private FusionRuntime() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            if (!BoundObject.class.isAssignableFrom(Entity.class)
                    || !BoundObject.class.isAssignableFrom(net.minecraft.network.syncher.SynchedEntityData.class)) {
                throw new IllegalStateException("Fusion binding slots were not installed");
            }
            for (Class<?> type : List.of(Entity.class, LivingEntity.class, Mob.class)) {
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) {
                        field.setAccessible(true);
                        BodyLinks.shareAccessor(field.get(null));
                    }
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Fusion bootstrap is missing or incompatible; refusing to run duplicate entity ticks", exception);
        }
        initialized = true;
    }

    private static final Map<Mob, List<Map<ResourceLocation, Double>>> ATTRIBUTE_ORIGINS = new IdentityHashMap<>();

    public static void attach(Mob host, List<Mob> facets) {
        List<Map<ResourceLocation, Double>> origins = new ArrayList<>();
        facets.forEach(member -> origins.add(AttributeAverages.capture(member)));
        origins.add(AttributeAverages.capture(host));
        attach(host, facets, List.copyOf(origins));
    }

    private static void attach(Mob host, List<Mob> facets, List<Map<ResourceLocation, Double>> origins) {
        initialize();
        if (host.level().isClientSide || facets.isEmpty() || SESSIONS.containsKey(host) || BodyLinks.isFacet(host)) {
            throw new IllegalArgumentException("Fusion requires a fresh server-side body and at least one facet");
        }
        Set<Mob> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.add(host);
        for (Mob facet : facets) {
            if (!unique.add(facet) || facet.level() != host.level() || facet.isAddedToLevel()
                    || facet.isRemoved() || BodyLinks.isFacet(facet) || SESSIONS.containsKey(facet)) {
                throw new IllegalArgumentException("Facets must be distinct, unregistered mobs in the body's level");
            }
        }
        if (origins.size() != facets.size() + 1) throw new IllegalArgumentException("Fusion attribute origins mismatch");
        List<Mob> bound = new ArrayList<>();
        try {
            AttributeAverages.apply(host, origins);
            for (Mob facet : facets) {
                facet.moveTo(host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
                BodyLinks.bind(facet, host, facet.getEntityData(), host.getEntityData());
                bound.add(facet);
            }
            SESSIONS.put(host, List.copyOf(facets));
            ATTRIBUTE_ORIGINS.put(host, List.copyOf(origins));
            BodyLinks.session(host, new FusionMotion(host, facets));
            OrderedCalls.bind(host, facets, FusionRuntime::aiBoundary);
            host.setPersistenceRequired();
        } catch (RuntimeException exception) {
            OrderedCalls.unbind(host);
            SESSIONS.remove(host);
            ATTRIBUTE_ORIGINS.remove(host);
            BodyLinks.session(host, null);
            bound.forEach(facet -> BodyLinks.unbind(facet, facet.getEntityData()));
            throw exception;
        }
    }

    private static List<Map<ResourceLocation, Double>> origins(Mob host) {
        var saved = ATTRIBUTE_ORIGINS.get(host);
        return saved == null ? List.of(AttributeAverages.capture(host)) : saved;
    }

    public static void prepend(Mob host, Mob incoming) {
        if (host == incoming || host.level() != incoming.level() || incoming.isAddedToLevel()
                || BodyLinks.isFacet(host) || BodyLinks.isFacet(incoming) || !host.isAlive() || !incoming.isAlive()) {
            throw new IllegalArgumentException("Fusion requires a live body and an independent incoming mob");
        }
        List<Mob> previous = List.copyOf(facets(host));
        List<Mob> incomingMembers = List.copyOf(facets(incoming));
        var previousOrigins = origins(host);
        var incomingOrigins = origins(incoming);
        List<Map<ResourceLocation, Double>> combinedOrigins = new ArrayList<>(incomingOrigins);
        combinedOrigins.addAll(previousOrigins);
        List<Mob> combined = new ArrayList<>(incomingMembers);
        combined.add(incoming);
        combined.addAll(previous);
        detach(incoming);
        detach(host);
        try {
            attach(host, combined, combinedOrigins);
        } catch (RuntimeException failure) {
            detach(host);
            if (!previous.isEmpty()) attach(host, previous, previousOrigins);
            if (!incomingMembers.isEmpty()) attach(incoming, incomingMembers, incomingOrigins);
            throw failure;
        }
    }

    public static List<Mob> facets(Mob host) {
        return SESSIONS.getOrDefault(host, List.of());
    }

    private static void aiBoundary(Object entity) {
        Mob facet = (Mob) entity;
        if (!facet.level().isClientSide && facet.isAlive() && !facet.isNoAi()) {
            ((NativeMobAccess) facet).cannedmonster$serverAiStep();
        }
    }

    public static InteractionResult interact(Mob host, Player player, InteractionHand hand) {
        for (Mob facet : facets(host)) {
            InteractionResult result = ((NativeMobAccess) facet).cannedmonster$mobInteract(player, hand);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return ((NativeMobAccess) host).cannedmonster$mobInteract(player, hand);
    }

    public static void save(Entity entity, CompoundTag tag) {
        if (!(entity instanceof Mob host)) {
            return;
        }
        List<Mob> facets = facets(host);
        if (facets.isEmpty()) {
            return;
        }
        CompoundTag fusion = new CompoundTag();
        fusion.putInt("Version", 2);
        fusion.put("AttributeOrigins", AttributeAverages.save(origins(host)));
        ListTag members = new ListTag();
        for (Mob facet : facets) {
            CompoundTag member = facet.saveWithoutId(new CompoundTag());
            member.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(facet.getType()).toString());
            member.remove("UUID");
            members.add(member);
        }
        fusion.put("Members", members);
        tag.put(SAVE_KEY, fusion);
    }

    public static void load(Entity entity, CompoundTag tag) {
        if (!(entity instanceof Mob host) || host.level().isClientSide || !tag.contains(SAVE_KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        detach(host);
        CompoundTag fusion = tag.getCompound(SAVE_KEY);
        int version = fusion.getInt("Version");
        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("Unsupported fusion save version: " + version);
        }
        ListTag members = fusion.getList("Members", Tag.TAG_COMPOUND);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Fusion save has no members");
        }
        List<Mob> facets = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            ResourceLocation id = ResourceLocation.parse(member.getString("id"));
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .orElseThrow(() -> new IllegalArgumentException("Missing fusion entity type: " + id));
            Entity created = type.create(host.level());
            if (!(created instanceof Mob facet)) {
                throw new IllegalArgumentException("Fusion member is not a Mob: " + id);
            }
            if (member.contains(SAVE_KEY)) {
                throw new IllegalArgumentException("Nested fusion members must be flattened");
            }
            facet.load(member);
            facets.add(facet);
        }
        if (version == 2) {
            attach(host, facets, AttributeAverages.load(fusion.getList("AttributeOrigins", Tag.TAG_COMPOUND), facets.size() + 1));
        } else {
            attach(host, facets);
        }
    }

    public static void detach(Mob host) {
        ATTRIBUTE_ORIGINS.remove(host);
        OrderedCalls.unbind(host);
        BodyLinks.session(host, null);
        List<Mob> facets = SESSIONS.remove(host);
        if (facets != null) {
            facets.forEach(facet -> BodyLinks.unbind(facet, facet.getEntityData()));
        }
    }

    public static void clear() {
        List<Mob> hosts;
        synchronized (SESSIONS) {
            hosts = List.copyOf(SESSIONS.keySet());
        }
        hosts.forEach(FusionRuntime::detach);
        RidingState.clear();
    }
}
