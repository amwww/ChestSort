package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent after running /cs find to highlight matching containers in-world.
 * Only intended for client-side rendering; server remains authoritative.
 */
public record FindHighlightsPayload(String dimensionId, String itemId, List<Long> posLongs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FindHighlightsPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "find_highlights"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FindHighlightsPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeUtf(payload.itemId == null ? "" : payload.itemId);
            List<Long> list = payload.posLongs == null ? List.of() : payload.posLongs;
            buf.writeVarInt(list.size());
            for (Long l : list) {
                buf.writeLong(l == null ? 0L : l);
            }
        },
        buf -> {
            String dim = buf.readUtf();
            String item = buf.readUtf();
            int n = buf.readVarInt();
            List<Long> pos = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                pos.add(buf.readLong());
            }
            return new FindHighlightsPayload(dim, item, pos);
        }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
