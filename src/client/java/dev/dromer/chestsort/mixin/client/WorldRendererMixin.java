package dev.dromer.chestsort.mixin.client;

import dev.dromer.chestsort.client.ClientFindHighlightRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("TAIL")
    )
    private void chestsort$renderFindHighlights(
        ObjectAllocator allocator,
        RenderTickCounter tickCounter,
        boolean renderBlockOutline,
        Camera camera,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        Matrix4f frustumMatrix,
        GpuBufferSlice fogUniforms,
        Vector4f fogColor,
        boolean bl,
        CallbackInfo ci
    ) {
        ClientFindHighlightRenderer.render(positionMatrix, camera);
    }
}