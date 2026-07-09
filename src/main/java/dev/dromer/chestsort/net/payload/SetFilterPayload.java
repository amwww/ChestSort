package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SetFilterPayload(String dimensionId, long posLong, List<String> filterItems) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetFilterPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "set_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            List<String> items = payload.filterItems == null ? List.of() : payload.filterItems;
            buf.writeVarInt(items.size());
            for (String itemId : items) {
                buf.writeUtf(itemId == null ? "" : itemId);
            }
        },
        buf -> {
            String dim = buf.readUtf();
            long pos = buf.readLong();
            int n = buf.readVarInt();
            List<String> items = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                items.add(buf.readUtf());
            }
            return new SetFilterPayload(dim, pos, items);
        }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
