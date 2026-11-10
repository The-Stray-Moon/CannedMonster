package cc.sighs.cannedmonster.fusion;

import java.util.IdentityHashMap;
import java.util.Map;

public final class CreationTickets {
    private final Map<Object, Object> sources = new IdentityHashMap<>();

    public void record(Object child, Object source) {
        if (child != null && source != null && child != source) sources.put(child, source);
    }

    public boolean claim(Object child, Object source) {
        if (sources.get(child) != source || source == null) return false;
        sources.remove(child);
        return true;
    }
}
