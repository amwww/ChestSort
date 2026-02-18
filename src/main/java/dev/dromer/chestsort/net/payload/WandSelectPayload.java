package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: select wand pos1/pos2 without interacting/breaking blocks. */
public record WandSelectPayload(byte which, String dimensionId, long posLong) implements CustomPayload {
    public static final Id<WandSelectPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "wand_select"));

    public static final PacketCodec<RegistryByteBuf, WandSelectPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeByte(payload.which);
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
        },
        buf -> new WandSelectPayload(buf.readByte(), buf.readString(), buf.readLong())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
