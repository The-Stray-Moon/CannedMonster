package cc.sighs.cannedmonster.fusion;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Consumer;

public final class OrderedCalls {
    private static final Map<Object, Session> SESSIONS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ClassValue<Map<String, MethodHandle>> METHODS = new ClassValue<>() {
        @Override
        protected Map<String, MethodHandle> computeValue(Class<?> type) {
            Map<String, MethodHandle> methods = new HashMap<>();
            for (String name : List.of("tick", "aiStep")) {
                try {
                    methods.put(name, MethodHandles.publicLookup().findVirtual(type, name, MethodType.methodType(void.class)));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Missing lifecycle entry " + type.getName() + "." + name, exception);
                }
            }
            return Map.copyOf(methods);
        }
    };

    private OrderedCalls() {
    }

    public static void bind(Object host, List<?> facets, Consumer<Object> aiBoundary) {
        List<Object> members = new ArrayList<>(facets);
        members.add(host);
        SESSIONS.put(host, new Session(List.copyOf(members), aiBoundary));
    }

    public static void unbind(Object host) {
        SESSIONS.remove(host);
    }

    public static boolean dispatch(Object receiver, String method, boolean boundary) {
        Object host = BodyLinks.body(receiver);
        Session session = SESSIONS.get(host);
        if (session == null) {
            return false;
        }
        Deque<Frame> stack = FRAMES.get();
        Frame active = stack.peek();
        if (active != null && active.session == session && active.method.equals(method)
                && active.current == receiver) {
            if (!boundary || receiver == host) {
                return false;
            }
            if (!active.advanced) {
                active.advanced = true;
                if (method.equals("aiStep")) {
                    session.aiBoundary.accept(receiver);
                }
                advance(active, active.index + 1);
            }
            return true;
        }
        if (receiver != host) {
            if (boundary && method.equals("aiStep")) {
                session.aiBoundary.accept(receiver);
            }
            return boundary;
        }
        Frame frame = new Frame(session, method);
        stack.push(frame);
        try {
            advance(frame, 0);
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                FRAMES.remove();
            }
        }
        return true;
    }

    private static void advance(Frame frame, int index) {
        if (index == frame.session.members.size()) {
            return;
        }
        Object previous = frame.current;
        int previousIndex = frame.index;
        boolean previousAdvanced = frame.advanced;
        Object receiver = frame.session.members.get(index);
        frame.current = receiver;
        frame.index = index;
        frame.advanced = false;
        try {
            METHODS.get(receiver.getClass()).get(frame.method).invoke(receiver);
            if (!frame.advanced) {
                advance(frame, index + 1);
            }
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Native fusion call failed: " + frame.method, exception);
        } finally {
            frame.current = previous;
            frame.index = previousIndex;
            frame.advanced = previousAdvanced;
        }
    }

    private record Session(List<Object> members, Consumer<Object> aiBoundary) {
    }

    private static final class Frame {
        final Session session;
        final String method;
        Object current;
        int index;
        boolean advanced;

        Frame(Session session, String method) {
            this.session = session;
            this.method = method;
        }
    }
}
