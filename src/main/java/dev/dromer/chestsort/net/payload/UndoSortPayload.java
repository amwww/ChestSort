package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UndoSortPayload(String dimensionId, long posLong, long undoId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UndoSortPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "undo_sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndoSortPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeLong(payload.undoId);
        },
        buf -> new UndoSortPayload(buf.readUtf(), buf.readLong(), buf.readLong())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
