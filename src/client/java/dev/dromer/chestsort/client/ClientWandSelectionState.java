package dev.dromer.chestsort.client;

import net.minecraft.util.math.BlockPos;

public final class ClientWandSelectionState {
    private ClientWandSelectionState() {
    }

    private static String wandItemId = "";
    private static boolean hasPos1;
    private static String pos1Dim = "";
    private static long pos1Long;
    private static boolean hasPos2;
    private static String pos2Dim = "";
    private static long pos2Long;
    private static long blockCount;
    private static int containerCount;

    public static void set(
        String wandItemId,
        boolean hasPos1,
        String pos1Dim,
        long pos1Long,
        boolean hasPos2,
        String pos2Dim,
        long pos2Long,
        long blockCount,
        int containerCount
    ) {
        ClientWandSelectionState.wandItemId = wandItemId == null ? "" : wandItemId;
        ClientWandSelectionState.hasPos1 = hasPos1;
        ClientWandSelectionState.pos1Dim = pos1Dim == null ? "" : pos1Dim;
        ClientWandSelectionState.pos1Long = pos1Long;
        ClientWandSelectionState.hasPos2 = hasPos2;
        ClientWandSelectionState.pos2Dim = pos2Dim == null ? "" : pos2Dim;
        ClientWandSelectionState.pos2Long = pos2Long;
        ClientWandSelectionState.blockCount = blockCount;
        ClientWandSelectionState.containerCount = containerCount;
    }

    public static String wandItemId() {
        return wandItemId;
    }

    public static boolean hasPos1In(String dimId) {
        return hasPos1 && dimId != null && dimId.equals(pos1Dim);
    }

    public static boolean hasPos2In(String dimId) {
        return hasPos2 && dimId != null && dimId.equals(pos2Dim);
    }

    public static BlockPos pos1() {
        return hasPos1 ? BlockPos.fromLong(pos1Long) : null;
    }

    public static BlockPos pos2() {
        return hasPos2 ? BlockPos.fromLong(pos2Long) : null;
    }

    public static long blockCount() {
        return blockCount;
    }

    public static int containerCount() {
        return containerCount;
    }
}
