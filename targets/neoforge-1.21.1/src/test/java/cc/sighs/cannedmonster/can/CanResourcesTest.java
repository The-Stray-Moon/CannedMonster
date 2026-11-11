package cc.sighs.cannedmonster.can;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanResourcesTest {
    private static final List<String> ITEMS = List.of("iron_can", "monster_can", "throwable_monster_can", "large_iron_can",
            "large_monster_can", "throwable_large_monster_can", "spatial_monster_can", "random_monster_can");

    @Test
    void eightTexturesAreNativeSixteenPixelsAndModelsResolve() throws Exception {
        for (String item : ITEMS) {
            try (var input = getClass().getResourceAsStream("/assets/cannedmonster/textures/item/" + item + ".png")) {
                assertNotNull(input, item);
                var image = ImageIO.read(input);
                assertEquals(16, image.getWidth(), item);
                assertEquals(16, image.getHeight(), item);
            }
            try (var input = getClass().getResourceAsStream("/assets/cannedmonster/models/item/" + item + ".json")) {
                assertNotNull(input, item);
                var json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("cannedmonster:item/" + item, json.getAsJsonObject("textures").get("layer0").getAsString());
            }
        }
    }

    @Test
    void recipesUseOnlyBlankIronContainersAndLeaveSpatialUncraftable() throws Exception {
        for (String item : ITEMS) {
            try (var input = getClass().getResourceAsStream("/data/cannedmonster/recipe/" + item + ".json")) {
                if (item.equals("spatial_monster_can")) { assertNull(input); continue; }
                assertNotNull(input, item);
                var json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("cannedmonster:" + item, json.getAsJsonObject("result").get("id").getAsString());
                if (!item.contains("iron")) {
                    String base = item.contains("large") || item.startsWith("random") ? "large_iron_can" : "iron_can";
                    assertEquals("cannedmonster:" + base, json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
                }
            }
        }
    }
}
