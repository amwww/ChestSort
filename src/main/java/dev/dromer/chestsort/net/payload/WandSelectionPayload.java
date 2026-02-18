package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: current wand binding + selection (for rendering/UX). */
public record WandSelectionPayload(
    String wandItemId,
    boolean hasPos1,
    String pos1DimensionId,
    long pos1Long,
    boolean hasPos2,
    String pos2DimensionId,
    long pos2Long,
    long blockCount,
    int containerCount
) implements CustomPayload {
    public static final Id<WandSelectionPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "wand_selection"));

    public static final PacketCodec<RegistryByteBuf, WandSelectionPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.wandItemId == null ? "" : payload.wandItemId);

            buf.writeBoolean(payload.hasPos1);
            buf.writeString(payload.pos1DimensionId == null ? "" : payload.pos1DimensionId);
            buf.writeLong(payload.pos1Long);

            buf.writeBoolean(payload.hasPos2);
            buf.writeString(payload.pos2DimensionId == null ? "" : payload.pos2DimensionId);
            buf.writeLong(payload.pos2Long);

            buf.writeLong(payload.blockCount);
            buf.writeVarInt(payload.containerCount);
        },
        buf -> new WandSelectionPayload(
            buf.readString(),
            buf.readBoolean(),
            buf.readString(),
            buf.readLong(),
            buf.readBoolean(),
            buf.readString(),
            buf.readLong(),
            buf.readLong(),
            buf.readVarInt()
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
