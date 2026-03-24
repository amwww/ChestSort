package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class ClientOnlyOrganizer {
    private ClientOnlyOrganizer() {
    }

    public static void organizeOpenContainer(MinecraftClient client, ScreenHandler handler) {
        if (client == null || handler == null || client.player == null) return;

        if (ClientClickQueue.isActive() || ClientQuickMoveQueue.isActive()) {
            client.player.sendMessage(Text.literal("ChestSort: Busy (action queue running)."), true);
            return;
        }

        if (!handler.getCursorStack().isEmpty()) {
            client.player.sendMessage(Text.literal("ChestSort: Clear your cursor stack first."), true);
            return;
        }

        PlayerInventory playerInv = client.player.getInventory();

        List<Slot> containerSlots = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot == null) continue;
            if (slot.inventory == playerInv) continue;
            containerSlots.add(slot);
        }

        containerSlots.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));

        List<ClientClickQueue.ClickAction> actions = new ArrayList<>();

        // Pass 1: merge identical stacks into earlier slots.
        for (int targetIndex = 0; targetIndex < containerSlots.size(); targetIndex++) {
            Slot targetSlot = containerSlots.get(targetIndex);
            ItemStack targetStack = targetSlot.getStack();
            if (targetStack.isEmpty()) continue;

            int targetMax = targetStack.getMaxCount();
            if (targetStack.getCount() >= targetMax) continue;

            for (int sourceIndex = targetIndex + 1; sourceIndex < containerSlots.size(); sourceIndex++) {
                Slot sourceSlot = containerSlots.get(sourceIndex);
                ItemStack sourceStack = sourceSlot.getStack();
                if (sourceStack.isEmpty()) continue;

                if (!ItemStack.areItemsAndComponentsEqual(targetStack, sourceStack)) continue;

                actions.add(new ClientClickQueue.ClickAction(sourceSlot.id, 0, SlotActionType.PICKUP));
                actions.add(new ClientClickQueue.ClickAction(targetSlot.id, 0, SlotActionType.PICKUP));
                actions.add(new ClientClickQueue.ClickAction(sourceSlot.id, 0, SlotActionType.PICKUP));

                // We can't perfectly predict post-click counts client-side with latency, so just plan merges opportunistically.
            }
        }

        // Pass 2: fill earlier empty slots (compact) by moving later stacks forward.
        for (int targetIndex = 0; targetIndex < containerSlots.size(); targetIndex++) {
            Slot targetSlot = containerSlots.get(targetIndex);
            if (!targetSlot.getStack().isEmpty()) continue;

            int sourceIndex = -1;
            for (int i = targetIndex + 1; i < containerSlots.size(); i++) {
                if (!containerSlots.get(i).getStack().isEmpty()) {
                    sourceIndex = i;
                    break;
                }
            }
            if (sourceIndex == -1) break;

            Slot sourceSlot = containerSlots.get(sourceIndex);

            actions.add(new ClientClickQueue.ClickAction(sourceSlot.id, 0, SlotActionType.PICKUP));
            actions.add(new ClientClickQueue.ClickAction(targetSlot.id, 0, SlotActionType.PICKUP));
            actions.add(new ClientClickQueue.ClickAction(sourceSlot.id, 0, SlotActionType.PICKUP));
        }

        if (actions.isEmpty()) {
            client.player.sendMessage(Text.literal("ChestSort: Nothing to organize."), true);
            return;
        }

        ClientClickQueue.start(handler, actions, () -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("ChestSort: Organize complete."), true);
            }
        });

        client.player.sendMessage(Text.literal("ChestSort: Organizing (client-side)…"), true);
    }
}
