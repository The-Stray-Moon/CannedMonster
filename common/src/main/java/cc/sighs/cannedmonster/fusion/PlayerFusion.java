package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.can.CanActions;
import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.can.MonsterCanItem;
import cc.sighs.cannedmonster.fusion.visual.VisualLayoutResolver;
import cc.sighs.cannedmonster.fusion.visual.VisualViews;
import com.mojang.logging.LogUtils;
import cc.sighs.cannedmonster.platform.PlayerAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerFusion {
    private static final Map<Player, CompoundTag> UNRESOLVED = new IdentityHashMap<>();

    private PlayerFusion() {}

    public static List<Mob> facets(Player player) {
        return FusionRuntime.facets(player);
    }

    private static boolean valid(Player player, ItemStack stack) {
        return !player.level().isClientSide() && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !UNRESOLVED.containsKey(player) && stack.getItem() instanceof MonsterCanItem item && item.storesMobs()
                && !cc.sighs.cannedmonster.fusion.visual.VisualEditorServer.frozen(player);
    }

    public static boolean fuse(Player player, ItemStack stack) {
        if (!valid(player, stack) || !CanContents.valid(stack)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        Mob incoming = null;
        try {
            incoming = CanActions.restore(stack, level);
            if (incoming == null) return false;
            FusionRuntime.prepend(player, incoming);
            CanContents.clear(stack);
            return true;
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Cannot fuse can into player: {}", failure.toString());
            PlayerAccess.message(player, Component.translatable("message.cannedmonster.failed"));
            return false;
        } finally {
            if (incoming != null && !BodyLinks.isFacet(incoming)) FusionRuntime.detach(incoming);
        }
    }

    public static boolean extract(Player player, ItemStack stack) {
        if (!valid(player, stack) || CanContents.filled(stack) || facets(player).isEmpty()) return false;
        MonsterCanItem item = (MonsterCanItem) stack.getItem();
        try {
            ItemStack prepared = stack.copyWithCount(1);
            if (item.storesFusion()) {
                List<Mob> members = facets(player);
                var stored = VisualViews.stored(player);
                CanContents.store(prepared, FusionRuntime.packAll(player), "", members.size());
                if (stored != null && stored.layerCount() == members.size()) {
                    VisualLayoutResolver.writeEmbedded(prepared, stored);
                }
                FusionRuntime.stripAll(player);
            } else {
                Mob member = facets(player).get(0);
                CanContents.store(prepared, FusionRuntime.independentSave(member), member.getName().getString(), 1);
                FusionRuntime.peel(player);
            }
            PlayerAccess.copyContents(prepared, stack);
            return true;
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Cannot extract player fusion member: {}", failure.toString());
            return false;
        }
    }

    static void preserve(Player player, CompoundTag fusion, RuntimeException failure) {
        UNRESOLVED.put(player, fusion.copy());
        LogUtils.getLogger().error("Player fusion is unavailable; preserving original payload for {}", player.getUUID(), failure);
    }

    static void resolved(Player player) {
        UNRESOLVED.remove(player);
    }

    static boolean saveUnresolved(Player player, CompoundTag tag) {
        CompoundTag pending = UNRESOLVED.get(player);
        if (pending == null) return false;
        tag.put(FusionRuntime.SAVE_KEY, pending.copy());
        return true;
    }

    public static void copy(Player original, Player fresh) {
        if (fresh.level().isClientSide()) return;
        CompoundTag saved = new CompoundTag();
        FusionRuntime.save(original, saved);
        FusionRuntime.detach(original);
        UNRESOLVED.remove(original);
        FusionRuntime.load(fresh, saved);
    }

    public static void logout(Player player) {
        FusionRuntime.detach(player);
        cc.sighs.cannedmonster.fusion.visual.VisualViews.discard(player);
        UNRESOLVED.remove(player);
    }

    public static void dimension(Player player) {
        if (facets(player).isEmpty()) return;
        CompoundTag saved = new CompoundTag();
        FusionRuntime.save(player, saved);
        FusionRuntime.detach(player);
        FusionRuntime.load(player, saved);
    }

    public static void clear() {
        UNRESOLVED.clear();
    }
}
