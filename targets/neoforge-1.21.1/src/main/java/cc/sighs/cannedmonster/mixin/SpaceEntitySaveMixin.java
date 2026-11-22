package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.space.SpaceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class SpaceEntitySaveMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void cannedmonster$anchorSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        Vec3 anchor = SpaceManager.saveAnchor((Entity) (Object) this);
        if (anchor == null) return;
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(anchor.x));
        pos.add(DoubleTag.valueOf(anchor.y));
        pos.add(DoubleTag.valueOf(anchor.z));
        tag.put("Pos", pos);
    }

    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void cannedmonster$ownedNotSaved(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceManager.isSpaceOwned((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
