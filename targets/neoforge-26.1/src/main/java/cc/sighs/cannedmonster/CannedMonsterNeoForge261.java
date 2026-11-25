package cc.sighs.cannedmonster;

import cc.sighs.cannedmonster.fusion.FusionRuntime;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;

@Mod(CannedMonsterNeoForge261.MOD_ID)
public final class CannedMonsterNeoForge261 {
    public static final String MOD_ID = "cannedmonster";
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));

    public CannedMonsterNeoForge261(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, FlightConfig.SPEC);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> FlightConfig.apply(event));
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> FlightConfig.apply(event));
        CanRegistration.register(modBus);
        cc.sighs.cannedmonster.platform.EditorNetwork.register(modBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.left(player);
        });
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("4").playToClient(cc.sighs.cannedmonster.fusion.visual.FusionAppearance.TYPE,
                        cc.sighs.cannedmonster.fusion.visual.FusionAppearance.CODEC,
                        (packet, context) -> cc.sighs.cannedmonster.fusion.visual.ClientAppearance.receive(packet)));
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("3").playToClient(cc.sighs.cannedmonster.fusion.RidingState.TYPE,
                        cc.sighs.cannedmonster.fusion.RidingState.CODEC,
                        (packet, context) -> cc.sighs.cannedmonster.fusion.visual.ClientRiding.receive(packet)));
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) ->
                event.registrar("4").playToClient(cc.sighs.cannedmonster.fusion.visual.MemberEvent.TYPE,
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
        FusionRuntime.initialize();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::leave);
        NeoForge.EVENT_BUS.addListener(this::stop);
    }

    private void canInteraction(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof cc.sighs.cannedmonster.can.MonsterCanItem can)) return;
        if (event.getEntity().isSpectator()) return;
        net.minecraft.world.InteractionResult result = net.minecraft.world.InteractionResult.PASS;
        if (!can.throwable() && can.storesMobs()) {
            result = event.getTarget() instanceof net.minecraft.world.entity.LivingEntity living
                    ? can.interactLivingEntity(event.getItemStack(), event.getEntity(), living, event.getHand())
                    : net.minecraft.world.InteractionResult.FAIL;
        }
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    private void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.isRemoved()
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
        }
    }

    private void stop(ServerStoppedEvent event) {
        cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.stop();
        cc.sighs.cannedmonster.fusion.visual.VisualDefaults.clear();
        FusionRuntime.clear();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fusion").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("first", IdentifierArgument.id())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                .then(Commands.argument("body", IdentifierArgument.id())
                                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                        .executes(context -> {
                                            Identifier first = IdentifierArgument.getId(context, "first");
                                            Identifier body = IdentifierArgument.getId(context, "body");
                                            var source = context.getSource();
                                            var firstType = BuiltInRegistries.ENTITY_TYPE.getOptional(first).orElseThrow(() -> ERROR.create("Unknown entity: " + first));
                                            var bodyType = BuiltInRegistries.ENTITY_TYPE.getOptional(body).orElseThrow(() -> ERROR.create("Unknown entity: " + body));
                                            Entity firstEntity = firstType.create(source.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                                            Entity bodyEntity = bodyType.create(source.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                                            if (!(firstEntity instanceof Mob facet) || !(bodyEntity instanceof Mob host)) {
                                                throw ERROR.create("Both fusion members must be mobs");
                                            }
                                            var position = source.getPosition();
                                            host.snapTo(position.x, position.y, position.z, source.getRotation().y, 0);
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

