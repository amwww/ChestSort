package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.ChestSortNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ClientOnlySorter {
    private ClientOnlySorter() {
    }

    /**
     * Fallback sort: moves matching items from the player's inventory into the open container using vanilla QUICK_MOVE.
     */
    public static void sortIntoOpenContainer(MinecraftClient client, ScreenHandler handler, ContainerFilterSpec whitelistBase, ContainerFilterSpec blacklistBase, boolean whitelistPriority) {
        if (client == null || client.player == null) return;
        if (handler == null || handler.slots == null) return;

        if (ClientClickQueue.isActive() || ClientQuickMoveQueue.isActive()) {
            client.player.sendMessage(Text.literal("[ChestSort] Busy (action queue running)").formatted(Formatting.GRAY), false);
            return;
        }

        PlayerInventory playerInv = client.player.getInventory();
        if (playerInv == null) return;

        ContainerFilterSpec effectiveWhitelist = ClientFilterResolver.resolveWithAppliedPresets(whitelistBase);
        ContainerFilterSpec effectiveBlacklist = ClientFilterResolver.resolveBlacklistWithAppliedPresets(blacklistBase);

        Set<String> allowedItemIds = new HashSet<>(effectiveWhitelist.items() == null ? List.of() : effectiveWhitelist.items());
        List<ChestSortNetworking.TagFilterRuntime> whitelistTagFilters = ChestSortNetworking.compileTagFilters(effectiveWhitelist.tags());

        Set<String> blockedItemIds = new HashSet<>(effectiveBlacklist.items() == null ? List.of() : effectiveBlacklist.items());
        List<ChestSortNetworking.TagFilterRuntime> blacklistTagFilters = ChestSortNetworking.compileTagFilters(effectiveBlacklist.tags());

        ArrayList<Integer> slotIdsToMove = new ArrayList<>();

        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null) continue;
            if (slot.inventory != playerInv) continue;

            int playerIndex = slot.getIndex();
            if (ClientLockedSlotsState.isLocked(playerIndex)) continue;

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            String itemId = String.valueOf(Registries.ITEM.getId(stack.getItem()));

            boolean allowed = allowedItemIds.contains(itemId);
            if (!allowed) {
                for (ChestSortNetworking.TagFilterRuntime tf : whitelistTagFilters) {
                    if (tf.matches(stack, itemId)) {
                        allowed = true;
                        break;
                    }
                }
            }
            if (!allowed) continue;

            boolean blocked = blockedItemIds.contains(itemId);
            if (!blocked) {
                for (ChestSortNetworking.TagFilterRuntime tf : blacklistTagFilters) {
                    if (tf.matches(stack, itemId)) {
                        blocked = true;
                        break;
                    }
                }
            }
            if (blocked && !whitelistPriority) continue;

            slotIdsToMove.add(slotId);
        }

        if (slotIdsToMove.isEmpty()) {
            client.player.sendMessage(Text.literal("[ChestSort] Nothing to move").formatted(Formatting.GRAY), false);
            return;
        }

        client.player.sendMessage(Text.literal("[ChestSort] Sorting (client-only)...").formatted(Formatting.YELLOW), false);

        ClientQuickMoveQueue.start(handler, slotIdsToMove, () -> {
            var p = MinecraftClient.getInstance().player;
            if (p != null) {
                p.sendMessage(Text.literal("[ChestSort] Done").formatted(Formatting.GRAY), false);
            }
        });
    }
}
