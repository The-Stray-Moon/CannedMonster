package cc.sighs.cannedmonster.fusion;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class BodyLinks {
    private static final Map<Object, Object> FALLBACK = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, Integer> HOSTS = new IdentityHashMap<>();
    private static volatile Set<Object> sharedAccessors = Set.of();

    private BodyLinks() {
    }

    public static Object body(Object entity) {
        Object body = entity instanceof BoundObject bound ? bound.cannedmonster$body() : FALLBACK.get(entity);
        return body == null ? entity : body;
    }

    public static boolean same(Object first, Object second) {
        return first != null && second != null && body(first) == body(second);
    }

    public static boolean isFacet(Object entity) {
        return body(entity) != entity;
    }

    public static Object data(Object data, Object accessor) {
        return sharedAccessors.contains(accessor) ? body(data) : data;
    }

    public static synchronized void shareAccessor(Object accessor) {
        Set<Object> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        copy.addAll(sharedAccessors);
        copy.add(accessor);
        sharedAccessors = Collections.unmodifiableSet(copy);
    }

    public static synchronized void bind(Object facet, Object host, Object facetData, Object hostData) {
        if (facet == host || isFacet(host) || isFacet(facet) || HOSTS.containsKey(facet)) {
            throw new IllegalArgumentException("A body must be independent and a facet must be unbound");
        }
        set(facet, host);
        set(facetData, hostData);
        HOSTS.merge(host, 1, Integer::sum);
    }

    public static synchronized void unbind(Object facet, Object facetData) {
        Object host = body(facet);
        if (host != facet) {
            HOSTS.computeIfPresent(host, (key, count) -> count == 1 ? null : count - 1);
        }
        set(facet, null);
        set(facetData, null);
    }

    private static void set(Object object, Object body) {
        if (object instanceof BoundObject bound) {
            bound.cannedmonster$body(body);
        } else if (body == null) {
            FALLBACK.remove(object);
        } else {
            FALLBACK.put(object, body);
        }
    }
}
