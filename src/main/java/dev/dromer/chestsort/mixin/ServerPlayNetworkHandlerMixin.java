package dev.dromer.chestsort.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dromer.chestsort.data.ChestSortState;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.ChestSortNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
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

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void chestsort$preventBlacklistedEntry(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        if (packet == null) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getUuidAsString();
        String mode = state.getBlacklistMode(uuid);
        if (!ChestSortState.blacklistModePreventsEntry(mode)) return;

        if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;

        int slot = packet.slot();
        SlotActionType action = packet.actionType();
        if (action == null) return;

        int containerSize = handler.getInventory().size();
        boolean strict = ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(mode);

        // Helper: check blacklist + optional whitelist override.
        java.util.function.Predicate<ItemStack> shouldBlock = (stack) -> {
            if (stack == null || stack.isEmpty()) return false;
            String itemId = String.valueOf(Registries.ITEM.getId(stack.getItem()));
            if (!state.isItemBlacklisted(uuid, itemId)) return false;
            if (strict) return true;
            // preventEntry: allow if whitelisted AND whitelistPriority enabled for this container.
            if (chestsort$isWhitelistedInOpenContainer(state, uuid, stack, itemId)) return false;
            return true;
        };

        // 1) Drag-split / drag-place (QUICK_CRAFT): packets often use slot=-999, so detect if any
        // modified slot is a container slot.
        if (action == SlotActionType.QUICK_CRAFT) {
            boolean touchesContainer = false;
            var modified = packet.modifiedStacks();
            if (modified != null && !modified.isEmpty()) {
                for (var e : modified.int2ObjectEntrySet()) {
                    int idx = e.getIntKey();
                    if (idx >= 0 && idx < containerSize) {
                        touchesContainer = true;
                        break;
                    }
                }
            }
            if (touchesContainer) {
                ItemStack cursor = handler.getCursorStack();
                if (shouldBlock.test(cursor)) {
                    ci.cancel();
                    handler.sendContentUpdates();
                }
            }
            return;
        }

        // 2) Normal click placing cursor stack into a container slot.
        if (action == SlotActionType.PICKUP && slot >= 0 && slot < containerSize) {
            ItemStack cursor = handler.getCursorStack();
            if (shouldBlock.test(cursor)) {
                ci.cancel();
                handler.sendContentUpdates();
            }
            return;
        }

        // 3) Shift-click from player inventory into container.
        if (action == SlotActionType.QUICK_MOVE && slot >= containerSize) {
            ItemStack clicked = handler.getSlot(slot).getStack();
            if (shouldBlock.test(clicked)) {
                ci.cancel();
                handler.sendContentUpdates();
            }
            return;
        }

        // 4) Number-key swap from hotbar into a container slot.
        if (action == SlotActionType.SWAP && slot >= 0 && slot < containerSize) {
            int hotbar = packet.button();
            if (hotbar >= 0 && hotbar < 9) {
                ItemStack hotbarStack = player.getInventory().getStack(hotbar);
                if (shouldBlock.test(hotbarStack)) {
                    ci.cancel();
                    handler.sendContentUpdates();
                }
            }
        }
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void chestsort$preventBlacklistedEntryCreative(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        if (packet == null) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getUuidAsString();
        String mode = state.getBlacklistMode(uuid);
        if (!ChestSortState.blacklistModePreventsEntry(mode)) return;

        // Only applies to generic containers (chests/barrels/shulkers).
        if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;

        int containerSize = handler.getInventory().size();
        int slot = packet.slot();
        if (slot < 0 || slot >= containerSize) return;

        ItemStack stack = packet.stack();
        if (stack == null || stack.isEmpty()) return;

        String itemId = String.valueOf(Registries.ITEM.getId(stack.getItem()));
        if (!state.isItemBlacklisted(uuid, itemId)) return;

        boolean strict = ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(mode);
        if (!strict && chestsort$isWhitelistedInOpenContainer(state, uuid, stack, itemId)) return;

        ci.cancel();
        handler.sendContentUpdates();
    }

    private static boolean chestsort$isWhitelistedInOpenContainer(ChestSortState state, String playerUuid, ItemStack stack, String itemId) {
        if (state == null) return false;
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        ChestSortState.OpenContainerRef open = state.getOpenContainer(playerUuid);
        if (open == null) return false;

        boolean whitelistPriority = state.whitelistPriority(open.dimensionId(), open.posLong());
        if (!whitelistPriority) return false;

        ContainerFilterSpec base = state.getFilterSpec(open.dimensionId(), open.posLong());
        if (base == null || base.isEmpty()) return false;

        ContainerFilterSpec effective = state.resolveWithAppliedPresets(base);
        if (effective == null || effective.isEmpty()) return false;

        ContainerFilterSpec w = effective.normalized();
        if (w.items() != null && w.items().contains(itemId)) return true;

        for (ChestSortNetworking.TagFilterRuntime tf : ChestSortNetworking.compileTagFilters(w.tags())) {
            if (tf.matches(stack, itemId)) return true;
        }

        return false;
    }
}
