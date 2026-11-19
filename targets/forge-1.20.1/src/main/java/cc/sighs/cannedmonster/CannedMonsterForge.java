package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionNetwork;
import cc.sighs.cannedmonster.fusion.FusionPursuit;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.fusion.PlayerFusion;
import cc.sighs.cannedmonster.fusion.RidingSync;
import cc.sighs.cannedmonster.fusion.visual.AppearanceSync;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

@Mod(CannedMonsterForge.MOD_ID)
public final class CannedMonsterForge {
    public static final String MOD_ID = "cannedmonster";
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));

    public CannedMonsterForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FlightConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> FlightConfig.apply(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> FlightConfig.apply(event));
        CanRegistration.register(modBus);
        FusionNetwork.register();
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.LevelTickEvent event) -> levelTick(event));
        bus.addListener((PlayerEvent.Clone event) -> clone(event));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> dimension(event));
        bus.addListener((PlayerEvent.StartTracking event) -> tracking(event));
        bus.addListener((EntityLeaveLevelEvent event) -> leave(event));
        bus.addListener((PlayerInteractEvent.EntityInteract event) -> canInteraction(event));
        bus.addListener((RegisterCommandsEvent event) -> registerCommands(event));
        bus.addListener((ServerStoppedEvent event) -> stop(event));
        FusionRuntime.initialize();
    }

    private void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        for (Entity entity : level.getAllEntities()) {
            RidingSync.tick(entity);
            AppearanceSync.tick(entity);
            FusionPursuit.tick(entity);
        }
    }

    private void clone(PlayerEvent.Clone event) {
        PlayerFusion.copy(event.getOriginal(), event.getEntity());
    }

    private void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PlayerFusion.dimension(event.getEntity());
    }

    private void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AppearanceSync.tracking(event.getTarget(), player);
        RidingSync.tracking(event.getTarget(), player);
    }

    private void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.isRemoved()
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
        }
        AppearanceSync.leave(event.getEntity());
        RidingSync.leave(event.getEntity());
        if (event.getLevel().isClientSide) cc.sighs.cannedmonster.fusion.visual.ClientRiding.leave(event.getEntity());
    }

    private void stop(ServerStoppedEvent event) {
        FusionRuntime.clear();
        AppearanceSync.stop();
        RidingSync.clear();
    }

    private void canInteraction(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof cc.sighs.cannedmonster.can.MonsterCanItem can)) return;
        if (event.getEntity().isSpectator()) return;
        InteractionResult result = InteractionResult.PASS;
        if (!can.throwable() && can.storesMobs()) {
            result = event.getTarget() instanceof net.minecraft.world.entity.LivingEntity living
                    ? can.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand())
                    : InteractionResult.FAIL;
        }
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fusion").requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("first", ResourceLocationArgument.id())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                .then(Commands.argument("body", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                        .executes(context -> {
                                            ResourceLocation first = ResourceLocationArgument.getId(context, "first");
                                            ResourceLocation body = ResourceLocationArgument.getId(context, "body");
                                            var source = context.getSource();
                                            var firstType = BuiltInRegistries.ENTITY_TYPE.getOptional(first).orElseThrow(() -> ERROR.create("Unknown entity: " + first));
                                            var bodyType = BuiltInRegistries.ENTITY_TYPE.getOptional(body).orElseThrow(() -> ERROR.create("Unknown entity: " + body));
                                            Entity firstEntity = firstType.create(source.getLevel());
                                            Entity bodyEntity = bodyType.create(source.getLevel());
                                            if (!(firstEntity instanceof Mob facet) || !(bodyEntity instanceof Mob host)) {
                                                throw ERROR.create("Both fusion members must be mobs");
                                            }
                                            var position = source.getPosition();
                                            host.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
                                            FusionRuntime.attach(host, List.of(facet));
                                            if (!source.getLevel().addFreshEntity(host)) {
                                                FusionRuntime.detach(host);
                                                throw ERROR.create("The level rejected the fusion body");
                                            }
                                            source.sendSuccess(() -> Component.literal("Fusion: " + first + " -> " + body + " [" + host.getUUID() + "]"), true);
                                            return 1;
                                        })))));
    }
}
