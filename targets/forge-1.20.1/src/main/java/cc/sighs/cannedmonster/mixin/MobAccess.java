package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeMobAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mob.class)
public abstract class MobAccess implements NativeMobAccess {
    @Override
    @Accessor("goalSelector")
    public abstract GoalSelector cannedmonster$goals();

    @Override
    @Invoker("serverAiStep")
    public abstract void cannedmonster$serverAiStep();

    @Override
    @Invoker("mobInteract")
    public abstract InteractionResult cannedmonster$mobInteract(Player player, InteractionHand hand);
}
