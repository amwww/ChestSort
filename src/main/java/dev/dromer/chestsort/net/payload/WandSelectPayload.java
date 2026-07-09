package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: select wand pos1/pos2 without interacting/breaking blocks. */
public record WandSelectPayload(byte which, String dimensionId, long posLong) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WandSelectPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "wand_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandSelectPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeByte(payload.which);
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
        },
        buf -> new WandSelectPayload(buf.readByte(), buf.readUtf(), buf.readLong())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
