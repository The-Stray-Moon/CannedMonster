package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionSlotTest {
    @Test
    void nestedExecutionReturnsToItsOwnSource() {
        ExecutionSlot<Object> slot = new ExecutionSlot<>();
        Object first = new Object();
        Object second = new Object();
        assertEquals(7, slot.run(first, () -> {
            assertSame(first, slot.get());
            slot.run(second, () -> {
                assertSame(second, slot.get());
                return null;
            });
            assertSame(first, slot.get());
            return 7;
        }));
        assertNull(slot.get());
    }

    @Test
    void exceptionNeverLeavesAnExecutionCredential() {
        ExecutionSlot<Object> slot = new ExecutionSlot<>();
        assertThrows(IllegalStateException.class, () -> slot.run(new Object(), () -> {
            throw new IllegalStateException("native callback failed");
        }));
        assertNull(slot.get());
    }

    @Test
    void nestedFailurePreservesOuterCredential() {
        ExecutionSlot<Object> slot = new ExecutionSlot<>();
        Object outer = new Object();
        slot.run(outer, () -> {
            assertThrows(IllegalArgumentException.class, () -> slot.run(new Object(), () -> {
                throw new IllegalArgumentException();
            }));
            assertSame(outer, slot.get());
            return null;
        });
        assertNull(slot.get());
    }
}
