package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ContainerContextPayload(String dimensionId, long posLong, String containerType, List<String> filterItems) implements CustomPayload {
    public static final Id<ContainerContextPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "container_context"));

    public static final PacketCodec<RegistryByteBuf, ContainerContextPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeString(payload.containerType == null ? "" : payload.containerType);
            List<String> items = payload.filterItems == null ? List.of() : payload.filterItems;
            buf.writeVarInt(items.size());
            for (String itemId : items) {
                buf.writeString(itemId == null ? "" : itemId);
            }
        },
        buf -> {
            String dim = buf.readString();
            long pos = buf.readLong();
            String type = buf.readString();
            int n = buf.readVarInt();
            List<String> items = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                items.add(buf.readString());
            }
            return new ContainerContextPayload(dim, pos, type, items);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
