package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface NativeRideAccess {
    Vec3 cannedmonster$riddenInput(Player player, Vec3 input);

    float cannedmonster$riddenSpeed(Player player);

    void cannedmonster$tickRidden(Player player, Vec3 input);
}
