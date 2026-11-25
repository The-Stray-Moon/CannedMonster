package cc.sighs.cannedmonster.fusion.visual;

import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.fusion.FusionRuntime;
import cc.sighs.cannedmonster.platform.CanStorage;
import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Resolves the layout hierarchy used by the editor and by released entities.
 *
 * <p>Order: the individual layout embedded inside the stored can entity (exact layer count), the
 * can item default in the ItemStack root (exact count), the player UUID default (exact count), the
 * server default (exact count), and finally an equal-height layout for that count. Defaults are
 * never resized; a preset for another composition is ignored instead of being silently stretched.
 */
public final class VisualLayoutResolver {
    public enum Source { INDIVIDUAL, ITEM, PLAYER, SERVER, DEFAULT }

    public record Resolved(VisualLayout layout, Source source) {
        public Resolved {
            if (layout == null) throw new IllegalArgumentException("Resolved layout cannot be null");
        }
    }

    private VisualLayoutResolver() {}

    /** Layout embedded in the entity saved inside {@code cannedmonster:contents}. */
    public static VisualLayout embedded(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !CanContents.valid(stack)) return null;
        CompoundTag entity = CanContents.entity(stack);
        return read(entity);
    }

    /** Item-wide default in the ItemStack root; independent of the contents and survives emptying. */
    public static VisualLayout itemDefault(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = CanStorage.compound(stack, VisualViews.SAVE_KEY);
        return tag.isEmpty() ? null : VisualLayout.load(tag);
    }

    public static boolean writeEmbedded(ItemStack stack, VisualLayout layout) {
        if (stack == null || stack.isEmpty() || !CanContents.valid(stack)) return false;
        try {
            CompoundTag entity = CanContents.entity(stack);
            write(entity, layout);
            return CanContents.replaceEntity(stack, entity);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static boolean writeItemDefault(ItemStack stack, VisualLayout layout) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (layout == null) CanStorage.remove(stack, VisualViews.SAVE_KEY);
            else CanStorage.put(stack, VisualViews.SAVE_KEY, layout.save());
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public static VisualLayout player(Player player, int layers) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        return VisualDefaults.player(level.getServer(), player.getUUID(), layers);
    }

    public static VisualLayout server(MinecraftServer server, int layers) {
        return VisualDefaults.server(server, layers);
    }

    public static void writePlayer(MinecraftServer server, Player player, int layers, VisualLayout layout) {
        if (server != null && player != null) VisualDefaults.writePlayer(server, player.getUUID(), layers, layout);
    }

    public static Resolved resolve(Player player, ItemStack stack, int layers) {
        int count = Math.max(0, layers);
        return select(embedded(stack), itemDefault(stack), player(player, count), server(player, count), count);
    }

    /**
     * Applies the release hierarchy when the restored entity has no individual layout:
     * item default, then the actor's UUID default, then the server default, each only for the
     * exact layer count. A restored entity layout always wins; when nothing matches, the entity is
     * left without a stored layout so the renderer keeps the legacy auto-fit.
     */
    public static void applyFallback(ItemStack stack, LivingEntity entity, Player actor) {
        if (stack == null || entity == null || VisualViews.stored(entity) != null) return;
        int layers = layers(entity);
        VisualLayout server = entity.level() instanceof ServerLevel level
                ? server(level.getServer(), layers) : server(actor, layers);
        Resolved resolved = select(null, itemDefault(stack), player(actor, layers), server, layers);
        if (resolved.source() != Source.DEFAULT) VisualViews.set(entity, resolved.layout());
    }

    /**
     * Pure precedence rule used by both the editor and release paths. Only exact layer counts are
     * accepted; a preset authored for another composition is ignored.
     */
    public static Resolved select(VisualLayout individual, VisualLayout item, VisualLayout player,
                                  VisualLayout server, int layers) {
        int count = Math.max(0, layers);
        if (exact(individual, count)) return new Resolved(individual, Source.INDIVIDUAL);
        if (exact(item, count)) return new Resolved(item, Source.ITEM);
        if (exact(player, count)) return new Resolved(player, Source.PLAYER);
        if (exact(server, count)) return new Resolved(server, Source.SERVER);
        return new Resolved(VisualLayout.defaults(count), Source.DEFAULT);
    }

    public static VisualLayout server(Player player, int layers) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        return server(level.getServer(), layers);
    }

    public static int layers(LivingEntity entity) {
        if (entity == null) return 1;
        try {
            return Math.max(1, FusionRuntime.facets(entity).size() + 1);
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    public static boolean exact(VisualLayout layout, int layers) {
        return layout != null && layout.layerCount() == layers;
    }

    public static void appendTooltip(ItemStack stack, Consumer<Component> tooltip) {
        VisualLayout embedded = embedded(stack);
        VisualLayout item = itemDefault(stack);
        if (embedded != null) {
            tooltip.accept(Component.translatable("tooltip.cannedmonster.layout_layers", embedded.layerCount())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (item != null) {
            tooltip.accept(Component.translatable("tooltip.cannedmonster.layout_default_layers", item.layerCount())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (stack != null && !stack.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.cannedmonster.layout_edit").withStyle(ChatFormatting.GRAY));
        }
    }

    private static VisualLayout read(CompoundTag parent) {
        CompoundTag tag = EntityStorage.compound(parent, VisualViews.SAVE_KEY);
        return tag.isEmpty() ? null : VisualLayout.load(tag);
    }

    private static void write(CompoundTag parent, VisualLayout layout) {
        if (layout == null) parent.remove(VisualViews.SAVE_KEY);
        else parent.put(VisualViews.SAVE_KEY, layout.save());
    }
}
