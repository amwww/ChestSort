package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UndoSortPayload(String dimensionId, long posLong, long undoId) implements CustomPayload {
    public static final Id<UndoSortPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "undo_sort"));

    public static final PacketCodec<RegistryByteBuf, UndoSortPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeLong(payload.undoId);
        },
        buf -> new UndoSortPayload(buf.readString(), buf.readLong(), buf.readLong())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
