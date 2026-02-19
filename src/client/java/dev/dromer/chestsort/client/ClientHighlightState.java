package dev.dromer.chestsort.client;

public final class ClientHighlightState {
    private static String currentItemId = "";
    private static boolean highlight = false;

    private ClientHighlightState() {
    }

    public static void set(String itemId, boolean shouldHighlight) {
        currentItemId = itemId == null ? "" : itemId;
        highlight = shouldHighlight;
    }

    public static void clear() {
        currentItemId = "";
        highlight = false;
    }

    public static boolean shouldHighlight() {
        return highlight;
    }

    public static String currentItemId() {
        return currentItemId;
    }
}
