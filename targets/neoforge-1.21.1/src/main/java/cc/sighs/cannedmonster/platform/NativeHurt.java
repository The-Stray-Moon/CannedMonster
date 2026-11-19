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
        MethodType signature = MethodType.methodType(boolean.class, DamageSource.class, float.class);
        MethodHandle handle;
        if (Player.class.isAssignableFrom(type)) {
            var lookup = MethodHandles.privateLookupIn(Player.class, MethodHandles.lookup());
            handle = lookup.findSpecial(Player.class, "hurt", signature, Player.class);
        } else handle = MethodHandles.publicLookup().findVirtual(LivingEntity.class, "hurt", signature);
        return MethodHandles.dropArguments(handle, 1, ServerLevel.class);
    }
}
