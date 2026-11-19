package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FusionSession {
    final List<Mob> members;
    final Map<Mob, UUID> identities = new IdentityHashMap<>();
    List<Map<String, Double>> origins;
    final FusionMotion motion;
    OrderedCalls.Session calls;

    FusionSession(LivingEntity body, List<Mob> members, List<Map<String, Double>> origins) {
        this.members = List.copyOf(members);
        this.origins = List.copyOf(origins);
        for (Mob member : members) identities.put(member, member.getUUID());
        motion = body instanceof Mob mob ? new FusionMotion(mob, this.members) : null;
    }
}
