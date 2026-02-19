package dev.dromer.chestsort.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

public final class ClientWandSelectionRenderer {
    private ClientWandSelectionRenderer() {
    }

    private static final int REGION_COLOR_ARGB = 0x80FFFF00;
    private static final int POS1_COLOR_ARGB = 0x8000FF00;
    private static final int POS2_COLOR_ARGB = 0x80FF0000;
    private static final float REGION_LINE_WIDTH = 2.0f;
    private static final float POS_LINE_WIDTH = 3.0f;

    public static void render(Matrix4f positionMatrix, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || positionMatrix == null || camera == null) return;

        String dimId = mc.world.getRegistryKey().getValue().toString();

        boolean has1 = ClientWandSelectionState.hasPos1In(dimId);
        boolean has2 = ClientWandSelectionState.hasPos2In(dimId);
        if (!has1 && !has2) return;

        BlockPos p1 = ClientWandSelectionState.pos1();
        BlockPos p2 = ClientWandSelectionState.pos2();

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);

        Vec3d camPos = camera.getCameraPos();

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEffectVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

        if (has1 && p1 != null) {
            VoxelShape shape = mc.world.getBlockState(p1).getOutlineShape(mc.world, p1);
            if (shape != null && !shape.isEmpty()) {
                VertexRendering.drawOutline(matrices, lines, shape,
                    p1.getX() - camPos.x,
                    p1.getY() - camPos.y,
                    p1.getZ() - camPos.z,
                    POS1_COLOR_ARGB,
                    POS_LINE_WIDTH);
            }
        }

        if (has2 && p2 != null) {
            VoxelShape shape = mc.world.getBlockState(p2).getOutlineShape(mc.world, p2);
            if (shape != null && !shape.isEmpty()) {
                VertexRendering.drawOutline(matrices, lines, shape,
                    p2.getX() - camPos.x,
                    p2.getY() - camPos.y,
                    p2.getZ() - camPos.z,
                    POS2_COLOR_ARGB,
                    POS_LINE_WIDTH);
            }
        }

        if (has1 && has2 && p1 != null && p2 != null) {
            int minX = Math.min(p1.getX(), p2.getX());
            int minY = Math.min(p1.getY(), p2.getY());
            int minZ = Math.min(p1.getZ(), p2.getZ());
            int maxX = Math.max(p1.getX(), p2.getX());
            int maxY = Math.max(p1.getY(), p2.getY());
            int maxZ = Math.max(p1.getZ(), p2.getZ());

            double sx = (double) (maxX - minX + 1);
            double sy = (double) (maxY - minY + 1);
            double sz = (double) (maxZ - minZ + 1);

            VoxelShape box = VoxelShapes.cuboid(new Box(0, 0, 0, sx, sy, sz));

            VertexRendering.drawOutline(
                matrices,
                lines,
                box,
                minX - camPos.x,
                minY - camPos.y,
                minZ - camPos.z,
                REGION_COLOR_ARGB,
                REGION_LINE_WIDTH
            );
        }

        consumers.draw(RenderLayers.lines());
    }
}
