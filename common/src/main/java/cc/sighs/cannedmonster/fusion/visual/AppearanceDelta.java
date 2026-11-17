package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.network.syncher.SynchedEntityData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AppearanceDelta {
    private AppearanceDelta() {}

    static List<SynchedEntityData.DataValue<?>> changed(List<SynchedEntityData.DataValue<?>> previous,
                                                       List<SynchedEntityData.DataValue<?>> current) {
        List<SynchedEntityData.DataValue<?>> changed = null;
        for (int i = 0; i < current.size(); i++) {
            var value = current.get(i);
            var before = i < previous.size() ? previous.get(i) : null;
            if (before == null || before.id() != value.id() || before.serializer() != value.serializer()
                    || !Objects.equals(before.value(), value.value())) {
                if (changed == null) changed = new ArrayList<>();
                changed.add(value);
            }
        }
        return changed == null ? List.of() : List.copyOf(changed);
    }
}
