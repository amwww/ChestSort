package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.dromer.chestsort.net.payload.SortResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ClientOnlyOrganizer {
    private ClientOnlyOrganizer() {
    }

    public static void organizeOpenContainer(Minecraft client, AbstractContainerMenu handler) {
        if (client == null || handler == null || client.player == null) return;

        if (ClientClickQueue.isActive() || ClientQuickMoveQueue.isActive()) {
            client.player.sendSystemMessage(Component.literal("ChestSort: Busy (action queue running)."));
            return;
        }

        if (!handler.getCarried().isEmpty()) {
            client.player.sendSystemMessage(Component.literal("ChestSort: Clear your cursor stack first."));
            return;
        }

        Inventory playerInv = client.player.getInventory();

        List<Slot> containerSlots = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot == null) continue;
            if (slot.container == playerInv) continue;
            containerSlots.add(slot);
        }

        containerSlots.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));

        List<ClientClickQueue.ClickAction> actions = new ArrayList<>();

        // Pass 1: merge identical stacks into earlier slots.
        for (int targetIndex = 0; targetIndex < containerSlots.size(); targetIndex++) {
            Slot targetSlot = containerSlots.get(targetIndex);
            ItemStack targetStack = targetSlot.getItem();
            if (targetStack.isEmpty()) continue;

            int targetMax = targetStack.getMaxStackSize();
            if (targetStack.getCount() >= targetMax) continue;

            for (int sourceIndex = targetIndex + 1; sourceIndex < containerSlots.size(); sourceIndex++) {
                Slot sourceSlot = containerSlots.get(sourceIndex);
                ItemStack sourceStack = sourceSlot.getItem();
                if (sourceStack.isEmpty()) continue;

                if (!ItemStack.isSameItemSameComponents(targetStack, sourceStack)) continue;

                actions.add(new ClientClickQueue.ClickAction(sourceSlot.index, 0, ContainerInput.PICKUP));
                actions.add(new ClientClickQueue.ClickAction(targetSlot.index, 0, ContainerInput.PICKUP));
                actions.add(new ClientClickQueue.ClickAction(sourceSlot.index, 0, ContainerInput.PICKUP));

                // We can't perfectly predict post-click counts client-side with latency, so just plan merges opportunistically.
            }
        }

        // Pass 2: fill earlier empty slots (compact) by moving later stacks forward.
        for (int targetIndex = 0; targetIndex < containerSlots.size(); targetIndex++) {
            Slot targetSlot = containerSlots.get(targetIndex);
            if (!targetSlot.getItem().isEmpty()) continue;

            int sourceIndex = -1;
            for (int i = targetIndex + 1; i < containerSlots.size(); i++) {
                if (!containerSlots.get(i).getItem().isEmpty()) {
                    sourceIndex = i;
                    break;
                }
            }
            if (sourceIndex == -1) break;

            Slot sourceSlot = containerSlots.get(sourceIndex);

            actions.add(new ClientClickQueue.ClickAction(sourceSlot.index, 0, ContainerInput.PICKUP));
            actions.add(new ClientClickQueue.ClickAction(targetSlot.index, 0, ContainerInput.PICKUP));
            actions.add(new ClientClickQueue.ClickAction(sourceSlot.index, 0, ContainerInput.PICKUP));
        }

        if (actions.isEmpty()) {
            client.player.sendSystemMessage(Component.literal("ChestSort: Nothing to organize."));
            return;
        }

        int moveCount = actions.size() / 3;
        String dimId = ClientContainerContext.dimensionId();
        long posLong = ClientContainerContext.posLong();

        ClientClickQueue.start(handler, actions, () -> {
            ClientSortNotificationState.set(new SortResultPayload(dimId, posLong, SortResultPayload.KIND_ORGANIZE, "", false, 0L, moveCount, List.of()));

            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("ChestSort: Organize complete."));
            }
        });

        client.player.sendSystemMessage(Component.literal("ChestSort: Organizing..."));
    }
}
