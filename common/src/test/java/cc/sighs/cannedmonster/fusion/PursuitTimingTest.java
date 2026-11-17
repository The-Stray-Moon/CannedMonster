package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PursuitTimingTest {
    @Test
    void singleQueryRemainsPendingUntilItsRefreshWindowExpires() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        assertTrue(timing.update(1, 10.0));
        assertFalse(timing.active(1));
        assertFalse(timing.direct());
        assertFalse(timing.update(41, 10.0));
        assertFalse(timing.direct());
    }

    @Test
    void separateQueriesCanAccumulateWithoutDuplicateSuperRegistrations() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        for (int tick = 1; tick < 20; tick++) assertTrue(timing.update(tick, 10.0));
        timing.refresh(20, 10.0, true);
        assertTrue(timing.active(20));
        assertTrue(timing.update(40, 10.0));
        assertTrue(timing.direct());
    }

    @Test
    void stalledWindowFallsBackToDirectSteering() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        timing.refresh(10, 10.0, true);
        assertTrue(timing.update(10, 10.0));
        assertFalse(timing.direct());
        for (int tick = 11; tick <= 50; tick++) {
            assertTrue(timing.update(tick, 10.0));
        }
        assertTrue(timing.direct());
    }

    @Test
    void progressInsideTheWindowKeepsNativeMovement() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        timing.refresh(1, 10.0, true);
        for (int tick = 1; tick <= 120; tick++) {
            double distance = 10.0 - tick * 0.1;
            timing.refresh(tick, distance, true);
            assertTrue(timing.update(tick, distance));
        }
        assertFalse(timing.direct());
    }

    @Test
    void arrivedMobIsNotPushedFurther() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 2.5, false);
        timing.refresh(1, 2.5, true);
        for (int tick = 1; tick <= 120; tick++) {
            if (tick % 20 == 0) timing.refresh(tick, 2.5, true);
            assertTrue(timing.update(tick, 2.5));
        }
        assertFalse(timing.direct());
    }

    @Test
    void expiredIntentIsDropped() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        timing.refresh(1, 10.0, true);
        assertTrue(timing.update(1, 10.0));
        assertFalse(timing.update(50, 10.0));
        assertFalse(timing.direct());
    }

    @Test
    void changedTargetRestartsProgressTracking() {
        PursuitTiming timing = new PursuitTiming();
        timing.refresh(0, 10.0, false);
        timing.refresh(1, 10.0, true);
        for (int tick = 2; tick <= 60; tick++) {
            if (tick % 20 == 0) timing.refresh(tick, 10.0, true);
            timing.update(tick, 10.0);
        }
        assertTrue(timing.direct());
        timing.refresh(61, 10.0, false);
        assertFalse(timing.direct());
    }
}
