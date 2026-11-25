package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.can.CanPreviewSelection;
import cc.sighs.cannedmonster.can.CanPreviewSource;
import cc.sighs.cannedmonster.platform.EntityStorage;
import cc.sighs.cannedmonster.platform.PlayerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds detached display bodies for item previews. Hosts and their display members are never
 * registered with the world, are kept for a short time after the last tooltip/editor frame and
 * are rebuilt when the item's saved contents or layout change.
 */
public final class CanPreviewHosts {
    private static final String FUSION_KEY = "cannedmonster:fusion";
    private static final int MAX_SESSIONS = 12;
    private static final int MAX_MEMBERS = 8;
    private static final long TTL_MILLIS = 6000L;
    private static final List<Session> SESSIONS = new ArrayList<>();
    private static ClientLevel level;

    private CanPreviewHosts() {}

    public static LivingEntity host(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current != level) {
            clear();
            level = current;
        }
        if (level == null || stack == null || stack.isEmpty()) return null;
        long now = System.currentTimeMillis();
        boolean spatial = CanContents.spaceId(stack) != null;
        int slot = spatial ? CanPreviewSource.spatialIndex(stack) : -1;
        if (spatial && slot < 0) return null;
        for (Session session : SESSIONS) {
            if (!session.matches(stack, slot)) continue;
            session.lastAccess = now;
            session.updateClock(now);
            return session.host;
        }
        List<CompoundTag> tags = CanPreviewSource.hosts(stack);
        if (tags.isEmpty()) return null;
        if (spatial && slot >= tags.size()) return null;
        List<CompoundTag> selected = spatial ? List.of(tags.get(slot)) : tags;
        Session created = create(stack, selected, now);
        if (created == null) return null;
        created.slot = slot;
        SESSIONS.add(created);
        prune(now);
        created.updateClock(now);
        return created.host;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            clear();
            level = minecraft.level;
            return;
        }
        prune(System.currentTimeMillis());
    }

    public static void clear() {
        for (Session session : SESSIONS) dispose(session);
        SESSIONS.clear();
        CanPreviewSelection.clear();
        VisualViews.clearClient();
    }

    private static Session create(ItemStack stack, List<CompoundTag> tags, long now) {
        Mob host = createEntity(tags.get(tags.size() - 1));
        if (host == null) return null;
        List<CompoundTag> memberTags = new ArrayList<>();
        boolean complete = true;
        CompoundTag fusion = EntityStorage.compound(tags.get(tags.size() - 1), FUSION_KEY);
        if (!fusion.isEmpty()) {
            if (EntityStorage.bool(fusion, "Incomplete")) complete = false;
            ListTag saved = EntityStorage.list(fusion, "Members");
            if (saved.size() > MAX_MEMBERS) complete = false;
            for (Tag child : saved) {
                if (memberTags.size() >= MAX_MEMBERS) break;
                if (!(child instanceof CompoundTag tag) || EntityStorage.string(tag, "id").isEmpty()) {
                    complete = false;
                    break;
                }
                memberTags.add(tag.copy());
            }
        }
        for (int i = 0; i < tags.size() - 1 && memberTags.size() < MAX_MEMBERS; i++) {
            memberTags.add(tags.get(i));
        }
        List<Mob> members = new ArrayList<>(memberTags.size());
        for (CompoundTag tag : memberTags) {
            Mob member = createEntity(tag);
            if (member == null) {
                complete = false;
                break;
            }
            DisplayExecution.register(member);
            members.add(member);
        }
        if (!complete) {
            members.forEach(member -> member.setRemoved(Entity.RemovalReason.DISCARDED));
            host.setRemoved(Entity.RemovalReason.DISCARDED);
            return null;
        }
        if (!members.isEmpty()) ClientAppearance.preview(host, members);
        VisualLayout embedded = CanPreviewSource.embedded(stack);
        VisualLayout item = CanPreviewSource.layout(stack);
        VisualLayout chosen = embedded != null && embedded.layerCount() == members.size() + 1 ? embedded
                : item != null && item.layerCount() == members.size() + 1 ? item
                : null;
        if (chosen != null) VisualViews.set(host, chosen);
        return new Session(stack.copy(), host, members, now);
    }

    private static Mob createEntity(CompoundTag tag) {
        try {
            String id = EntityStorage.string(tag, "id");
            if (id.isEmpty()) return null;
            Entity created = EntityStorage.create(id, level);
            if (!(created instanceof Mob mob)) {
                created.discard();
                return null;
            }
            EntityStorage.load(mob, tag);
            mob.setUUID(UUID.randomUUID());
            mob.setPos(0.0, 0.0, 0.0);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.setNoGravity(true);
            return mob;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static void prune(long now) {
        for (int i = SESSIONS.size() - 1; i >= 0; i--) {
            Session session = SESSIONS.get(i);
            long age = now - session.lastAccess;
            if (age <= TTL_MILLIS) continue;
            SESSIONS.remove(i);
            dispose(session);
        }
        while (SESSIONS.size() > MAX_SESSIONS) {
            Session oldest = SESSIONS.get(0);
            for (Session session : SESSIONS) {
                if (session.lastAccess < oldest.lastAccess) oldest = session;
            }
            SESSIONS.remove(oldest);
            dispose(oldest);
        }
    }

    private static void dispose(Session session) {
        ClientAppearance.clearPreview(session.host);
        VisualViews.discard(session.host);
        session.host.setRemoved(Entity.RemovalReason.DISCARDED);
    }

    private static final class Session {
        private final ItemStack key;
        private final Mob host;
        private final List<Mob> members;
        private long lastAccess;
        private int slot = -1;

        private Session(ItemStack key, Mob host, List<Mob> members, long now) {
            this.key = key;
            this.host = host;
            this.members = List.copyOf(members);
            this.lastAccess = now;
        }

        private boolean matches(ItemStack stack, int slot) {
            return this.slot == slot && PlayerAccess.same(key, stack);
        }

        private void updateClock(long now) {
            int ticks = (int) ((now / 50L) % 100000L);
            host.tickCount = ticks;
            host.yBodyRot = host.getYRot();
            host.yBodyRotO = host.yBodyRot;
            host.yHeadRot = host.getYRot();
            host.yHeadRotO = host.yHeadRot;
            for (Mob member : members) {
                member.tickCount = ticks;
                member.yBodyRot = host.yBodyRot;
                member.yBodyRotO = member.yBodyRot;
                member.yHeadRot = host.yHeadRot;
                member.yHeadRotO = member.yHeadRot;
            }
        }
    }
}
