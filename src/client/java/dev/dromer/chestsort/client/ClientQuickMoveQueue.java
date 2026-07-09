package dev.dromer.chestsort.client;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.Predicate;

import dev.dromer.chestsort.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Runs a sequence of vanilla QUICK_MOVE (shift-click) slot clicks over time.
 * This is used as the client-only sorter, since the mod does not rely on any server support.
 */
public final class ClientQuickMoveQueue {
    private static final ArrayDeque<Integer> pendingSlotIds = new ArrayDeque<>();
    private static int pendingSyncId = -1;
    private static Predicate<ItemStack> stillWanted;
    private static Runnable onDone;

    private ClientQuickMoveQueue() {
    }

    public static boolean isActive() {
        return !pendingSlotIds.isEmpty();
    }

    public static void clear() {
        pendingSlotIds.clear();
        pendingSyncId = -1;
        stillWanted = null;
        onDone = null;
    }

    public static void start(AbstractContainerMenu handler, Collection<Integer> slotIds, Predicate<ItemStack> stillWantedPredicate, Runnable onDoneCallback) {
        clear();
        if (handler == null || slotIds == null || slotIds.isEmpty()) return;
        pendingSyncId = handler.containerId;
        pendingSlotIds.addAll(slotIds);
        stillWanted = stillWantedPredicate;
        onDone = onDoneCallback;
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        if (ClientClickQueue.isActive()) return;
        if (pendingSlotIds.isEmpty()) return;

        if (client.player == null || client.gameMode == null) {
            clear();
            return;
        }

        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> handled)) {
            clear();
            return;
        }

        AbstractContainerMenu handler = ((AbstractContainerScreenAccessor) (Object) handled).chestsort$getHandler();
        if (handler == null || handler.containerId != pendingSyncId) {
            clear();
            return;
        }

        Integer slotId = pendingSlotIds.pollFirst();
        if (slotId == null) {
            clear();
            return;
        }

        // Re-validate against the CURRENT item in this slot before clicking. Earlier clicks in
        // this same batch (stack merges, auto-consolidation) can change what's sitting in a
        // player-inventory slot by the time its turn comes up; skip rather than move the wrong item.
        Slot slot = handler.getSlot(slotId);
        if (slot == null || stillWanted == null || !stillWanted.test(slot.getItem())) {
            if (pendingSlotIds.isEmpty()) {
                Runnable done = onDone;
                clear();
                if (done != null) done.run();
            }
            return;
        }

        client.gameMode.handleContainerInput(handler.containerId, slotId, 0, ContainerInput.QUICK_MOVE, client.player);

        if (pendingSlotIds.isEmpty()) {
            Runnable done = onDone;
            clear();
            if (done != null) done.run();
        }
    }
}
