package cc.sighs.cannedmonster.fusion;

import cc.sighs.cannedmonster.can.CanActions;
import cc.sighs.cannedmonster.can.CanContents;
import cc.sighs.cannedmonster.can.MonsterCanItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
                && !UNRESOLVED.containsKey(player) && stack.getItem() instanceof MonsterCanItem item && item.storesMobs();
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
            if (player instanceof net.minecraft.server.level.ServerPlayer server) server.sendSystemMessage(Component.translatable("message.cannedmonster.failed"), true);
            else player.sendSystemMessage(Component.translatable("message.cannedmonster.failed"));
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
                CanContents.store(prepared, FusionRuntime.packAll(player), "", members.size());
                FusionRuntime.stripAll(player);
            } else {
                Mob member = facets(player).getFirst();
                CanContents.store(prepared, FusionRuntime.independentSave(member), member.getName().getString(), 1);
                FusionRuntime.peel(player);
            }
            stack.set(DataComponents.CUSTOM_DATA, prepared.get(DataComponents.CUSTOM_DATA));
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

    static CompoundTag unresolved(Player player) {
        return UNRESOLVED.get(player);
    }

    public static void clone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide()) copy(event.getOriginal(), event.getEntity());
    }

    public static void copy(Player original, Player fresh) {
        CompoundTag saved = FusionRuntime.saveTag(original);
        FusionRuntime.detach(original);
        UNRESOLVED.remove(original);
        if (saved != null) FusionRuntime.load(fresh, saved);
    }

    public static void logout(Player player) {
        FusionRuntime.detach(player);
        UNRESOLVED.remove(player);
    }

    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (facets(player).isEmpty()) return;
        CompoundTag saved = FusionRuntime.saveTag(player);
        FusionRuntime.detach(player);
        if (saved != null) FusionRuntime.load(player, saved);
    }

    public static void clear() {
        UNRESOLVED.clear();
    }
}
