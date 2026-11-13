package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionRiding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FoodOnAStickItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodOnAStickItem.class)
public abstract class FoodOnAStickMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getControlledVehicle()Lnet/minecraft/world/entity/Entity;"))
    private Entity cannedmonster$vehicle(Player player) {
        return FusionRiding.itemVehicle(player);
    }
}
