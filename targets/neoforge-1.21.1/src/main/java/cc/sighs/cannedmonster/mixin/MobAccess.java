package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeMobAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public interface MobAccess extends NativeMobAccess {
    @Override
    @Invoker("serverAiStep")
    void cannedmonster$serverAiStep();

    @Override
    @Invoker("mobInteract")
    InteractionResult cannedmonster$mobInteract(Player player, InteractionHand hand);
}
