package dev.dromer.chestsort.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

public final class ClientFindHighlightRenderer {
    private ClientFindHighlightRenderer() {
    }

    private static final int OUTLINE_COLOR_ARGB = 0x80FFFF00;
    private static final float OUTLINE_LINE_WIDTH = 2.0f;

    public static void render(Matrix4f positionMatrix, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || positionMatrix == null || camera == null) return;

        // Render wand selection overlay first (independent of /cs find highlights).
        ClientWandSelectionRenderer.render(positionMatrix, camera);

        String dimId = mc.world.getRegistryKey().getValue().toString();
        if (!ClientFindHighlightState.isActiveFor(dimId)) return;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);

        // WorldRenderer's matrices in 1.21.x are not guaranteed to include camera translation;
        // render in camera-relative coordinates to ensure the outline appears at the right place.
        Vec3d camPos = camera.getCameraPos();

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEffectVertexConsumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

        for (Long l : ClientFindHighlightState.posLongs()) {
            if (l == null) continue;
            BlockPos pos = BlockPos.fromLong(l);
            VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
            if (shape == null || shape.isEmpty()) continue;

            VertexRendering.drawOutline(
                matrices,
                lines,
                shape,
                pos.getX() - camPos.x,
                pos.getY() - camPos.y,
                pos.getZ() - camPos.z,
                OUTLINE_COLOR_ARGB,
                OUTLINE_LINE_WIDTH
            );
        }

        consumers.draw(RenderLayers.lines());
    }
}
