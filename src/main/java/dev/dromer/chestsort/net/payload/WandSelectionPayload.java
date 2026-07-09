package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WandSelectionPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "wand_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandSelectionPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.wandItemId == null ? "" : payload.wandItemId);

            buf.writeBoolean(payload.hasPos1);
            buf.writeUtf(payload.pos1DimensionId == null ? "" : payload.pos1DimensionId);
            buf.writeLong(payload.pos1Long);

            buf.writeBoolean(payload.hasPos2);
            buf.writeUtf(payload.pos2DimensionId == null ? "" : payload.pos2DimensionId);
            buf.writeLong(payload.pos2Long);

            buf.writeLong(payload.blockCount);
            buf.writeVarInt(payload.containerCount);
        },
        buf -> new WandSelectionPayload(
            buf.readUtf(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readLong(),
            buf.readBoolean(),
            buf.readUtf(),
            buf.readLong(),
            buf.readLong(),
            buf.readVarInt()
        )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
