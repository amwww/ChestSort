package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client payload describing the outcome of a Sort/Autosort/Undo operation.
 */
public record SortResultPayload(
    String dimensionId,
    long posLong,
    byte kind,
    String autosortMode,
    boolean containerAutosort,
    long undoId,
    int movedTotal,
    List<SortLine> lines
) implements CustomPayload {

    public static final byte KIND_SORT = 0;
    public static final byte KIND_AUTOSORT = 1;
    public static final byte KIND_UNDO = 2;
    public static final byte KIND_ORGANIZE = 3;

    public static final Id<SortResultPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "sort_result"));

    public static final PacketCodec<RegistryByteBuf, SortResultPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeByte(payload.kind);
            buf.writeString(payload.autosortMode == null ? "" : payload.autosortMode);
            buf.writeBoolean(payload.containerAutosort);
            buf.writeLong(payload.undoId);
            buf.writeInt(payload.movedTotal);

            List<SortLine> lines = payload.lines == null ? List.of() : payload.lines;
            buf.writeInt(lines.size());
            for (SortLine line : lines) {
                if (line == null) {
                    buf.writeString("");
                    buf.writeInt(0);
                    buf.writeInt(0);
                    continue;
                }
                buf.writeString(line.itemId == null ? "" : line.itemId);
                buf.writeInt(line.count);
                List<String> reasons = line.reasons == null ? List.of() : line.reasons;
                buf.writeInt(reasons.size());
                for (String r : reasons) {
                    buf.writeString(r == null ? "" : r);
                }
            }
        },
        buf -> {
            String dimId = buf.readString();
            long posLong = buf.readLong();
            byte kind = buf.readByte();
            String autosortMode = buf.readString();
            boolean containerAutosort = buf.readBoolean();
            long undoId = buf.readLong();
            int movedTotal = buf.readInt();

            int n = buf.readInt();
            ArrayList<SortLine> lines = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                String itemId = buf.readString();
                int count = buf.readInt();
                int rn = buf.readInt();
                ArrayList<String> reasons = new ArrayList<>(Math.max(0, rn));
                for (int j = 0; j < rn; j++) {
                    reasons.add(buf.readString());
                }
                lines.add(new SortLine(itemId, count, reasons));
            }

            return new SortResultPayload(dimId, posLong, kind, autosortMode, containerAutosort, undoId, movedTotal, lines);
        }
    );

    public record SortLine(String itemId, int count, List<String> reasons) {
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
