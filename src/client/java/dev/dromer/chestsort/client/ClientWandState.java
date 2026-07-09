package dev.dromer.chestsort.client;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import net.minecraft.core.BlockPos;

/**
 * Client-authoritative wand state: bound item, pos1/pos2 selection, and clipboard filter.
 * This mod never relies on any server-side support for the wand feature.
 */
public final class ClientWandState {
    public record Pos(String dimensionId, long posLong) {
        public BlockPos pos() {
            return BlockPos.of(posLong);
        }
    }

    private static String wandItemId = "";
    private static Pos pos1 = null;
    private static Pos pos2 = null;
    private static ContainerFilterSpec clipboard = null;

    private ClientWandState() {
    }

    public static String wandItemId() {
        return wandItemId;
    }

    public static void bind(String itemId) {
        wandItemId = itemId == null ? "" : itemId;
        pushRenderState();
    }

    public static void unbind() {
        wandItemId = "";
        pos1 = null;
        pos2 = null;
        clipboard = null;
        pushRenderState();
    }

    public static boolean isBound() {
        return wandItemId != null && !wandItemId.isEmpty();
    }

    public static Pos pos1() {
        return pos1;
    }

    public static Pos pos2() {
        return pos2;
    }

    public static void setPos1(String dimId, long posLong) {
        pos1 = new Pos(dimId == null ? "" : dimId, posLong);
        pushRenderState();
    }

    public static void setPos2(String dimId, long posLong) {
        pos2 = new Pos(dimId == null ? "" : dimId, posLong);
        pushRenderState();
    }

    public static void clearSelection() {
        pos1 = null;
        pos2 = null;
        pushRenderState();
    }

    public static ContainerFilterSpec clipboard() {
        return clipboard;
    }

    public static void setClipboard(ContainerFilterSpec spec) {
        clipboard = spec;
    }

    private static void pushRenderState() {
        ClientWandSelectionState.set(
            wandItemId,
            pos1 != null,
            pos1 == null ? "" : pos1.dimensionId(),
            pos1 == null ? 0L : pos1.posLong(),
            pos2 != null,
            pos2 == null ? "" : pos2.dimensionId(),
            pos2 == null ? 0L : pos2.posLong(),
            0L,
            0
        );
    }
}
