package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.can.MonsterCanItem;
import cc.sighs.cannedmonster.can.ThrownCan;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class CanRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CannedMonsterNeoForge261.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CannedMonsterNeoForge261.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownCan>> THROWN_CAN = ENTITIES.register("thrown_can",
            () -> EntityType.Builder.<ThrownCan>of(ThrownCan::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(4).updateInterval(5).build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(CannedMonsterNeoForge261.MOD_ID, "thrown_can"))));
    public static final DeferredHolder<Item, Item> IRON_CAN = ITEMS.registerSimpleItem("iron_can");
    public static final DeferredHolder<Item, MonsterCanItem> MONSTER_CAN = ITEMS.registerItem("monster_can",
            properties -> new MonsterCanItem(properties, false, false, false, false, THROWN_CAN));
    public static final DeferredHolder<Item, MonsterCanItem> THROWABLE_MONSTER_CAN = ITEMS.registerItem("throwable_monster_can",
            properties -> new MonsterCanItem(properties, false, true, false, false, THROWN_CAN));
    public static final DeferredHolder<Item, Item> LARGE_IRON_CAN = ITEMS.registerSimpleItem("large_iron_can");
    public static final DeferredHolder<Item, MonsterCanItem> LARGE_MONSTER_CAN = ITEMS.registerItem("large_monster_can",
            properties -> new MonsterCanItem(properties, true, false, false, false, THROWN_CAN));
    public static final DeferredHolder<Item, MonsterCanItem> THROWABLE_LARGE_MONSTER_CAN = ITEMS.registerItem("throwable_large_monster_can",
            properties -> new MonsterCanItem(properties, true, true, false, false, THROWN_CAN));
    public static final DeferredHolder<Item, MonsterCanItem> SPATIAL_MONSTER_CAN = ITEMS.registerItem("spatial_monster_can",
            properties -> new MonsterCanItem(properties, true, false, false, true, THROWN_CAN));
    public static final DeferredHolder<Item, MonsterCanItem> RANDOM_MONSTER_CAN = ITEMS.registerItem("random_monster_can",
            properties -> new MonsterCanItem(properties, false, true, true, false, THROWN_CAN));

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
