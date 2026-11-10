package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreationTicketsTest {
    @Test
    void requiresExactRecordedSourceAndConsumesOnlyOnce() {
        var tickets = new CreationTickets();
        Object child = new Object(), source = new Object();
        assertFalse(tickets.claim(child, source));
        tickets.record(child, source);
        assertFalse(tickets.claim(child, new Object()));
        assertTrue(tickets.claim(child, source));
        assertFalse(tickets.claim(child, source));
    }

    @Test
    void usesIdentityAndDoesNotAuthorizeEqualOrUnrelatedObjects() {
        var tickets = new CreationTickets();
        Object child = new String("same"), other = new String("same"), source = new Object();
        tickets.record(child, source);
        assertFalse(tickets.claim(other, source));
        assertTrue(tickets.claim(child, source));
        tickets.record(null, source);
        tickets.record(child, null);
        tickets.record(source, source);
        assertFalse(tickets.claim(null, source));
        assertFalse(tickets.claim(child, null));
        assertFalse(tickets.claim(source, source));
    }
}
