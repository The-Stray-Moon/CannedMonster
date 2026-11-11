package cc.sighs.cannedmonster.fusion;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttributeAveragesTest {
    @Test
    void averagesAllMembersInsteadOfAveragesOfGroups() {
        var origins = List.of(Map.of("health", 10.0), Map.of("health", 30.0), Map.of("health", 80.0));
        assertEquals(40, AttributeAverages.mean(origins).get("health"), 0.000001);
    }

    @Test
    void absentAttributesAreNotZeroes() {
        var origins = List.of(Map.of("health", 10.0, "attack", 6.0), Map.of("health", 30.0));
        assertEquals(Map.of("health", 20.0, "attack", 6.0), AttributeAverages.mean(origins));
    }

    @Test
    void countsRepeatedMembersIndividually() {
        assertEquals(20, AttributeAverages.mean(List.of(Map.of("health", 10.0), Map.of("health", 10.0), Map.of("health", 40.0))).get("health"), 0.000001);
    }

    @Test
    void persistsOriginsWithoutFlatteningTheirValues() {
        var id = ResourceLocation.parse("minecraft:generic.max_health");
        var origins = List.of(Map.of(id, 10.0), Map.of(id, 30.0), Map.of(id, 80.0));
        var restored = AttributeAverages.load(AttributeAverages.save(origins), 3);
        assertEquals(origins, restored);
        assertEquals(40, AttributeAverages.mean(restored).get(id), 0.000001);
    }

    @Test
    void rejectsInvalidOrigins() {
        assertThrows(IllegalArgumentException.class, () -> AttributeAverages.mean(List.of(Map.of("health", Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> AttributeAverages.mean(List.of(Map.of("health", Double.POSITIVE_INFINITY))));
        assertThrows(IllegalArgumentException.class, () -> AttributeAverages.load(new net.minecraft.nbt.ListTag(), 2));
    }
}
