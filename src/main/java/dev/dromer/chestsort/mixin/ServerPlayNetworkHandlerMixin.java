package dev.dromer.chestsort.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dromer.chestsort.data.ChestSortState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandSelectPos1(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        if (packet == null) return;
        if (packet.getAction() != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getUuidAsString();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId == null || wandItemId.isEmpty()) return;

        boolean holdingWand = false;
        if (!player.getMainHandStack().isEmpty()) {
            String id = String.valueOf(Registries.ITEM.getId(player.getMainHandStack().getItem()));
            holdingWand = wandItemId.equals(id);
        }
        if (!holdingWand && !player.getOffHandStack().isEmpty()) {
            String id = String.valueOf(Registries.ITEM.getId(player.getOffHandStack().getItem()));
            holdingWand = wandItemId.equals(id);
        }
        if (!holdingWand) return;

        BlockPos pos = packet.getPos();
        if (pos == null) return;

        String dimId = world.getRegistryKey().getValue().toString();
        state.setWandPos1(uuid, dimId, pos.asLong());
        player.sendMessage(Text.literal("[CS] ").formatted(Formatting.GOLD)
            .append(Text.literal("Wand pos1 set to ").formatted(Formatting.GRAY))
            .append(Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.YELLOW)), false);

        // Prevent breaking blocks while selecting.
        ci.cancel();
    }
}
