package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightRulesTest {
    @Test
    void defaultExtraCoversCustomFlyers() {
        assertTrue(FlightRules.DEFAULT_EXTRA.contains("minecraft:vex"));
        assertTrue(FlightRules.DEFAULT_EXTRA.contains("minecraft:bat"));
        assertTrue(FlightRules.DEFAULT_EXTRA.contains("minecraft:ender_dragon"));
    }
}
