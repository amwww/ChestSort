package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SortRequestPayload(String dimensionId, long posLong) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SortRequestPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortRequestPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
        },
        buf -> new SortRequestPayload(buf.readUtf(), buf.readLong())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
