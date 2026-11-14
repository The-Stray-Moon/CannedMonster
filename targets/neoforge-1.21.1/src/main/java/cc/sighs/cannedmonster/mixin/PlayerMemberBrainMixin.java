package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionTraits;
import cc.sighs.cannedmonster.fusion.NativeBrainAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Brain.class)
public abstract class PlayerMemberBrainMixin implements NativeBrainAccess {
    @Unique private Mob cannedmonster$owner;

    @Override
    public void cannedmonster$owner(Mob owner) {
        cannedmonster$owner = owner;
    }

    @Unique
    private boolean cannedmonster$forbidden(MemoryModuleType<?> type, Object value) {
        return type == MemoryModuleType.ATTACK_TARGET && value instanceof Entity target
                && cannedmonster$owner != null && BodyLinks.body(cannedmonster$owner) instanceof Player player
                && !FusionTraits.allowedTarget(player, target);
    }

    @Inject(method = "setMemoryInternal", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$write(MemoryModuleType<?> type, Optional<? extends ExpirableValue<?>> value, CallbackInfo ci) {
        if (value.isPresent() && cannedmonster$forbidden(type, value.get().getValue())) {
            ((Brain<?>) (Object) this).eraseMemory(type);
            ci.cancel();
        }
    }

    @Inject(method = {"getMemory", "getMemoryInternal"}, at = @At("RETURN"), cancellable = true)
    private void cannedmonster$read(MemoryModuleType<?> type, CallbackInfoReturnable<Optional<?>> cir) {
        Optional<?> value = cir.getReturnValue();
        if (value != null && value.isPresent() && cannedmonster$forbidden(type, value.get())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
