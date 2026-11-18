package cc.sighs.cannedmonster.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;
import java.util.List;

@Mixin(SynchedEntityData.class)
public abstract class AppearanceDataAccess implements cc.sighs.cannedmonster.fusion.visual.AppearanceData {
    @Accessor("itemsById")
    public abstract Int2ObjectMap<SynchedEntityData.DataItem<?>> cannedmonster$itemMap();

    @Override
    public List<SynchedEntityData.DataItem<?>> cannedmonster$items() {
        return new ArrayList<>(cannedmonster$itemMap().values());
    }

    @Override
    public int cannedmonster$id(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.getId();
    }

    @Override
    public net.minecraft.network.syncher.EntityDataSerializer<?> cannedmonster$serializer(
            net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        return accessor.getSerializer();
    }
}
