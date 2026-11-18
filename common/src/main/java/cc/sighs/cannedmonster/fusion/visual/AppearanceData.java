package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.List;

public interface AppearanceData {
    List<SynchedEntityData.DataItem<?>> cannedmonster$items();

    int cannedmonster$id(EntityDataAccessor<?> accessor);

    EntityDataSerializer<?> cannedmonster$serializer(EntityDataAccessor<?> accessor);
}
