package cc.sighs.cannedmonster.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.class)
public interface AppearanceDataAccess extends cc.sighs.cannedmonster.fusion.visual.AppearanceData {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] cannedmonster$items();
}
