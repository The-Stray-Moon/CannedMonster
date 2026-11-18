package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.visual.AppearanceData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Arrays;
import java.util.List;

@Mixin(SynchedEntityData.class)
public abstract class AppearanceDataAccess implements AppearanceData {
    @Accessor("itemsById")
    abstract SynchedEntityData.DataItem<?>[] cannedmonster$itemArray();

    @Override
    public List<SynchedEntityData.DataItem<?>> cannedmonster$items() {
        return Arrays.asList(cannedmonster$itemArray());
    }

    @Override
    public int cannedmonster$id(EntityDataAccessor<?> accessor) {
        return accessor.id();
    }

    @Override
    public EntityDataSerializer<?> cannedmonster$serializer(EntityDataAccessor<?> accessor) {
        return accessor.serializer();
    }
}
