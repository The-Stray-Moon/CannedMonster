package cc.sighs.cannedmonster.platform;

import cc.sighs.cannedmonster.fusion.FusionNetwork;
import cc.sighs.cannedmonster.fusion.visual.EditorOpen;
import cc.sighs.cannedmonster.fusion.visual.EditorResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Version backend for opening the visual editor and reporting save results. */
public final class EditorLink {
    private EditorLink() {}

    public static void open(ServerPlayer player, InteractionHand hand, long session, int revision,
                            CompoundTag layout, boolean admin) {
        FusionNetwork.sendToPlayer(player, new EditorOpen(hand, session, revision, layout, admin));
    }

    public static void result(ServerPlayer player, long session, int revision, boolean ok, String message) {
        FusionNetwork.sendToPlayer(player, new EditorResult(session, revision, ok, message));
    }

    public static void closed(ServerPlayer player, long session) {
    }
}
