package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
) implements CustomPacketPayload {

    public static final byte KIND_SORT = 0;
    public static final byte KIND_AUTOSORT = 1;
    public static final byte KIND_UNDO = 2;
    public static final byte KIND_ORGANIZE = 3;

    public static final CustomPacketPayload.Type<SortResultPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "sort_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortResultPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeByte(payload.kind);
            buf.writeUtf(payload.autosortMode == null ? "" : payload.autosortMode);
            buf.writeBoolean(payload.containerAutosort);
            buf.writeLong(payload.undoId);
            buf.writeInt(payload.movedTotal);

            List<SortLine> lines = payload.lines == null ? List.of() : payload.lines;
            buf.writeInt(lines.size());
            for (SortLine line : lines) {
                if (line == null) {
                    buf.writeUtf("");
                    buf.writeInt(0);
                    buf.writeInt(0);
                    continue;
                }
                buf.writeUtf(line.itemId == null ? "" : line.itemId);
                buf.writeInt(line.count);
                List<String> reasons = line.reasons == null ? List.of() : line.reasons;
                buf.writeInt(reasons.size());
                for (String r : reasons) {
                    buf.writeUtf(r == null ? "" : r);
                }
            }
        },
        buf -> {
            String dimId = buf.readUtf();
            long posLong = buf.readLong();
            byte kind = buf.readByte();
            String autosortMode = buf.readUtf();
            boolean containerAutosort = buf.readBoolean();
            long undoId = buf.readLong();
            int movedTotal = buf.readInt();

            int n = buf.readInt();
            ArrayList<SortLine> lines = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                String itemId = buf.readUtf();
                int count = buf.readInt();
                int rn = buf.readInt();
                ArrayList<String> reasons = new ArrayList<>(Math.max(0, rn));
                for (int j = 0; j < rn; j++) {
                    reasons.add(buf.readUtf());
                }
                lines.add(new SortLine(itemId, count, reasons));
            }

            return new SortResultPayload(dimId, posLong, kind, autosortMode, containerAutosort, undoId, movedTotal, lines);
        }
    );

    public record SortLine(String itemId, int count, List<String> reasons) {
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
