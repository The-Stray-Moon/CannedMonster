package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.PlayerFusion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerFusionLogoutMixin {
    @Inject(method = "remove", at = @At("RETURN"))
    private void cannedmonster$afterSave(ServerPlayer player, CallbackInfo ci) {
        PlayerFusion.logout(player);
    }
}
