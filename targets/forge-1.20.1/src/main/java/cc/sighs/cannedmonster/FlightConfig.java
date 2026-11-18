package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FlightRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

public final class FlightConfig {
    private static final List<String> DEFAULT_EXTRA = List.of(
            "minecraft:vex", "minecraft:bat", "minecraft:ender_dragon");
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Flying fusion behaviour").push("flight");
        EXTRA = builder.comment("Mobs treated as flying in addition to the structural check.")
                .defineList("extra_flying_mobs", DEFAULT_EXTRA,
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        SPEC = builder.pop().build();
    }

    private FlightConfig() {
    }

    public static void apply(ModConfigEvent event) {
        if (!CannedMonsterForge.MOD_ID.equals(event.getConfig().getModId())) return;
        List<ResourceLocation> ids = new ArrayList<>();
        for (String value : EXTRA.get()) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) ids.add(id);
        }
        FlightRules.setExtraTypes(ids);
    }
}
