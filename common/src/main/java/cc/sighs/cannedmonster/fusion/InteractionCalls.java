package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class InteractionCalls {
    private InteractionCalls() {
    }

    public static InteractionResult item(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        for (Mob member : FusionRuntime.facets(entity)) {
            InteractionResult result = stack.interactLivingEntity(player, member, hand);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return stack.interactLivingEntity(player, entity, hand);
    }
}
