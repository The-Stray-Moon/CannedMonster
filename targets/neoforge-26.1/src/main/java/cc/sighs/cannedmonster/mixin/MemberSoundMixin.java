package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.ClientMemberEvents;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class MemberSoundMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void cannedmonster$owner(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        ClientMemberEvents.sound(sound);
    }
}
