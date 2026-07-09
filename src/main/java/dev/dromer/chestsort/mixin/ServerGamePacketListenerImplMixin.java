package dev.dromer.chestsort.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dromer.chestsort.data.ChestSortState;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.ChestSortNetworking;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void chestsort$wandSelectPos1(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.level() instanceof ServerLevel world)) return;
        if (packet == null) return;
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getStringUUID();
        String wandItemId = state.getWandItemId(uuid);
        if (wandItemId == null || wandItemId.isEmpty()) return;

        boolean holdingWand = false;
        if (!player.getMainHandItem().isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
            holdingWand = wandItemId.equals(id);
        }
        if (!holdingWand && !player.getOffhandItem().isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString();
            holdingWand = wandItemId.equals(id);
        }
        if (!holdingWand) return;

        BlockPos pos = packet.getPos();
        if (pos == null) return;

        String dimId = world.dimension().identifier().toString();
        state.setWandPos1(uuid, dimId, pos.asLong());
        player.sendSystemMessage(Component.literal("[CS] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Wand pos1 set to ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.YELLOW)), false);

        // Prevent breaking blocks while selecting.
        ci.cancel();
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void chestsort$preventBlacklistedEntry(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.level() instanceof ServerLevel world)) return;
        if (packet == null) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getStringUUID();
        String mode = state.getBlacklistMode(uuid);
        if (!ChestSortState.blacklistModePreventsEntry(mode)) return;

        if (!(player.containerMenu instanceof ChestMenu handler)) return;

        int slot = packet.slotNum();
        ContainerInput action = packet.containerInput();
        if (action == null) return;

        int containerSize = handler.getContainer().getContainerSize();
        boolean strict = ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(mode);

        // Helper: check blacklist + optional whitelist override.
        java.util.function.Predicate<ItemStack> shouldBlock = (stack) -> {
            if (stack == null || stack.isEmpty()) return false;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!state.isItemBlacklisted(uuid, itemId)) return false;
            if (strict) return true;
            // preventEntry: allow if whitelisted AND whitelistPriority enabled for this container.
            if (chestsort$isWhitelistedInOpenContainer(state, uuid, stack, itemId)) return false;
            return true;
        };

        // 1) Drag-split / drag-place (QUICK_CRAFT): packets often use slot=-999, so detect if any
        // modified slot is a container slot.
        if (action == ContainerInput.QUICK_CRAFT) {
            boolean touchesContainer = false;
            var modified = packet.changedSlots();
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
                ItemStack cursor = handler.getCarried();
                if (shouldBlock.test(cursor)) {
                    ci.cancel();
                    handler.sendAllDataToRemote();
                }
            }
            return;
        }

        // 2) Normal click placing cursor stack into a container slot.
        if (action == ContainerInput.PICKUP && slot >= 0 && slot < containerSize) {
            ItemStack cursor = handler.getCarried();
            if (shouldBlock.test(cursor)) {
                ci.cancel();
                handler.sendAllDataToRemote();
            }
            return;
        }

        // 3) Shift-click from player inventory into container.
        if (action == ContainerInput.QUICK_MOVE && slot >= containerSize) {
            ItemStack clicked = handler.getSlot(slot).getItem();
            if (shouldBlock.test(clicked)) {
                ci.cancel();
                handler.sendAllDataToRemote();
            }
            return;
        }

        // 4) Number-key swap from hotbar into a container slot.
        if (action == ContainerInput.SWAP && slot >= 0 && slot < containerSize) {
            int hotbar = packet.buttonNum();
            if (hotbar >= 0 && hotbar < 9) {
                ItemStack hotbarStack = player.getInventory().getItem(hotbar);
                if (shouldBlock.test(hotbarStack)) {
                    ci.cancel();
                    handler.sendAllDataToRemote();
                }
            }
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void chestsort$preventBlacklistedEntryCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (player == null) return;
        if (!(player.level() instanceof ServerLevel world)) return;
        if (packet == null) return;

        ChestSortState state = ChestSortState.get(world.getServer());
        String uuid = player.getStringUUID();
        String mode = state.getBlacklistMode(uuid);
        if (!ChestSortState.blacklistModePreventsEntry(mode)) return;

        // Only applies to generic containers (chests/barrels/shulkers).
        if (!(player.containerMenu instanceof ChestMenu handler)) return;

        int containerSize = handler.getContainer().getContainerSize();
        int slot = packet.slotNum();
        if (slot < 0 || slot >= containerSize) return;

        ItemStack stack = packet.itemStack();
        if (stack == null || stack.isEmpty()) return;

String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!state.isItemBlacklisted(uuid, itemId)) return;

        boolean strict = ChestSortState.BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(mode);
        if (!strict && chestsort$isWhitelistedInOpenContainer(state, uuid, stack, itemId)) return;

        ci.cancel();
        handler.sendAllDataToRemote();
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
