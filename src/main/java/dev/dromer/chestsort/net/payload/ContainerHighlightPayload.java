package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ContainerHighlightPayload(String itemId, boolean highlight) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ContainerHighlightPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "container_highlight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerHighlightPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.itemId == null ? "" : payload.itemId);
            buf.writeBoolean(payload.highlight);
        },
        buf -> new ContainerHighlightPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
