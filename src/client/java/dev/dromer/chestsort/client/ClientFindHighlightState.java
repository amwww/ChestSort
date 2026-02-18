package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientFindHighlightState {
    private static String dimensionId = "";
    private static String itemId = "";
    private static List<Long> posLongs = List.of();

    private ClientFindHighlightState() {
    }

    public static void set(String dimId, String item, List<Long> positions) {
        dimensionId = dimId == null ? "" : dimId;
        itemId = item == null ? "" : item;
        if (positions == null || positions.isEmpty()) {
            posLongs = List.of();
        } else {
            posLongs = Collections.unmodifiableList(new ArrayList<>(positions));
        }
    }

    public static void clear() {
        dimensionId = "";
        itemId = "";
        posLongs = List.of();
    }

    public static String dimensionId() {
        return dimensionId;
    }

    public static String itemId() {
        return itemId;
    }

    public static List<Long> posLongs() {
        return posLongs;
    }

    public static boolean isActiveFor(String currentDimId) {
        return !posLongs.isEmpty() && currentDimId != null && currentDimId.equals(dimensionId);
    }
}
