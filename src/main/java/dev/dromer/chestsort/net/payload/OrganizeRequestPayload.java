package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OrganizeRequestPayload(String dimensionId, long posLong) implements CustomPayload {
    public static final Id<OrganizeRequestPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "organize"));

    public static final PacketCodec<RegistryByteBuf, OrganizeRequestPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
        },
        buf -> new OrganizeRequestPayload(buf.readString(), buf.readLong())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
