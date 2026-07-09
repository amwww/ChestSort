package dev.dromer.chestsort.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

public final class ClientFindHighlightRenderer {
    private ClientFindHighlightRenderer() {
    }

    private static final int OUTLINE_COLOR_ARGB = 0x80FFFF00;
    private static final float OUTLINE_LINE_WIDTH = 2.0f;

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Render wand selection overlay first (independent of /cs find highlights).
        ClientWandSelectionRenderer.render();

        String dimId = mc.level.dimension().identifier().toString();
        if (!ClientFindHighlightState.isActiveFor(dimId)) return;

        for (Long l : ClientFindHighlightState.posLongs()) {
            if (l == null) continue;
            BlockPos pos = BlockPos.of(l);
            Gizmos.cuboid(pos, GizmoStyle.stroke(OUTLINE_COLOR_ARGB, OUTLINE_LINE_WIDTH));
        }
    }
}
