package cc.sighs.cannedmonster.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Arrays;
import java.util.List;

@Mixin(SynchedEntityData.class)
public interface AppearanceDataAccess extends cc.sighs.cannedmonster.fusion.visual.AppearanceData {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] cannedmonster$itemArray();

    @Override
    default List<SynchedEntityData.DataItem<?>> cannedmonster$items() {
        return Arrays.asList(cannedmonster$itemArray());
    }

    @Override
    default int cannedmonster$id(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.id();
    }

    @Override
    default net.minecraft.network.syncher.EntityDataSerializer<?> cannedmonster$serializer(
            net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.serializer();
    }
}
