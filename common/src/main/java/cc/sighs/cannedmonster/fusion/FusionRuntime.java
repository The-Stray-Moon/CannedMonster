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
    static final String SAVE_KEY = "cannedmonster:fusion";
    private static final Map<LivingEntity, List<Mob>> SESSIONS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<LivingEntity, List<Map<ResourceLocation, Double>>> ATTRIBUTE_ORIGINS = new IdentityHashMap<>();
    private static boolean initialized;

    private FusionRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        try {
            if (!BoundObject.class.isAssignableFrom(Entity.class)
                    || !BoundObject.class.isAssignableFrom(net.minecraft.network.syncher.SynchedEntityData.class)) {
                throw new IllegalStateException("Fusion binding slots were not installed");
            }
            for (Class<?> type : List.of(Entity.class, LivingEntity.class, Mob.class)) {
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) {
                        field.setAccessible(true);
                        BodyLinks.shareAccessor(field.get(null), type != Mob.class);
                    }
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Fusion bootstrap is missing or incompatible", exception);
        }
        initialized = true;
    }

    public static void attach(LivingEntity host, List<Mob> facets) {
        if (!host.isAlive() || host.isRemoved()) throw new IllegalArgumentException("Cannot attach members to a removed or dead body");
        List<Map<ResourceLocation, Double>> origins = new ArrayList<>();
        facets.forEach(member -> origins.add(AttributeAverages.capture(member)));
        origins.add(AttributeAverages.capture(host));
        attach(host, facets, List.copyOf(origins));
    }

    static void attach(LivingEntity host, List<Mob> facets, List<Map<ResourceLocation, Double>> origins) {
        initialize();
        if (host.level().isClientSide || facets.isEmpty() || SESSIONS.containsKey(host) || BodyLinks.isFacet(host)) {
            throw new IllegalArgumentException("Fusion requires an independent server body and members");
        }
        Set<LivingEntity> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.add(host);
        for (Mob facet : facets) {
            if (!unique.add(facet) || facet.level() != host.level() || facet.isAddedToLevel()
                    || facet.isRemoved() || BodyLinks.isFacet(facet) || SESSIONS.containsKey(facet)) {
                throw new IllegalArgumentException("Members must be distinct unregistered mobs in the body's level");
            }
        }
        if (origins.size() != facets.size() + 1) throw new IllegalArgumentException("Fusion attribute origins mismatch");
        List<Mob> bound = new ArrayList<>();
        var originalState = AttributeAverages.snapshot(host);
        try {
            AttributeAverages.apply(host, origins);
            for (Mob facet : facets) {
                facet.moveTo(host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
                BodyLinks.bind(facet, host, facet.getEntityData(), host.getEntityData());
                if (facet.getEntityData() instanceof BoundObject data) data.cannedmonster$session(host);
                bound.add(facet);
                if (host instanceof Player) {
                    ((NativeBrainAccess) facet.getBrain()).cannedmonster$owner(facet);
                    facet.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                }
            }
            SESSIONS.put(host, List.copyOf(facets));
            ATTRIBUTE_ORIGINS.put(host, List.copyOf(origins));
            if (host instanceof Mob mob) {
                BodyLinks.session(host, new FusionMotion(mob, facets));
                mob.setPersistenceRequired();
            }
            OrderedCalls.bind(host, facets, FusionRuntime::aiBoundary);
        } catch (RuntimeException exception) {
            OrderedCalls.unbind(host);
            SESSIONS.remove(host);
            ATTRIBUTE_ORIGINS.remove(host);
            BodyLinks.session(host, null);
            bound.forEach(facet -> BodyLinks.unbind(facet, facet.getEntityData()));
            try { originalState.restore(host); }
            catch (RuntimeException rollback) { exception.addSuppressed(rollback); }
            throw exception;
        }
    }

    static List<Map<ResourceLocation, Double>> originsOf(LivingEntity host) {
        var saved = ATTRIBUTE_ORIGINS.get(host);
        return saved == null ? List.of(AttributeAverages.capture(host)) : saved;
    }

    static void baseChanged(LivingEntity body, ResourceLocation attribute, double value) {
        if (!(body instanceof Player) || !Double.isFinite(value)) return;
        var origins = ATTRIBUTE_ORIGINS.get(body);
        if (origins == null) return;
        List<Map<ResourceLocation, Double>> updated = new ArrayList<>(origins);
        Map<ResourceLocation, Double> own = new HashMap<>(origins.getLast());
        own.put(attribute, value);
        updated.set(updated.size() - 1, Map.copyOf(own));
        ATTRIBUTE_ORIGINS.put(body, List.copyOf(updated));
    }

    public static void prepend(LivingEntity host, Mob incoming) {
        if (host == incoming || host.level() != incoming.level() || incoming.isAddedToLevel()
                || BodyLinks.isFacet(host) || BodyLinks.isFacet(incoming) || !host.isAlive() || !incoming.isAlive()) {
            throw new IllegalArgumentException("Fusion requires a live body and an independent incoming mob");
        }
        List<Mob> previous = List.copyOf(facets(host));
        List<Mob> incomingMembers = List.copyOf(facets(incoming));
        Set<UUID> occupied = new HashSet<>();
        occupied.add(host.getUUID());
        synchronized (SESSIONS) {
            SESSIONS.forEach((body, members) -> {
                if (body != incoming) members.forEach(member -> occupied.add(independentSave(member).getUUID("UUID")));
            });
        }
        List<Mob> additions = new ArrayList<>(incomingMembers);
        additions.add(incoming);
        for (Mob member : additions) {
            if (!occupied.add(independentSave(member).getUUID("UUID"))) {
                throw new IllegalArgumentException("Fusion member UUID is already owned by a body");
            }
        }
        var previousOrigins = originsOf(host);
        var incomingOrigins = originsOf(incoming);
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

    public static List<Mob> facets(LivingEntity host) {
        return SESSIONS.getOrDefault(host, List.of());
    }

    public static Mob peel(LivingEntity host) {
        initialize();
        if (BodyLinks.isFacet(host)) throw new IllegalArgumentException("Target the fusion body");
        List<Mob> previous = List.copyOf(facets(host));
        if (previous.isEmpty()) throw new IllegalArgumentException("Fusion body has no members");
        var previousOrigins = originsOf(host);
        Mob peeled = previous.getFirst();
        List<Mob> remaining = List.copyOf(previous.subList(1, previous.size()));
        var remainingOrigins = List.copyOf(previousOrigins.subList(1, previousOrigins.size()));
        detach(host);
        try {
            if (remaining.isEmpty()) AttributeAverages.apply(host, List.of(previousOrigins.getLast()));
            else attach(host, remaining, remainingOrigins);
            return peeled;
        } catch (RuntimeException failure) {
            detach(host);
            try { attach(host, previous, previousOrigins); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    private static void aiBoundary(Object entity) {
        Mob facet = (Mob) entity;
        if (!facet.level().isClientSide && facet.isAlive() && !facet.isNoAi()) {
            if (BodyLinks.body(facet) instanceof Player player) {
                LivingEntity target = player.getLastHurtMob();
                LivingEntity allowed = FusionTraits.allowedTarget(player, target) ? target : null;
                facet.setTarget(allowed);
                facet.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, allowed);
            }
            ((NativeMobAccess) facet).cannedmonster$serverAiStep();
        }
    }

    public static InteractionResult interact(Mob host, Player player, InteractionHand hand) {
        for (Mob facet : facets(host)) {
            InteractionResult result = ((NativeMobAccess) facet).cannedmonster$mobInteract(player, hand);
            if (result != InteractionResult.PASS) return result;
        }
        return ((NativeMobAccess) host).cannedmonster$mobInteract(player, hand);
    }

    public static void save(Entity entity, CompoundTag tag) {
        if (!(entity instanceof LivingEntity host)) return;
        if (host instanceof Player player && PlayerFusion.saveUnresolved(player, tag)) return;
        List<Mob> facets = facets(host);
        if (facets.isEmpty()) return;
        CompoundTag fusion = new CompoundTag();
        fusion.putInt("Version", 2);
        fusion.put("AttributeOrigins", AttributeAverages.save(originsOf(host)));
        ListTag members = new ListTag();
        for (Mob facet : facets) {
            CompoundTag member = independentSave(facet);
            member.remove("UUID");
            members.add(member);
        }
        fusion.put("Members", members);
        tag.put(SAVE_KEY, fusion);
    }

    public static CompoundTag independentSave(Mob member) {
        Object body = BodyLinks.body(member);
        if (body == member) return saveMember(member);
        var data = member.getEntityData();
        BodyLinks.unbind(member, data);
        try { return saveMember(member); }
        finally {
            BodyLinks.bind(member, body, data, ((LivingEntity) body).getEntityData());
            if (data instanceof BoundObject bound) bound.cannedmonster$session(body);
        }
    }

    private static CompoundTag saveMember(Mob member) {
        CompoundTag tag = member.saveWithoutId(new CompoundTag());
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(member.getType()).toString());
        return tag;
    }

    public static void load(Entity entity, CompoundTag tag) {
        if (!(entity instanceof LivingEntity host) || host.level().isClientSide || !tag.contains(SAVE_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag fusion = tag.getCompound(SAVE_KEY);
        try {
            int version = fusion.getInt("Version");
            if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported fusion save version: " + version);
            ListTag members = fusion.getList("Members", Tag.TAG_COMPOUND);
            if (members.isEmpty()) throw new IllegalArgumentException("Fusion save has no members");
            List<Mob> restored = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                CompoundTag member = members.getCompound(i);
                ResourceLocation id = ResourceLocation.parse(member.getString("id"));
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                        .orElseThrow(() -> new IllegalArgumentException("Missing fusion entity type: " + id));
                Entity created = type.create(host.level());
                if (!(created instanceof Mob facet)) throw new IllegalArgumentException("Fusion member is not a Mob: " + id);
                if (member.contains(SAVE_KEY)) throw new IllegalArgumentException("Nested fusion members must be flattened");
                facet.load(member);
                restored.add(facet);
            }
            var origins = version == 2 ? AttributeAverages.load(fusion.getList("AttributeOrigins", Tag.TAG_COMPOUND), restored.size() + 1) : null;
            detach(host);
            if (origins == null) attach(host, restored);
            else attach(host, restored, origins);
            if (host instanceof Player player) PlayerFusion.resolved(player);
        } catch (RuntimeException failure) {
            if (host instanceof Player player) PlayerFusion.preserve(player, fusion, failure);
            else throw failure;
        }
    }

    public static void detach(LivingEntity host) {
        ATTRIBUTE_ORIGINS.remove(host);
        OrderedCalls.unbind(host);
        BodyLinks.session(host, null);
        List<Mob> facets = SESSIONS.remove(host);
        if (facets != null) facets.forEach(facet -> BodyLinks.unbind(facet, facet.getEntityData()));
    }

    public static void clear() {
        List<LivingEntity> hosts;
        synchronized (SESSIONS) { hosts = List.copyOf(SESSIONS.keySet()); }
        hosts.forEach(FusionRuntime::detach);
        PlayerFusion.clear();
        RidingState.clear();
    }
}
