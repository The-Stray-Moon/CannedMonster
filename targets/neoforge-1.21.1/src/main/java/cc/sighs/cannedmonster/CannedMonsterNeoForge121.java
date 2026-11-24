package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(CannedMonsterNeoForge121.MOD_ID)
public final class CannedMonsterNeoForge121 {
    public static final String MOD_ID = "cannedmonster";
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));
    private static final boolean SPACE_TEST_HOOKS = Boolean.getBoolean("cannedmonster.spaceTestHooks")
            || "true".equalsIgnoreCase(System.getenv("CANNEDMONSTER_SPACE_TEST_HOOKS"));
    private static final Map<String, Integer> CANCEL_JOINS = new HashMap<>();
    private static boolean cancelJoinHook;

    public CannedMonsterNeoForge121(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, FlightConfig.SPEC);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> FlightConfig.apply(event));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> FlightConfig.apply(event));
        CanRegistration.register(modBus);
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("3").playToClient(cc.sighs.cannedmonster.fusion.visual.FusionAppearance.TYPE,
                        cc.sighs.cannedmonster.fusion.visual.FusionAppearance.CODEC,
                        (packet, context) -> cc.sighs.cannedmonster.fusion.visual.ClientAppearance.receive(packet)));
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("3").playToClient(cc.sighs.cannedmonster.fusion.RidingState.TYPE,
                        cc.sighs.cannedmonster.fusion.RidingState.CODEC,
                        (packet, context) -> cc.sighs.cannedmonster.fusion.visual.ClientRiding.receive(packet)));
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("3").playToClient(cc.sighs.cannedmonster.fusion.visual.MemberEvent.TYPE,
                        cc.sighs.cannedmonster.fusion.visual.MemberEvent.CODEC,
                        (packet, context) -> cc.sighs.cannedmonster.fusion.visual.ClientAppearance.receive(packet)));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) ->
                cc.sighs.cannedmonster.fusion.RidingSync.tick(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) ->
                cc.sighs.cannedmonster.fusion.FusionPursuit.tick(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) ->
                cc.sighs.cannedmonster.fusion.PlayerFusion.copy(event.getOriginal(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) ->
                cc.sighs.cannedmonster.fusion.PlayerFusion.dimension(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.fusion.RidingSync.tracking(event.getTarget(), player);
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> cc.sighs.cannedmonster.fusion.RidingSync.leave(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) ->
                cc.sighs.cannedmonster.fusion.visual.AppearanceSync.tick(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.fusion.visual.AppearanceSync.tracking(event.getTarget(), player);
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) ->
                cc.sighs.cannedmonster.fusion.visual.AppearanceSync.leave(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> cc.sighs.cannedmonster.fusion.visual.AppearanceSync.stop());
        NeoForge.EVENT_BUS.addListener(this::canInteraction);
        NeoForge.EVENT_BUS.addListener(this::canInteractionSpecific);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                cc.sighs.cannedmonster.space.SpaceManager.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent event) ->
                cc.sighs.cannedmonster.space.SpaceManager.started(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) ->
                cc.sighs.cannedmonster.space.SpaceManager.saved(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Save event) -> {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                    && level.getServer() != null && level == level.getServer().overworld()) {
                cc.sighs.cannedmonster.space.SpaceManager.saved(level.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.space.SpaceManager.joined(player);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.space.SpaceManager.clientLeft(player);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) ->
                cc.sighs.cannedmonster.space.SpaceManager.entityDied(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.space.SpaceManager.abandon(player);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.space.SpaceManager.abandon(player);
        });
        FusionRuntime.initialize();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::leave);
        NeoForge.EVENT_BUS.addListener(this::stop);
    }

    private void canInteraction(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        interact(event.getEntity(), event.getItemStack(), event.getTarget(), event.getHand(), event::setCancellationResult, event::setCanceled);
    }

    private void canInteractionSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event) {
        interact(event.getEntity(), event.getItemStack(), event.getTarget(), event.getHand(), event::setCancellationResult, event::setCanceled);
    }

    private void interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack,
                          net.minecraft.world.entity.Entity target, net.minecraft.world.InteractionHand hand,
                          java.util.function.Consumer<net.minecraft.world.InteractionResult> resultSetter,
                          java.util.function.Consumer<Boolean> cancelSetter) {
        if (!(stack.getItem() instanceof cc.sighs.cannedmonster.can.MonsterCanItem can)) return;
        if (player.isSpectator() || can.throwable()) return;
        net.minecraft.world.InteractionResult result;
        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            result = can.interactLivingEntity(stack, player, living, hand);
        } else {
            result = net.minecraft.world.InteractionResult.FAIL;
        }
        // On the client a cancelled PASS would make vanilla try the item/air path and
        // send a use packet, which is exactly how a mob capture used to enter the space.
        resultSetter.accept(result);
        cancelSetter.accept(true);
    }

    private void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.isRemoved()
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
        }
        Entity entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
            cc.sighs.cannedmonster.space.SpaceManager.anchorItemRemoved(item, entity.getRemovalReason());
        }
        if (!entity.level().isClientSide() && cc.sighs.cannedmonster.space.SpaceManager.isSpaceOwned(entity) && entity.isRemoved()) {
            // UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER and CHANGED_DIMENSION are not
            // terminal: a resident riding a logging-out player must keep its ledger row.
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                cc.sighs.cannedmonster.space.SpaceManager.forget(entity);
            }
        }
    }

    private void stop(ServerStoppedEvent event) {
        CANCEL_JOINS.clear();
        cc.sighs.cannedmonster.space.SpaceManager.stop(event.getServer());
        FusionRuntime.clear();
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
        event.getDispatcher().register(Commands.literal("cannedmonster").requires(source -> source.hasPermission(2))
                .then(Commands.literal("space").then(Commands.literal("recover").executes(context -> {
                    if (!cc.sighs.cannedmonster.space.SpaceManager.recover(context.getSource().getPlayerOrException())) {
                        throw ERROR.create("No recoverable virtual-space anchor");
                    }
                    return 1;
                }))));
        // Test-only hook, absent from normal production sessions. Enable explicitly with
        // -Dcannedmonster.spaceTestHooks=true (or CANNEDMONSTER_SPACE_TEST_HOOKS=true).
        if (SPACE_TEST_HOOKS) {
            event.getDispatcher().register(Commands.literal("cannedmonster").requires(source -> source.hasPermission(2))
                    .then(Commands.literal("space").then(Commands.literal("canceljoin")
                            .then(Commands.argument("type", ResourceLocationArgument.id())
                                    .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                String type = ResourceLocationArgument.getId(context, "type").toString();
                                                int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count");
                                                CANCEL_JOINS.merge(type, count, Integer::sum);
                                                if (!cancelJoinHook) {
                                                    cancelJoinHook = true;
                                                    NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent join) -> {
                                                        if (!(join.getLevel() instanceof net.minecraft.server.level.ServerLevel)) return;
                                                        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                                                .getKey(join.getEntity().getType()).toString();
                                                        Integer left = CANCEL_JOINS.get(id);
                                                        if (left == null || left <= 0) return;
                                                        CANCEL_JOINS.put(id, left - 1);
                                                        join.setCanceled(true);
                                                    });
                                                }
                                                return 1;
                                            }))))));
        }
    }
}

