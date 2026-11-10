package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

public interface NativeMobAccess {
    void cannedmonster$serverAiStep();

    InteractionResult cannedmonster$mobInteract(Player player, InteractionHand hand);
}
