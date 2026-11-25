package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.can.MonsterCanItem;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.platform.EditorLink;
import cc.sighs.cannedmonster.platform.PlayerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Authoritative server side of the visual editor.
 *
 * <p>Entry is deliberately narrow: only a filled large fusion can in the acting player's hand can
 * open the editor. Sneaking with that can on air, a block or an entity all edit the held can; the
 * clicked block container or entity is never edited. The exact hand stack and its contents are
 * frozen for the session; changing the stack closes the session (a fresh sneak click reopens it).
 *
 * <p>Scopes are 0 individual embedded inside the can contents, 1 can item default independent of
 * the contents (survives an empty can), 2 the player UUID default for the exact layer count and
 * 3 the server default for the exact layer count. Defaults are never resized across counts.
 */
public final class VisualEditorServer {
    public static final int SCOPE_INDIVIDUAL = 0;
    public static final int SCOPE_ITEM = 1;
    public static final int SCOPE_PLAYER = 2;
    public static final int SCOPE_SERVER = 3;
    public static final int SCOPE_COUNT = 4;
    /** Alias retained for older call sites. */
    public static final int SCOPE_SESSION = SCOPE_INDIVIDUAL;

    private static final int VALIDATE_INTERVAL = 20;
    private static final int SESSION_TIMEOUT = 20 * 60 * 5;
    private static final Random RANDOM = new Random();
    private static final Map<Long, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> BY_PLAYER = new HashMap<>();

    private static final class Session {
        final long id;
        final ServerPlayer player;
        final InteractionHand hand;
        final ItemStack source;
        final int layers;
        final boolean admin;
        final long opened;
        ItemStack frozen;
        VisualLayout layout;
        int revision;
        long validated;
        boolean committed;

        private Session(long id, ServerPlayer player, InteractionHand hand, ItemStack source, ItemStack frozen,
                        VisualLayout layout, int layers, boolean admin, long opened) {
            this.id = id;
            this.player = player;
            this.hand = hand;
            this.source = source;
            this.frozen = frozen;
            this.layout = layout;
            this.layers = layers;
            this.admin = admin;
            this.opened = opened;
            this.validated = opened;
        }
    }

    private VisualEditorServer() {}

    /** Only a filled large fusion can is editable; empty, random, spatial and small cans are not. */
    public static boolean openHeld(ServerPlayer player, InteractionHand hand) {
        if (player == null || player.isSpectator() || hand == null) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!editable(stack)) return false;
        Session previous = session(player);
        if (previous != null) close(previous, null);
        int layers = layerCount(stack);
        VisualLayoutResolver.Resolved resolved = VisualLayoutResolver.resolve(player, stack, layers);
        VisualLayout layout = resolved.layout();
        long id = nextId();
        Session session = new Session(id, player, hand, stack, stack.copy(), layout, layers,
                PlayerAccess.admin(player), player.tickCount);
        SESSIONS.put(id, session);
        BY_PLAYER.put(player.getUUID(), id);
        CompoundTag packetLayout = resolved.source() == VisualLayoutResolver.Source.DEFAULT
                ? new CompoundTag() : layout.save();
        EditorLink.open(player, hand, id, 0, packetLayout, session.admin);
        PlayerAccess.message(player, Component.translatable("editor.cannedmonster.opened", layers));
        return true;
    }

    /** A block click while sneaking only opens the held can; the clicked block is never an editor target. */
    public static boolean openBlock(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        return openHeld(player, hand);
    }

    /** An entity click while sneaking only opens the held can; the clicked entity is never an editor target. */
    public static boolean openEntity(ServerPlayer player, InteractionHand hand, LivingEntity target) {
        return openHeld(player, hand);
    }

    public static void handleSave(ServerPlayer player, long sessionId, int revision, int scope, CompoundTag tag) {
        Session session = session(player, sessionId);
        if (session == null) {
            result(player, sessionId, 0, false, key("editor.cannedmonster.session_missing"));
            return;
        }
        if (revision != session.revision) {
            result(player, sessionId, session.revision, false, key("editor.cannedmonster.revision"));
            return;
        }
        if (scope < 0 || scope >= SCOPE_COUNT) {
            result(player, sessionId, session.revision, false, key("editor.cannedmonster.save_failed"));
            return;
        }
        VisualLayout parsed = VisualLayout.load(tag);
        if (parsed == null) {
            result(player, sessionId, session.revision, false, key("editor.cannedmonster.invalid_layout"));
            return;
        }
        if (parsed.layerCount() != session.layers) {
            result(player, sessionId, session.revision, false, key("editor.cannedmonster.invalid_layout"));
            return;
        }
        ItemStack current = player.getItemInHand(session.hand);
        if (!exactTarget(session, current)) {
            result(player, sessionId, session.revision, false, key("editor.cannedmonster.source_changed"));
            close(session, null);
            return;
        }
        switch (scope) {
            case SCOPE_INDIVIDUAL -> {
                if (!VisualLayoutResolver.writeEmbedded(current, parsed)) {
                    result(player, sessionId, session.revision, false, key("editor.cannedmonster.invalid_layout"));
                    return;
                }
            }
            case SCOPE_ITEM -> {
                if (!VisualLayoutResolver.writeItemDefault(current, parsed)) {
                    result(player, sessionId, session.revision, false, key("editor.cannedmonster.invalid_layout"));
                    return;
                }
            }
            case SCOPE_PLAYER -> {
                MinecraftServer server = server(session);
                if (server == null) {
                    result(player, sessionId, session.revision, false, key("editor.cannedmonster.save_failed"));
                    return;
                }
                VisualDefaults.writePlayer(server, player.getUUID(), session.layers, parsed);
            }
            case SCOPE_SERVER -> {
                if (!PlayerAccess.admin(player)) {
                    result(player, sessionId, session.revision, false, key("editor.cannedmonster.not_admin"));
                    return;
                }
                MinecraftServer server = server(session);
                if (server == null) {
                    result(player, sessionId, session.revision, false, key("editor.cannedmonster.save_failed"));
                    return;
                }
                VisualDefaults.writeServer(server, session.layers, parsed);
            }
            default -> {
                result(player, sessionId, session.revision, false, key("editor.cannedmonster.save_failed"));
                return;
            }
        }
        session.layout = parsed;
        session.frozen = current.copy();
        session.committed = true;
        session.revision++;
        result(player, sessionId, session.revision, true, savedKey(scope));
    }

    public static void handleClose(ServerPlayer player, long sessionId, int revision, boolean ok, String message) {
        Session session = session(player, sessionId);
        if (session == null) return;
        close(session, message);
    }

    /**
     * Unfreezes the held can when it is released (including a throwable can). The item already
     * carries its embedded individual and item default, so the thrown copy keeps both presets;
     * unsaved editor edits are not silently written here.
     */
    public static boolean release(ServerPlayer player, ItemStack stack) {
        Session session = session(player);
        if (session == null) return true;
        ItemStack held = player.getItemInHand(session.hand);
        if (held != stack && !PlayerAccess.same(held, stack)) return true;
        close(session, null);
        return true;
    }

    /** True only while the session's exact hand stack is still the current stack. */
    public static boolean frozen(Player player, ItemStack stack) {
        Session session = sessionFor(player, stack);
        return session != null && exactTarget(session, stack);
    }

    public static boolean frozen(Entity entity) {
        return false;
    }

    public static boolean frozen(Player player) {
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        int tick = server.getTickCount();
        List<Session> expired = new ArrayList<>();
        List<Session> lost = new ArrayList<>();
        for (Session session : SESSIONS.values()) {
            if (tick - session.opened > SESSION_TIMEOUT) {
                expired.add(session);
                continue;
            }
            if (tick - session.validated < VALIDATE_INTERVAL) continue;
            session.validated = tick;
            if (!exactTarget(session, session.player.getItemInHand(session.hand))) lost.add(session);
        }
        for (Session session : expired) {
            result(session.player, session.id, session.revision, false, key("editor.cannedmonster.expired"));
            close(session, null);
        }
        for (Session session : lost) {
            result(session.player, session.id, session.revision, false, key("editor.cannedmonster.source_changed"));
            close(session, null);
        }
    }

    public static void left(ServerPlayer player) {
        Session session = session(player);
        if (session != null) close(session, null);
    }

    public static void stop() {
        SESSIONS.clear();
        BY_PLAYER.clear();
    }

    public static int layerCount(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !CanContents.filled(stack)) return 1;
        try {
            return Math.max(1, FusionRuntime.memberCount(CanContents.entity(stack)));
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    public static VisualLayout itemLayout(ItemStack stack) {
        return VisualLayoutResolver.itemDefault(stack);
    }

    public static boolean writeItemLayout(ItemStack stack, VisualLayout layout) {
        return VisualLayoutResolver.writeItemDefault(stack, layout);
    }

    private static boolean exactTarget(Session session, ItemStack current) {
        if (current == null || current.isEmpty()) return false;
        if (current.getItem() != session.source.getItem()) return false;
        return PlayerAccess.same(current, session.frozen) && CanContents.valid(current);
    }

    private static Session sessionFor(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return null;
        Session direct = session(player);
        if (direct == null) return null;
        return direct.player.getItemInHand(direct.hand) == stack ? direct : null;
    }

    private static Session session(Player player) {
        if (player == null) return null;
        Long id = BY_PLAYER.get(player.getUUID());
        return id == null ? null : SESSIONS.get(id);
    }

    private static Session session(ServerPlayer player, long id) {
        if (player == null) return null;
        Session session = SESSIONS.get(id);
        return session == null || session.player != player ? null : session;
    }

    private static boolean editable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof MonsterCanItem can
                && can.storesMobs() && can.storesFusion() && CanContents.valid(stack);
    }

    private static void close(Session session, String message) {
        SESSIONS.remove(session.id);
        BY_PLAYER.remove(session.player.getUUID(), session.id);
        if (message != null && !message.isEmpty()) PlayerAccess.message(session.player, Component.literal(message));
        EditorLink.closed(session.player, session.id);
    }

    private static void result(ServerPlayer player, long session, int revision, boolean ok, String message) {
        EditorLink.result(player, session, revision, ok, message);
    }

    private static String savedKey(int scope) {
        return switch (scope) {
            case SCOPE_INDIVIDUAL -> key("editor.cannedmonster.saved_individual");
            case SCOPE_ITEM -> key("editor.cannedmonster.saved_item");
            case SCOPE_PLAYER -> key("editor.cannedmonster.saved_player");
            default -> key("editor.cannedmonster.saved_server");
        };
    }

    private static String key(String translationKey) {
        return Component.translatable(translationKey).getString();
    }

    private static long nextId() {
        long id;
        do id = RANDOM.nextLong() & Long.MAX_VALUE;
        while (id == 0 || SESSIONS.containsKey(id));
        return id;
    }

    private static MinecraftServer server(Session session) {
        return session.player.level() instanceof ServerLevel level ? level.getServer() : null;
    }
}
