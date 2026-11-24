package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceClientView;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The client space view uses a fixed height profile instead of the real dimension
 * type, so the local room works in custom dimensions with min Y above 0 or max Y
 * below the room top. The real type comes back as soon as the view is inactive.
 */
@Mixin(Level.class)
public abstract class SpaceClientDimensionMixin {
    @WrapMethod(method = "dimensionType")
    private DimensionType cannedmonster$viewDimensionType(Operation<DimensionType> original) {
        DimensionType base = original.call();
        if ((Object) this instanceof ClientLevel && SpaceClientView.active() && !SpaceClientView.realHeight()) {
            return SpaceClientView.viewDimensionType(base);
        }
        return base;
    }
}
