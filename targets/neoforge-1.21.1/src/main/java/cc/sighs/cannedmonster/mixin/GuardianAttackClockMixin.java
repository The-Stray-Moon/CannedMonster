package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.AttackClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal")
public interface GuardianAttackClockMixin extends AttackClock {
    @Override
    @Accessor("attackTime")
    int cannedmonster$attackTime();
}
