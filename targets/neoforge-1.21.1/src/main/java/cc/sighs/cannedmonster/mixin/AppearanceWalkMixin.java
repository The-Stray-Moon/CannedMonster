package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.AppearanceWalk;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WalkAnimationState.class)
public abstract class AppearanceWalkMixin implements AppearanceWalk {
    @Shadow private float speedOld;
    @Shadow private float speed;
    @Shadow private float position;

    @Override
    public void cannedmonster$copy(WalkAnimationState source) {
        speedOld = source.speed(0);
        speed = source.speed();
        position = source.position();
    }
}
