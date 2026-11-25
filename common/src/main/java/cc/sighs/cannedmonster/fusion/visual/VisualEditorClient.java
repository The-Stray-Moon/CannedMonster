package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

/**
 * Entry point used by the network layer when the server opens an editor session, and by the
 * screen adapters when it closes. The screen implementation itself is registered by each
 * version's client setup through {@link #bind(Host)}.
 */
public final class VisualEditorClient {
    public interface Host {
        void open(VisualEditorState state);
    }

    private static Host host;
    private static VisualEditorState current;

    private VisualEditorClient() {}

    public static void bind(Host value) {
        host = value;
    }

    public static VisualEditorState current() {
        return current;
    }

    /**
     * Opens (or replaces) the editor for a server-issued session. Must be called on the client;
     * network handlers may call it from their own thread and the work is moved to the client thread.
     */
    public static void open(InteractionHand hand, long session, int revision, CompoundTag layout, boolean admin) {
        VisualLayout parsed;
        try {
            parsed = VisualLayout.load(layout);
        } catch (RuntimeException exception) {
            parsed = null;
        }
        VisualLayout packetLayout = parsed;
        Minecraft minecraft = Minecraft.getInstance();
        Runnable task = () -> {
            InteractionHand actualHand = hand == null ? InteractionHand.MAIN_HAND : hand;
            net.minecraft.client.player.LocalPlayer player = minecraft.player;
            VisualLayout resolved = packetLayout;
            if (resolved == null || resolved.layers().isEmpty()) {
                resolved = null;
                if (player != null) {
                    net.minecraft.world.item.ItemStack stack = player.getItemInHand(actualHand);
                    net.minecraft.world.entity.LivingEntity preview = CanPreviewHosts.host(stack);
                    if (preview != null) {
                        VisualLayout legacy = VisualLayout.legacy(VisualViews.ordered(preview));
                        if (!legacy.layers().isEmpty()) resolved = legacy;
                    }
                    if (resolved == null && cc.sighs.cannedmonster.can.CanContents.valid(stack)) {
                        int layers = Math.max(1, cc.sighs.cannedmonster.fusion.FusionRuntime.memberCount(
                                cc.sighs.cannedmonster.can.CanContents.entity(stack)));
                        resolved = VisualLayout.defaults(layers);
                    }
                }
            }
            VisualEditorState state = new VisualEditorState(actualHand, session, revision, resolved, admin);
            current = state;
            Host target = host;
            if (target != null) target.open(state);
            else com.mojang.logging.LogUtils.getLogger().warn("Visual editor host is not bound on the client");
        };
        if (minecraft.isSameThread()) task.run();
        else minecraft.execute(task);
    }

    /** Server response to a save; the editor stays open and reports the result. */
    public static void ack(long session, int revision, boolean ok, String message) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable task = () -> {
            VisualEditorState state = current;
            if (state == null || state.session() != session) return;
            state.acknowledge(revision, ok, message);
        };
        if (minecraft.isSameThread()) task.run();
        else minecraft.execute(task);
    }

    public static void closed(long session) {
        VisualEditorState state = current;
        if (state == null || state.session() != session) return;
        current = null;
        EditorTransport.ack(session, state.revision(), false, "");
    }

    public static void save(VisualEditorState state, int scope) {
        if (state == null || state.saving()) return;
        if (scope < VisualEditorServer.SCOPE_SESSION || scope >= VisualEditorServer.SCOPE_COUNT) {
            state.fail("editor.cannedmonster.save_failed");
            return;
        }
        if (scope == VisualEditorServer.SCOPE_SERVER && !state.admin()) {
            state.fail("editor.cannedmonster.not_admin");
            return;
        }
        if (!EditorTransport.bound()) {
            state.fail("editor.cannedmonster.transport_missing");
            return;
        }
        state.beginSave(scope);
        EditorTransport.save(state.session(), state.revision(), scope, state.layout().save());
    }
}
