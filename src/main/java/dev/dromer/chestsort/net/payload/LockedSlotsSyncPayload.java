package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: current protected player inventory slot indices. */
public record LockedSlotsSyncPayload(List<Integer> lockedSlots) implements CustomPayload {
    public static final Id<LockedSlotsSyncPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "locked_slots_sync"));

    public static final PacketCodec<RegistryByteBuf, LockedSlotsSyncPayload> CODEC = PacketCodec.ofStatic(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
