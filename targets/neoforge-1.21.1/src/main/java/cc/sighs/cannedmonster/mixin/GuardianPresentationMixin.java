package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeMobAccess;
import cc.sighs.cannedmonster.fusion.visual.AttackClock;
import cc.sighs.cannedmonster.fusion.visual.PresentationState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Guardian.class)
public abstract class GuardianPresentationMixin implements PresentationState {
    @Shadow private int clientSideAttackTime;

    @Override
    public CompoundTag cannedmonster$capturePresentation() {
        CompoundTag state = new CompoundTag();
        for (var goal : ((NativeMobAccess) this).cannedmonster$goals().getAvailableGoals()) {
            if (goal.getGoal() instanceof AttackClock clock) {
                state.putInt("AttackTime", Math.max(0, clock.cannedmonster$attackTime()));
                break;
            }
        }
        return state;
    }

    @Override
    public int cannedmonster$continuousEvent() {
        return ((Guardian) (Object) this).hasActiveAttackTarget() ? 21 : -1;
    }

    @Override
    public void cannedmonster$applyPresentation(CompoundTag state, boolean fresh) {
        if (state.contains("AttackTime")) clientSideAttackTime = Math.min(((Guardian) (Object) this).getAttackDuration(), Math.max(0, state.getInt("AttackTime")));
    }
}
