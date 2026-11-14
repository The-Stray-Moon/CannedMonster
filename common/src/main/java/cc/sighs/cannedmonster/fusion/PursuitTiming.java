package cc.sighs.cannedmonster.fusion;

final class PursuitTiming {
    private static final int REFRESH_TIMEOUT = 40;
    private static final int MIN_CALLS = 2;
    private static final int WINDOW = 40;
    private static final double REQUIRED_PROGRESS = 0.5;
    private static final double MIN_DIRECT_DISTANCE = 4.0;

    private int calls;
    private int lastTick;
    private int windowStart;
    private double windowDistance = Double.MAX_VALUE;
    private boolean direct;

    void reset() {
        calls = 0;
        lastTick = 0;
        windowStart = 0;
        windowDistance = Double.MAX_VALUE;
        direct = false;
    }

    void refresh(int tick, double distance, boolean sameTarget) {
        if (!sameTarget) reset();
        calls++;
        lastTick = tick;
        if (windowDistance == Double.MAX_VALUE) {
            windowStart = tick;
            windowDistance = distance;
        }
    }

    boolean update(int tick, double distance) {
        if (calls == 0 || tick - lastTick > REFRESH_TIMEOUT) {
            reset();
            return false;
        }
        if (calls < MIN_CALLS) {
            return false;
        }
        if (windowDistance == Double.MAX_VALUE) {
            windowStart = tick;
            windowDistance = distance;
            return true;
        }
        if (tick - windowStart < WINDOW) {
            return true;
        }
        if (distance > windowDistance - REQUIRED_PROGRESS && distance > MIN_DIRECT_DISTANCE) {
            direct = true;
        }
        windowStart = tick;
        windowDistance = distance;
        return true;
    }

    boolean direct() {
        return direct;
    }

    boolean active(int tick) {
        return calls >= MIN_CALLS && tick - lastTick <= REFRESH_TIMEOUT;
    }

    int calls() {
        return calls;
    }

    int window() {
        return windowStart;
    }

    double windowDistance() {
        return windowDistance;
    }
}
