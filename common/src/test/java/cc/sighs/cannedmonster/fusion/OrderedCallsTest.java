package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderedCallsTest {
    @Test
    void preservesPrefixSuffixOrderAndDistinctCalls() {
        List<String> trace = new ArrayList<>();
        Native host = new Native("host", trace);
        Native facet = new Native("facet", trace);
        bind(host, facet);
        try {
            host.tick();
            assertEquals(List.of("facet.before", "host.before", "body", "facet.ai", "host.ai", "host.after", "facet.after"), trace);
            host.tick();
            assertEquals(2, host.bodyTicks);
            assertEquals(2, facet.aiCalls);
            assertEquals(2, host.aiCalls);
        } finally {
            unbind(host, facet);
        }
    }

    @Test
    void missingSuperDoesNotPreventLaterMemberFromRunning() {
        Native host = new Native("host", new ArrayList<>());
        Native facet = new Native("facet", host.trace);
        facet.skipSuper = true;
        bind(host, facet);
        try {
            host.tick();
            assertEquals(1, host.bodyTicks);
            assertEquals(List.of("facet.before", "facet.after", "host.before", "body", "facet.ai", "host.ai", "host.after"), host.trace);
        } finally {
            unbind(host, facet);
        }
    }

    @Test
    void exceptionDoesNotLeaveAStaleExecutionFrame() {
        Native host = new Native("host", new ArrayList<>());
        Native facet = new Native("facet", host.trace);
        facet.fail = true;
        bind(host, facet);
        try {
            assertThrows(IllegalStateException.class, host::tick);
            facet.fail = false;
            host.tick();
            assertEquals(1, host.bodyTicks);
            assertEquals(1, host.aiCalls);
        } finally {
            unbind(host, facet);
        }
    }

    private static void bind(Native host, Native facet) {
        BodyLinks.bind(facet, host, facet.data, host.data);
        OrderedCalls.bind(host, List.of(facet), ignored -> {});
    }

    private static void unbind(Native host, Native facet) {
        OrderedCalls.unbind(host);
        BodyLinks.unbind(facet, facet.data);
    }

    public static final class Native implements BoundObject {
        final String name;
        final List<String> trace;
        final Object data = new Object();
        Object body;
        int bodyTicks;
        int aiCalls;
        boolean skipSuper;
        boolean fail;

        Native(String name, List<String> trace) {
            this.name = name;
            this.trace = trace;
        }

        public void tick() {
            if (OrderedCalls.dispatch(this, "tick", false)) {
                return;
            }
            if (fail) {
                throw new IllegalStateException("native failure");
            }
            trace.add(name + ".before");
            if (!skipSuper) {
                baseTick();
            }
            trace.add(name + ".after");
        }

        private void baseTick() {
            if (OrderedCalls.dispatch(this, "tick", true)) {
                return;
            }
            bodyTicks++;
            trace.add("body");
            aiStep();
        }

        public void aiStep() {
            if (OrderedCalls.dispatch(this, "aiStep", false)) {
                return;
            }
            aiCalls++;
            trace.add(name + ".ai");
            OrderedCalls.dispatch(this, "aiStep", true);
        }

        @Override
        public Object cannedmonster$body() {
            return body;
        }

        @Override
        public void cannedmonster$body(Object body) {
            this.body = body;
        }
    }
}
