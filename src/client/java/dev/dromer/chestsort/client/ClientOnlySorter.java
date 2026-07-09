package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.net.ChestSortNetworking;
import dev.dromer.chestsort.net.payload.SortResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

public final class ClientOnlySorter {
    private ClientOnlySorter() {
    }

    /**
     * Moves matching items from the player's inventory into the open container using vanilla
     * QUICK_MOVE. This mod does not rely on any server-side support - sorting is always done by
     * simulating the same clicks a player would make.
     */
    public static void sortIntoOpenContainer(Minecraft client, AbstractContainerMenu handler, ContainerFilterSpec whitelistBase, ContainerFilterSpec blacklistBase, boolean whitelistPriority) {
        if (client == null || client.player == null) return;
        if (handler == null || handler.slots == null) return;

        if (ClientClickQueue.isActive() || ClientQuickMoveQueue.isActive()) {
            client.player.sendSystemMessage(Component.literal("[ChestSort] Busy (action queue running)").withStyle(ChatFormatting.GRAY));
            return;
        }

        Inventory playerInv = client.player.getInventory();
        if (playerInv == null) return;

        ContainerFilterSpec effectiveWhitelist = ClientFilterResolver.resolveWithAppliedPresets(whitelistBase);
        ContainerFilterSpec effectiveBlacklist = ClientFilterResolver.resolveBlacklistWithAppliedPresets(blacklistBase);

        Predicate<ItemStack> matches = chestsort$buildMatcher(effectiveWhitelist, effectiveBlacklist, whitelistPriority);

        ArrayList<Integer> slotIdsToMove = new ArrayList<>();
        for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
            Slot slot = handler.slots.get(slotId);
            if (slot == null) continue;
            if (slot.container != playerInv) continue;

            int playerIndex = slot.index;
            if (ClientLockedSlotsState.isLocked(playerIndex)) continue;

            if (matches.test(slot.getItem())) {
                slotIdsToMove.add(slotId);
            }
        }

        if (slotIdsToMove.isEmpty()) {
            client.player.sendSystemMessage(Component.literal("[ChestSort] Nothing to move").withStyle(ChatFormatting.GRAY));
            return;
        }

        client.player.sendSystemMessage(Component.literal("[ChestSort] Sorting...").withStyle(ChatFormatting.YELLOW));

        // Track what actually gets clicked (not just what we planned to move), since the queue
        // re-validates each slot right before clicking and may skip some.
        Map<String, Integer> movedCounts = new LinkedHashMap<>();
        Predicate<ItemStack> trackingMatches = stack -> {
            boolean ok = matches.test(stack);
            if (ok) {
                String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                movedCounts.merge(itemId, stack.getCount(), Integer::sum);
            }
            return ok;
        };

        String dimId = ClientContainerContext.dimensionId();
        long posLong = ClientContainerContext.posLong();

        ClientQuickMoveQueue.start(handler, slotIdsToMove, trackingMatches, () -> {
            var p = Minecraft.getInstance().player;

            int movedTotal = 0;
            List<SortResultPayload.SortLine> lines = new ArrayList<>(movedCounts.size());
            for (var e : movedCounts.entrySet()) {
                lines.add(new SortResultPayload.SortLine(e.getKey(), e.getValue(), List.of()));
                movedTotal += e.getValue();
            }
            ClientSortNotificationState.set(new SortResultPayload(dimId, posLong, SortResultPayload.KIND_SORT, "", false, 0L, movedTotal, lines));

            if (p != null) {
                p.sendSystemMessage(Component.literal("[ChestSort] Done").withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private static Predicate<ItemStack> chestsort$buildMatcher(ContainerFilterSpec effectiveWhitelist, ContainerFilterSpec effectiveBlacklist, boolean whitelistPriority) {
        Set<String> allowedItemIds = new HashSet<>(effectiveWhitelist.items() == null ? List.of() : effectiveWhitelist.items());
        List<ChestSortNetworking.TagFilterRuntime> whitelistTagFilters = ChestSortNetworking.compileTagFilters(effectiveWhitelist.tags());

        Set<String> blockedItemIds = new HashSet<>(effectiveBlacklist.items() == null ? List.of() : effectiveBlacklist.items());
        List<ChestSortNetworking.TagFilterRuntime> blacklistTagFilters = ChestSortNetworking.compileTagFilters(effectiveBlacklist.tags());

        return stack -> {
            if (stack == null || stack.isEmpty()) return false;

            String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));

            boolean allowed = allowedItemIds.contains(itemId);
            if (!allowed) {
                for (ChestSortNetworking.TagFilterRuntime tf : whitelistTagFilters) {
                    if (tf.matches(stack, itemId)) {
                        allowed = true;
                        break;
                    }
                }
            }
            if (!allowed) return false;

            boolean blocked = blockedItemIds.contains(itemId);
            if (!blocked) {
                for (ChestSortNetworking.TagFilterRuntime tf : blacklistTagFilters) {
                    if (tf.matches(stack, itemId)) {
                        blocked = true;
                        break;
                    }
                }
            }
            return !blocked || whitelistPriority;
        };
    }
}
