package cc.sighs.cannedmonster.can;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Monotonic carousel/rotation clock for can previews. Spatial cans show one resident at a time in
 * LIFO order (the newest resident first) and advance every {@link #ROTATE_MILLIS}. A stable key
 * anchors the first slot to the moment that item/space revision is first previewed, so opening a
 * tooltip always starts at the newest resident instead of a process-wide phase.
 */
public final class CanPreviewSelection {
    public static final long ROTATE_MILLIS = 15000L;
    private static final int MAX_TRACKS = 256;
    private static final Map<String, Track> TRACKS = new ConcurrentHashMap<>();

    private static final class Track {
        private long start;
        private long last;

        private Track(long now) {
            this.start = now;
            this.last = now;
        }
    }

    private CanPreviewSelection() {}

    public static long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    public static int index(int count) {
        return index(count, nowMillis());
    }

    public static int index(int count, long millis) {
        if (count <= 0) return -1;
        long slot = Math.floorDiv(millis, ROTATE_MILLIS);
        return (int) Math.floorMod(slot, count);
    }

    public static int index(String key, int count) {
        return index(key, count, nowMillis());
    }

    public static int index(String key, int count, long millis) {
        if (count <= 0) return -1;
        Track track = TRACKS.computeIfAbsent(key, ignored -> new Track(millis));
        if (TRACKS.size() > MAX_TRACKS) trim();
        long elapsed;
        synchronized (track) {
            if (millis - track.last > ROTATE_MILLIS) track.start = millis;
            track.last = millis;
            elapsed = Math.max(0L, millis - track.start);
        }
        return index(count, elapsed);
    }

    public static void clear() {
        TRACKS.clear();
    }

    private static void trim() {
        Iterator<String> keys = TRACKS.keySet().iterator();
        while (TRACKS.size() > MAX_TRACKS && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }
}
