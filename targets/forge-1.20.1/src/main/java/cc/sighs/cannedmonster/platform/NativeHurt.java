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
            try {
                handle = lookup.findSpecial(Player.class, "hurt", signature, Player.class);
            } catch (NoSuchMethodException missing) {
                handle = lookup.findSpecial(Player.class, "m_6469_", signature, Player.class);
            }
        } else {
            try {
                handle = MethodHandles.publicLookup().findVirtual(LivingEntity.class, "hurt", signature);
            } catch (NoSuchMethodException missing) {
                handle = MethodHandles.publicLookup().findVirtual(LivingEntity.class, "m_6469_", signature);
            }
        }
        return MethodHandles.dropArguments(handle, 1, ServerLevel.class);
    }
}
