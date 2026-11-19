package cc.sighs.cannedmonster.fusion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FusionAveragesTest {
    @Test
    void flattenedOriginsKeepEveryIndividualsWeight() {
        var origins = List.of(Map.of("health", 10.0), Map.of("health", 30.0), Map.of("health", 80.0));
        assertEquals(40.0, AttributeAverages.mean(origins).get("health"), 1.0E-9);
        assertEquals(origins, List.of(Map.of("health", 10.0), Map.of("health", 30.0), Map.of("health", 80.0)));
    }

    @Test
    void missingAttributesDoNotContributeZero() {
        assertEquals(Map.of("health", 20.0, "attack", 6.0), AttributeAverages.mean(
                List.of(Map.of("health", 10.0, "attack", 6.0), Map.of("health", 30.0))));
    }

    @Test
    void repeatedMembersRemainSeparateIndividuals() {
        assertEquals(20.0, AttributeAverages.mean(
                List.of(Map.of("health", 10.0), Map.of("health", 10.0), Map.of("health", 40.0))).get("health"), 1.0E-9);
    }

    @Test
    void nonFiniteOriginsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AttributeAverages.mean(List.of(Map.of("health", Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> AttributeAverages.mean(List.of(Map.of("health", Double.POSITIVE_INFINITY))));
    }
}
