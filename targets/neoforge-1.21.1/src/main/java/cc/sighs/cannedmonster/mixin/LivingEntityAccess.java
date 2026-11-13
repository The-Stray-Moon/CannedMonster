package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeRideAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccess extends NativeRideAccess {
    @Override
    @Invoker("getRiddenInput")
    Vec3 cannedmonster$riddenInput(Player player, Vec3 input);

    @Override
    @Invoker("getRiddenSpeed")
    float cannedmonster$riddenSpeed(Player player);

    @Override
    @Invoker("tickRidden")
    void cannedmonster$tickRidden(Player player, Vec3 input);
}
