package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SortRequestPayload(String dimensionId, long posLong) implements CustomPayload {
    public static final Id<SortRequestPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "sort"));

    public static final PacketCodec<RegistryByteBuf, SortRequestPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
        },
        buf -> new SortRequestPayload(buf.readString(), buf.readLong())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
