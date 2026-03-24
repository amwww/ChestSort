package dev.dromer.chestsort.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.dromer.chestsort.client.ClientLastInteractedBlock;
import dev.dromer.chestsort.client.ClientNetworkingUtil;
import dev.dromer.chestsort.client.ClientWandSelectionState;
import dev.dromer.chestsort.net.payload.WandSelectPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    private static boolean chestsort$isHoldingWand() {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return false;

        String wandItemId = ClientWandSelectionState.wandItemId();
        if (wandItemId == null || wandItemId.isEmpty()) return false;

        String heldId = String.valueOf(Registries.ITEM.getId(mc.player.getMainHandStack().getItem()));
        String offId = String.valueOf(Registries.ITEM.getId(mc.player.getOffHandStack().getItem()));
        return wandItemId.equals(heldId) || wandItemId.equals(offId);
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (!chestsort$isHoldingWand()) return;

        String dimId = mc.world.getRegistryKey().getValue().toString();
        ClientNetworkingUtil.sendSafe(new WandSelectPayload((byte) 1, dimId, pos.asLong()));

        // Prevent client-side breaking/prediction.
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandUpdateBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!chestsort$isHoldingWand()) return;
        // Extra safety for creative-mode instant breaking paths.
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandUseBlock(net.minecraft.client.network.ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        if (hitResult != null && hitResult.getBlockPos() != null) {
            String dimId = mc.world.getRegistryKey().getValue().toString();
            BlockPos pos = hitResult.getBlockPos();
            ClientLastInteractedBlock.set(dimId, pos.asLong());
        }

        String wandItemId = ClientWandSelectionState.wandItemId();
        if (wandItemId == null || wandItemId.isEmpty()) return;

        String heldId = String.valueOf(Registries.ITEM.getId(mc.player.getStackInHand(hand).getItem()));
        if (!wandItemId.equals(heldId)) return;

        BlockPos pos = hitResult.getBlockPos();
        String dimId = mc.world.getRegistryKey().getValue().toString();
        ClientNetworkingUtil.sendSafe(new WandSelectPayload((byte) 2, dimId, pos.asLong()));

        // Prevent opening/using blocks while selecting.
        cir.setReturnValue(ActionResult.SUCCESS);
        cir.cancel();
    }
}
