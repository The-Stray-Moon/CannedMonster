package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionGaze;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TargetingConditions.class)
public abstract class TargetingGazeMixin {
    @Redirect(method = "test",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean cannedmonster$gazeType(LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof Mob member && BodyLinks.body(member) instanceof Player player) {
            LivingEntity gaze = FusionGaze.target(player);
            if (gaze != null && gaze == target) {
                return true;
            }
        }
        return attacker.canAttack(target);
    }
}
