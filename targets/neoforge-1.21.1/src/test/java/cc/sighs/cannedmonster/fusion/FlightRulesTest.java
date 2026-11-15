package cc.sighs.cannedmonster.fusion;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightRulesTest {
    @Test
    void defaultExtraCoversCustomFlyers() {
        assertTrue(FlightRules.DEFAULT_EXTRA.contains(ResourceLocation.parse("minecraft:vex")));
        assertTrue(FlightRules.DEFAULT_EXTRA.contains(ResourceLocation.parse("minecraft:bat")));
        assertTrue(FlightRules.DEFAULT_EXTRA.contains(ResourceLocation.parse("minecraft:ender_dragon")));
    }
}
