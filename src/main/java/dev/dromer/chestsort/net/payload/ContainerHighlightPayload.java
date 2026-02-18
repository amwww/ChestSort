package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ContainerHighlightPayload(String itemId, boolean highlight) implements CustomPayload {
    public static final Id<ContainerHighlightPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "container_highlight"));

    public static final PacketCodec<RegistryByteBuf, ContainerHighlightPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.itemId == null ? "" : payload.itemId);
            buf.writeBoolean(payload.highlight);
        },
        buf -> new ContainerHighlightPayload(buf.readString(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
