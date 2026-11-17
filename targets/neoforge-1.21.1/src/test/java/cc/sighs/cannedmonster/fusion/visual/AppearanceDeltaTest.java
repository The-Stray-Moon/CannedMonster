package cc.sighs.cannedmonster.fusion.visual;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceDeltaTest {
    private static final net.minecraft.network.syncher.EntityDataSerializer<Integer> INT =
            net.minecraft.network.syncher.EntityDataSerializer.forValueType(net.minecraft.network.codec.ByteBufCodecs.INT);
    private static final net.minecraft.network.syncher.EntityDataSerializer<CompoundTag> TAG = new net.minecraft.network.syncher.EntityDataSerializer<>() {
        @Override
        public net.minecraft.network.codec.StreamCodec<? super RegistryFriendlyByteBuf, CompoundTag> codec() {
            return net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG;
        }
        @Override
        public CompoundTag copy(CompoundTag value) { return value.copy(); }
    };

    private static final UUID BODY = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();

    private static SynchedEntityData.DataValue<?> value(int id, int value) {
        return new SynchedEntityData.DataValue<>(id, INT, value);
    }

    private static FusionAppearance state(long revision, int number, boolean flying) {
        return new FusionAppearance(1, BODY, -1, flying, revision, true, List.of(new FusionAppearance.Member(MEMBER,
                ResourceLocation.withDefaultNamespace("pig"), List.of(value(0, number), value(1, 4)), new CompoundTag())));
    }

    @Test
    void unchangedSnapshotsDoNotProducePackets() {
        assertNull(AppearanceSync.delta(state(1, 3, false), state(2, 3, false)));
    }

    @Test
    void sendsOnlyChangedValuesAndStillCarriesComposition() {
        var delta = AppearanceSync.delta(state(1, 3, false), state(2, 5, false));
        assertNotNull(delta);
        assertFalse(delta.full());
        assertEquals(1, delta.members().size());
        assertEquals(MEMBER, delta.members().getFirst().id());
        assertEquals(List.of(value(0, 5)), delta.members().getFirst().data());
        var control = AppearanceSync.delta(state(1, 3, false), state(2, 3, true));
        assertTrue(control.flying());
        assertTrue(control.members().getFirst().data().isEmpty());
    }

    @Test
    void compositionReplacementRequiresAFullSnapshot() {
        var replacement = new FusionAppearance(1, BODY, -1, false, 2, true, List.of());
        assertSame(replacement, AppearanceSync.delta(state(1, 3, false), replacement));
    }

    @Test
    void copiesMutableSerializerValuesForComparison() {
        CompoundTag original = new CompoundTag();
        original.putInt("value", 1);
        var accessor = new net.minecraft.network.syncher.EntityDataAccessor<>(3, TAG);
        var before = SynchedEntityData.DataValue.create(accessor, original);
        original.putInt("value", 2);
        var after = SynchedEntityData.DataValue.create(accessor, original);
        assertEquals(1, AppearanceDelta.changed(List.of(before), List.of(after)).size());
        assertEquals(1, before.value().getInt("value"));
    }

    @Test
    void codecCarriesFullAndDeltaFlags() {
        for (boolean full : List.of(true, false)) {
            var packet = new FusionAppearance(1, BODY, -1, true, 99, full, List.of());
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                FusionAppearance.CODEC.encode(buffer, packet);
                assertEquals(packet, FusionAppearance.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }
}
