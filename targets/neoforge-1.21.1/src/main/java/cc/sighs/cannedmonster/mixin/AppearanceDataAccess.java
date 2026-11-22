package cc.sighs.cannedmonster.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Arrays;
import java.util.List;

@Mixin(SynchedEntityData.class)
public abstract class AppearanceDataAccess implements cc.sighs.cannedmonster.fusion.visual.AppearanceData {
    @Accessor("itemsById")
    protected abstract SynchedEntityData.DataItem<?>[] cannedmonster$itemArray();

    @Override
    public List<SynchedEntityData.DataItem<?>> cannedmonster$items() {
        return Arrays.asList(cannedmonster$itemArray());
    }

    @Override
    public int cannedmonster$id(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.id();
    }

    @Override
    public net.minecraft.network.syncher.EntityDataSerializer<?> cannedmonster$serializer(
            net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.serializer();
    }
}
