package cc.sighs.cannedmonster.fusion.visual;

import net.minecraft.nbt.CompoundTag;

public interface PresentationState {
    CompoundTag cannedmonster$capturePresentation();
    void cannedmonster$applyPresentation(CompoundTag state, boolean fresh);
    default int cannedmonster$continuousEvent() { return -1; }
}
