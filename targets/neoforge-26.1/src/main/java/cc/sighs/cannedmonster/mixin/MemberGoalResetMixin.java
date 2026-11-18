package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeMobAccess;
import cc.sighs.cannedmonster.fusion.OrderedCalls;
import cc.sighs.cannedmonster.fusion.TargetResets;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GoalSelector.class)
public abstract class MemberGoalResetMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void cannedmonster$completedCheck(CallbackInfo ci) {
        if (OrderedCalls.currentMember() instanceof Mob member
                && ((NativeMobAccess) member).cannedmonster$goals() == (Object) this) TargetResets.checked(member);
    }
}
