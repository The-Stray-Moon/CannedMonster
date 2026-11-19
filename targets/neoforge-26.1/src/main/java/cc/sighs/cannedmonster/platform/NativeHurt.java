package cc.sighs.cannedmonster.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class NativeHurt {
    private NativeHurt() {}

    public static MethodHandle bind(Class<?> type) throws ReflectiveOperationException {
        MethodType signature = MethodType.methodType(boolean.class, ServerLevel.class, DamageSource.class, float.class);
        if (Player.class.isAssignableFrom(type)) {
            var lookup = MethodHandles.privateLookupIn(Player.class, MethodHandles.lookup());
            return lookup.findSpecial(Player.class, "hurtServer", signature, Player.class);
        }
        return MethodHandles.publicLookup().findVirtual(LivingEntity.class, "hurtServer", signature);
    }
}
