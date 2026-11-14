package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PlayerMemberMobMixin {
    private Player cannedmonster$player() {
        Object body = BodyLinks.body(this);
        return body instanceof Player player ? player : null;
    }

    @org.spongepowered.asm.mixin.Unique
    private Player cannedmonster$equipmentPlayer() {
        if (cc.sighs.cannedmonster.fusion.OrderedCalls.currentMember() == (Object) this
                || cc.sighs.cannedmonster.fusion.HurtCalls.currentMember() == (Object) this) return null;
        return cannedmonster$player();
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

    @Inject(method = "getHandSlots", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$hands(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        Player player = cannedmonster$equipmentPlayer();
        if (player != null) cir.setReturnValue(player.getHandSlots());
    }

    @Inject(method = "getArmorSlots", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$armor(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        Player player = cannedmonster$equipmentPlayer();
        if (player != null) cir.setReturnValue(player.getArmorSlots());
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$target(LivingEntity target, CallbackInfo ci) {
        Player player = cannedmonster$player();
        if (player != null && target != null && !cc.sighs.cannedmonster.fusion.FusionTraits.allowedTarget(player, target)) ci.cancel();
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$attack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Player player = cannedmonster$player();
        if (player != null && !cc.sighs.cannedmonster.fusion.FusionTraits.allowedTarget(player, target)) cir.setReturnValue(false);
    }
}
