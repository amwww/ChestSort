package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.net.payload.SortResultPayload;

public final class ClientSortNotificationState {
    private ClientSortNotificationState() {
    }

    private static String dimensionId = "";
    private static long posLong = 0L;

    private static byte kind = SortResultPayload.KIND_SORT;
    private static String autosortMode = "";
    private static boolean containerAutosort = false;
    private static long undoId = 0L;
    private static int movedTotal = 0;
    private static List<SortResultPayload.SortLine> lines = List.of();

    public static void set(SortResultPayload payload) {
        if (payload == null) {
            clear();
            return;
        }
        dimensionId = payload.dimensionId() == null ? "" : payload.dimensionId();
        posLong = payload.posLong();
        kind = payload.kind();
        autosortMode = payload.autosortMode() == null ? "" : payload.autosortMode();
        containerAutosort = payload.containerAutosort();
        undoId = payload.undoId();
        movedTotal = payload.movedTotal();

        List<SortResultPayload.SortLine> in = payload.lines() == null ? List.of() : payload.lines();
        lines = new ArrayList<>(in);
    }

    public static void clear() {
        dimensionId = "";
        posLong = 0L;
        kind = SortResultPayload.KIND_SORT;
        autosortMode = "";
        containerAutosort = false;
        undoId = 0L;
        movedTotal = 0;
        lines = List.of();
    }

    public static boolean isActiveForCurrentContainer() {
        if (!ClientContainerContext.isChestOrBarrel()) return false;
        if (dimensionId == null || dimensionId.isEmpty()) return false;
        if (!dimensionId.equals(ClientContainerContext.dimensionId())) return false;
        return posLong == ClientContainerContext.posLong();
    }

    public static byte kind() {
        return kind;
    }

    public static String autosortMode() {
        return autosortMode;
    }

    public static boolean containerAutosort() {
        return containerAutosort;
    }

    public static long undoId() {
        return undoId;
    }

    public static int movedTotal() {
        return movedTotal;
    }

    public static List<SortResultPayload.SortLine> lines() {
        return lines;
    }
}
