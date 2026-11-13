package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.FusionMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class NavigationQueryMixin {
    @Shadow @Final protected Mob mob;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$query(Set<BlockPos> positions, int margin, boolean above, int reach, float range,
                                    CallbackInfoReturnable<Path> callback) {
        if (FusionMotion.denyPath(mob)) callback.setReturnValue(null);
    }
}
