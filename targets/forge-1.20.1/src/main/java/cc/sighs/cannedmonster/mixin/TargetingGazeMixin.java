package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.BodyLinks;
import cc.sighs.cannedmonster.fusion.FusionGaze;
import net.minecraft.world.entity.EntityType;
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
                    target = "Lnet/minecraft/world/entity/LivingEntity;canAttackType(Lnet/minecraft/world/entity/EntityType;)Z"))
    private boolean cannedmonster$gazeType(LivingEntity attacker, EntityType<?> type) {
        if (attacker instanceof Mob member && BodyLinks.body(member) instanceof Player player) {
            LivingEntity target = FusionGaze.target(player);
            if (target != null && target.getType() == type) {
                return true;
            }
        }
        return attacker.canAttackType(type);
    }
}
