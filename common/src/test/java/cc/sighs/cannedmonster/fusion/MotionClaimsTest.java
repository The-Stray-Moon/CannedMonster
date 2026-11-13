package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotionClaimsTest {
    private boolean active = true;

    @Test
    void earlierMemberPreemptsTheBodyButNotTheOtherWayRound() {
        MotionClaims claims = new MotionClaims();
        Object facet = new Object();
        Object body = new Object();
        assertTrue(claims.claim(body, 1, () -> active));
        assertTrue(claims.claim(facet, 0, () -> active));
        assertEquals(facet, claims.owner(() -> active));
        assertFalse(claims.claim(body, 1, () -> active));
        assertEquals(facet, claims.owner(() -> active));
    }

    @Test
    void samePriorityKeepsTheFirstClaimant() {
        MotionClaims claims = new MotionClaims();
        Object first = new Object();
        Object second = new Object();
        assertTrue(claims.claim(first, 0, () -> active));
        assertFalse(claims.claim(second, 0, () -> active));
        assertEquals(first, claims.owner(() -> active));
    }

    @Test
    void queryPermissionDoesNotClaimThePath() {
        MotionClaims claims = new MotionClaims();
        Object facet = new Object();
        Object body = new Object();
        assertTrue(claims.claim(body, 1, () -> active));
        assertTrue(claims.allows(facet, 0, () -> active));
        assertEquals(body, claims.owner(() -> active));
        assertFalse(claims.allows(new Object(), 1, () -> active));
    }

    @Test
    void releaseOnlyClearsTheOwner() {
        MotionClaims claims = new MotionClaims();
        Object first = new Object();
        Object second = new Object();
        assertTrue(claims.claim(first, 0, () -> active));
        claims.release(second);
        assertEquals(first, claims.owner(() -> active));
        claims.release(first);
        assertNull(claims.owner(() -> active));
        assertTrue(claims.claim(second, 1, () -> active));
    }

    @Test
    void finishedPathReleasesTheOwner() {
        MotionClaims claims = new MotionClaims();
        Object first = new Object();
        Object second = new Object();
        assertTrue(claims.claim(first, 0, () -> active));
        active = false;
        assertTrue(claims.claim(second, 1, () -> active));
        active = true;
        assertEquals(second, claims.owner(() -> active));
    }

    @Test
    void runningMovementGoalKeepsTheClaimAfterItsPathEnds() {
        MotionClaims claims = new MotionClaims();
        Object facet = new Object();
        Object body = new Object();
        boolean[] runningGoal = {true};
        java.util.function.BooleanSupplier valid = () -> active || runningGoal[0];
        assertTrue(claims.claim(facet, 0, valid));
        active = false;
        assertSame(facet, claims.owner(valid));
        assertFalse(claims.claim(body, 1, valid));
        runningGoal[0] = false;
        assertNull(claims.owner(valid));
        assertTrue(claims.claim(body, 1, valid));
    }

    @Test
    void sameMemberRefreshesWithoutLosingTheClaim() {
        MotionClaims claims = new MotionClaims();
        Object first = new Object();
        assertTrue(claims.claim(first, 0, () -> active));
        active = false;
        assertTrue(claims.claim(first, 0, () -> active));
        active = true;
        assertEquals(first, claims.owner(() -> active));
    }
}
