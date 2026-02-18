package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SetFilterPayload(String dimensionId, long posLong, List<String> filterItems) implements CustomPayload {
    public static final Id<SetFilterPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "set_filter"));

    public static final PacketCodec<RegistryByteBuf, SetFilterPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            List<String> items = payload.filterItems == null ? List.of() : payload.filterItems;
            buf.writeVarInt(items.size());
            for (String itemId : items) {
                buf.writeString(itemId == null ? "" : itemId);
            }
        },
        buf -> {
            String dim = buf.readString();
            long pos = buf.readLong();
            int n = buf.readVarInt();
            List<String> items = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                items.add(buf.readString());
            }
            return new SetFilterPayload(dim, pos, items);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
