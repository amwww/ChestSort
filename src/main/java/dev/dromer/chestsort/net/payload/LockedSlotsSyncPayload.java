package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: current protected player inventory slot indices. */
public record LockedSlotsSyncPayload(List<Integer> lockedSlots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LockedSlotsSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "locked_slots_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockedSlotsSyncPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            List<Integer> list = payload.lockedSlots == null ? List.of() : payload.lockedSlots;
            buf.writeVarInt(list.size());
            for (Integer i : list) {
                buf.writeVarInt(i == null ? -1 : i);
            }
        },
        buf -> {
            int n = buf.readVarInt();
            ArrayList<Integer> out = new ArrayList<>(Math.max(0, n));
            for (int k = 0; k < n; k++) {
                out.add(buf.readVarInt());
            }
            return new LockedSlotsSyncPayload(out);
        }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
