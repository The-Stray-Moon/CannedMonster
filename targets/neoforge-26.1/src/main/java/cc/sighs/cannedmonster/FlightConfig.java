package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FlightRules;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class FlightConfig {
    private static final List<String> DEFAULT_EXTRA = List.of(
            "minecraft:vex", "minecraft:bat", "minecraft:ender_dragon");
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Flying fusion behaviour").push("flight");
        EXTRA = builder.comment("Mobs treated as flying in addition to the structural check.")
                .defineList("extra_flying_mobs", DEFAULT_EXTRA, () -> "minecraft:vex",
                        value -> value instanceof String id && Identifier.tryParse(id) != null);
        SPEC = builder.pop().build();
    }

    private FlightConfig() {
    }

    public static void apply(ModConfigEvent event) {
        if (!CannedMonsterNeoForge261.MOD_ID.equals(event.getConfig().getModId())) return;
        List<Identifier> ids = new ArrayList<>();
        for (String value : EXTRA.get()) {
            Identifier id = Identifier.tryParse(value);
            if (id != null) ids.add(id);
        }
        FlightRules.setExtraTypes(ids.stream().map(Object::toString).toList());
    }
}
