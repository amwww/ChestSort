package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent after running /cs find to highlight matching containers in-world.
 * Only intended for client-side rendering; server remains authoritative.
 */
public record FindHighlightsPayload(String dimensionId, String itemId, List<Long> posLongs) implements CustomPayload {
    public static final Id<FindHighlightsPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "find_highlights"));

    public static final PacketCodec<RegistryByteBuf, FindHighlightsPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeString(payload.itemId == null ? "" : payload.itemId);
            List<Long> list = payload.posLongs == null ? List.of() : payload.posLongs;
            buf.writeVarInt(list.size());
            for (Long l : list) {
                buf.writeLong(l == null ? 0L : l);
            }
        },
        buf -> {
            String dim = buf.readString();
            String item = buf.readString();
            int n = buf.readVarInt();
            List<Long> pos = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                pos.add(buf.readLong());
            }
            return new FindHighlightsPayload(dim, item, pos);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
