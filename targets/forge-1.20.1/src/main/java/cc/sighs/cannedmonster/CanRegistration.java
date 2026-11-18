package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.MonsterCanItem;
import cc.sighs.cannedmonster.can.ThrownCan;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public final class CanRegistration {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CannedMonsterForge.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CannedMonsterForge.MOD_ID);

    public static final RegistryObject<EntityType<ThrownCan>> THROWN_CAN = ENTITIES.register("thrown_can",
            () -> EntityType.Builder.<ThrownCan>of(ThrownCan::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(5).build("cannedmonster:thrown_can"));
    public static final RegistryObject<Item> IRON_CAN = ITEMS.register("iron_can", () -> new Item(new Item.Properties()));
    public static final RegistryObject<MonsterCanItem> MONSTER_CAN = ITEMS.register("monster_can", () -> new MonsterCanItem(false, false, false, false, THROWN_CAN));
    public static final RegistryObject<MonsterCanItem> THROWABLE_MONSTER_CAN = ITEMS.register("throwable_monster_can", () -> new MonsterCanItem(false, true, false, false, THROWN_CAN));
    public static final RegistryObject<Item> LARGE_IRON_CAN = ITEMS.register("large_iron_can", () -> new Item(new Item.Properties()));
    public static final RegistryObject<MonsterCanItem> LARGE_MONSTER_CAN = ITEMS.register("large_monster_can", () -> new MonsterCanItem(true, false, false, false, THROWN_CAN));
    public static final RegistryObject<MonsterCanItem> THROWABLE_LARGE_MONSTER_CAN = ITEMS.register("throwable_large_monster_can", () -> new MonsterCanItem(true, true, false, false, THROWN_CAN));
    public static final RegistryObject<MonsterCanItem> SPATIAL_MONSTER_CAN = ITEMS.register("spatial_monster_can", () -> new MonsterCanItem(true, false, false, true, THROWN_CAN));
    public static final RegistryObject<MonsterCanItem> RANDOM_MONSTER_CAN = ITEMS.register("random_monster_can", () -> new MonsterCanItem(false, true, true, false, THROWN_CAN));

    private CanRegistration() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(CanRegistration::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (var item : List.of(IRON_CAN, MONSTER_CAN, THROWABLE_MONSTER_CAN, LARGE_IRON_CAN, LARGE_MONSTER_CAN,
                    THROWABLE_LARGE_MONSTER_CAN, SPATIAL_MONSTER_CAN, RANDOM_MONSTER_CAN)) event.accept(item.get());
        }
    }
}
