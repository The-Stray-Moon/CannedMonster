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

import java.util.List;

@Mod(CannedMonsterNeoForge121.MOD_ID)
public final class CannedMonsterNeoForge121 {
    public static final String MOD_ID = "cannedmonster";
    private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));

    public CannedMonsterNeoForge121() {
        FusionRuntime.initialize();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::leave);
        NeoForge.EVENT_BUS.addListener(this::stop);
    }

    private void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.isRemoved()
                && !cc.sighs.cannedmonster.fusion.RemovalCalls.active(mob)) {
            FusionRuntime.detach(mob);
        }
    }

    private void stop(ServerStoppedEvent event) {
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
    }
}

