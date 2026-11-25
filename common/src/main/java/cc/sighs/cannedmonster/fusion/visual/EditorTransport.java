package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;

/**
 * Client-to-server editor channel. The transport itself belongs to the version backend; this class
 * is the single entry point the editor UI calls. {@link #save} commits a scope and
 * {@link #ack} closes (or reports) the session so the server can unfreeze its target.
 */
public final class EditorTransport {
    public interface Sender {
        void save(long session, int revision, int scope, CompoundTag layout);

        void ack(long session, int revision, boolean ok, String message);
    }

    private static Sender sender;

    private EditorTransport() {}

    public static void bind(Sender value) {
        sender = value;
    }

    public static boolean bound() {
        return sender != null;
    }

    public static void save(long session, int revision, int scope, CompoundTag layout) {
        Sender target = sender;
        if (target != null) target.save(session, revision, scope, layout == null ? new CompoundTag() : layout.copy());
    }

    public static void ack(long session, int revision, boolean ok, String message) {
        Sender target = sender;
        if (target != null) target.ack(session, revision, ok, message == null ? "" : message);
    }
}
