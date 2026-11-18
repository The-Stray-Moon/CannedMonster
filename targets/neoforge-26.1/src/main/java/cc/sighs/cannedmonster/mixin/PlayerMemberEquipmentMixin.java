package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerMemberEquipmentMixin {
    @org.spongepowered.asm.mixin.Unique
    private Player cannedmonster$equipmentPlayer() {
        if (cc.sighs.cannedmonster.fusion.OrderedCalls.currentMember() == (Object) this
                || cc.sighs.cannedmonster.fusion.HurtCalls.currentMember() == (Object) this) return null;
        Object body = BodyLinks.body(this);
        if (body == this) return null;
        return body instanceof Player player ? player : null;
    }

    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$equipment(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        Player player = cannedmonster$equipmentPlayer();
        if (player != null) cir.setReturnValue(player.getItemBySlot(slot));
    }

    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$equipmentWrite(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        Player player = cannedmonster$equipmentPlayer();
        if (player != null) {
            player.setItemSlot(slot, stack);
            ci.cancel();
        }
    }
}
