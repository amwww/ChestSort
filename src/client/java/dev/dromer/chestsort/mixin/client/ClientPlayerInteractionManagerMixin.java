package dev.dromer.chestsort.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.dromer.chestsort.client.ClientContainerCanonicalizer;
import dev.dromer.chestsort.client.ClientLastInteractedBlock;
import dev.dromer.chestsort.client.ClientWandState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {

    private static boolean chestsort$isHoldingWand() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return false;

        String wandItemId = ClientWandState.wandItemId();
        if (wandItemId == null || wandItemId.isEmpty()) return false;

        String heldId = BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString();
        String offId = BuiltInRegistries.ITEM.getKey(mc.player.getOffhandItem().getItem()).toString();
        return wandItemId.equals(heldId) || wandItemId.equals(offId);
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        if (!chestsort$isHoldingWand()) return;

        String dimId = mc.level.dimension().identifier().toString();
        ClientWandState.setPos1(dimId, pos.asLong());

        // Prevent client-side breaking/prediction.
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandUpdateBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!chestsort$isHoldingWand()) return;
        // Extra safety for creative-mode instant breaking paths.
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandUseBlock(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        if (hitResult != null && hitResult.getBlockPos() != null) {
            String hitDimId = mc.level.dimension().identifier().toString();
            BlockPos hitPos = hitResult.getBlockPos();
            var be = mc.level.getBlockEntity(hitPos);
            var canonical = be == null ? null : ClientContainerCanonicalizer.canonicalize(mc.level, hitPos, be);
            long canonicalPosLong = canonical != null ? canonical.posLong() : hitPos.asLong();
            ClientLastInteractedBlock.set(hitDimId, canonicalPosLong);
        }

        String wandItemId = ClientWandState.wandItemId();
        if (wandItemId == null || wandItemId.isEmpty()) return;

        String heldId = BuiltInRegistries.ITEM.getKey(mc.player.getItemInHand(hand).getItem()).toString();
        if (!wandItemId.equals(heldId)) return;

        BlockPos pos = hitResult.getBlockPos();
        String dimId = mc.level.dimension().identifier().toString();
        ClientWandState.setPos2(dimId, pos.asLong());

        // Prevent opening/using blocks while selecting.
        cir.setReturnValue(InteractionResult.SUCCESS);
        cir.cancel();
    }
}
