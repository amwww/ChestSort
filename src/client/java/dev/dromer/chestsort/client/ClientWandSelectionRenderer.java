package dev.dromer.chestsort.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

public final class ClientWandSelectionRenderer {
    private ClientWandSelectionRenderer() {
    }

    private static final int REGION_COLOR_ARGB = 0x80FFFF00;
    private static final int POS1_COLOR_ARGB = 0x8000FF00;
    private static final int POS2_COLOR_ARGB = 0x80FF0000;
    private static final float REGION_LINE_WIDTH = 2.0f;
    private static final float POS_LINE_WIDTH = 3.0f;

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        String dimId = mc.level.dimension().identifier().toString();

        boolean has1 = ClientWandSelectionState.hasPos1In(dimId);
        boolean has2 = ClientWandSelectionState.hasPos2In(dimId);
        if (!has1 && !has2) return;

        BlockPos p1 = ClientWandSelectionState.pos1();
        BlockPos p2 = ClientWandSelectionState.pos2();

        if (has1 && p1 != null) {
            Gizmos.cuboid(p1, GizmoStyle.stroke(POS1_COLOR_ARGB, POS_LINE_WIDTH));
        }

        if (has2 && p2 != null) {
            Gizmos.cuboid(p2, GizmoStyle.stroke(POS2_COLOR_ARGB, POS_LINE_WIDTH));
        }

        if (has1 && has2 && p1 != null && p2 != null) {
            int minX = Math.min(p1.getX(), p2.getX());
            int minY = Math.min(p1.getY(), p2.getY());
            int minZ = Math.min(p1.getZ(), p2.getZ());
            int maxX = Math.max(p1.getX(), p2.getX());
            int maxY = Math.max(p1.getY(), p2.getY());
            int maxZ = Math.max(p1.getZ(), p2.getZ());

            AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            Gizmos.cuboid(box, GizmoStyle.stroke(REGION_COLOR_ARGB, REGION_LINE_WIDTH));
        }
    }
}
