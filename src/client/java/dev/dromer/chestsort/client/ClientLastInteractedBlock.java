package dev.dromer.chestsort.client;

public final class ClientLastInteractedBlock {
    private static String dimId = "";
    private static long posLong = 0L;
    private static long timestampMs = 0L;

    private ClientLastInteractedBlock() {
    }

    public static void set(String dimensionId, long posLong) {
        ClientLastInteractedBlock.dimId = (dimensionId == null ? "" : dimensionId);
        ClientLastInteractedBlock.posLong = posLong;
        ClientLastInteractedBlock.timestampMs = System.currentTimeMillis();
    }

    public static String dimIdIfRecent(long maxAgeMs) {
        if (!isRecent(maxAgeMs)) return "";
        return dimId;
    }

    public static long posLongIfRecent(long maxAgeMs) {
        if (!isRecent(maxAgeMs)) return 0L;
        return posLong;
    }

    public static boolean isRecent(long maxAgeMs) {
        if (timestampMs <= 0L) return false;
        long age = System.currentTimeMillis() - timestampMs;
        return age >= 0L && age <= maxAgeMs;
    }

    public static void clear() {
        dimId = "";
        posLong = 0L;
        timestampMs = 0L;
    }
}
