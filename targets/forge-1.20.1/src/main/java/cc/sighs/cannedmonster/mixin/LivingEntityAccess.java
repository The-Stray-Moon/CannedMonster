package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeRideAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAccess implements NativeRideAccess {
    @Override
    @Invoker("getRiddenInput")
    public abstract Vec3 cannedmonster$riddenInput(Player player, Vec3 input);

    @Override
    @Invoker("getRiddenSpeed")
    public abstract float cannedmonster$riddenSpeed(Player player);

    @Override
    @Invoker("tickRidden")
    public abstract void cannedmonster$tickRidden(Player player, Vec3 input);
}
