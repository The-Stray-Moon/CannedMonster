package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSpeedTest {
    @Test
    void standardGroundMobSpeedUsesItsSquaredNativeDrive() {
        assertEquals(0.04, PlayerSpeed.equivalentGround(0.2), 1.0E-9);
        assertEquals(0.0529, PlayerSpeed.equivalentGround(0.23), 1.0E-9);
        assertEquals(0.16, PlayerSpeed.equivalentGround(0.4), 1.0E-9);
    }

    @Test
    void averageKeepsPlayerBaseAndEveryMember() {
        assertEquals(0.07, PlayerSpeed.target(0.1, new double[]{0.04}), 1.0E-9);
        assertEquals(0.06, PlayerSpeed.target(0.1, new double[]{0.04, 0.04}), 1.0E-9);
        assertEquals(0.13, PlayerSpeed.target(0.1, new double[]{0.16}), 1.0E-9);
    }

    @Test
    void unsupportedPipelinesContributeThePlayerBaseAsNeutral() {
        assertEquals(0.1, PlayerSpeed.target(0.1, new double[]{Double.NaN}), 1.0E-9);
        assertEquals(0.08, PlayerSpeed.target(0.1, new double[]{Double.NaN, 0.04}), 1.0E-9);
    }

    @Test
    void migrationOnlyReplacesTheOldAveragedBaseWithThePlayersOwnValue() {
        assertTrue(PlayerSpeed.needsMigration(0.07, 0.1));
        assertTrue(PlayerSpeed.matchesMean(0.07, 0.07));
        assertFalse(PlayerSpeed.needsMigration(0.1, 0.1));
        assertFalse(PlayerSpeed.matchesMean(0.12, 0.07));
    }
}
