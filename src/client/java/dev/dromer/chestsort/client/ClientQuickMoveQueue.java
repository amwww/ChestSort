package dev.dromer.chestsort.client;

import java.util.ArrayDeque;
import java.util.Collection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Runs a sequence of vanilla QUICK_MOVE (shift-click) slot clicks over time.
 * This is used as a fallback on servers that don't have ChestSort installed.
 */
public final class ClientQuickMoveQueue {
    private static final ArrayDeque<Integer> pendingSlotIds = new ArrayDeque<>();
    private static int pendingSyncId = -1;
    private static Runnable onDone;

    private ClientQuickMoveQueue() {
    }

    public static boolean isActive() {
        return !pendingSlotIds.isEmpty();
    }

    public static void clear() {
        pendingSlotIds.clear();
        pendingSyncId = -1;
        onDone = null;
    }

    public static void start(ScreenHandler handler, Collection<Integer> slotIds, Runnable onDoneCallback) {
        clear();
        if (handler == null || slotIds == null || slotIds.isEmpty()) return;
        pendingSyncId = handler.syncId;
        pendingSlotIds.addAll(slotIds);
        onDone = onDoneCallback;
    }

    public static void tick(MinecraftClient client) {
        if (client == null) return;
        if (ClientClickQueue.isActive()) return;
        if (pendingSlotIds.isEmpty()) return;

        if (client.player == null || client.interactionManager == null) {
            clear();
            return;
        }

        if (!(client.currentScreen instanceof HandledScreen<?> handled)) {
            clear();
            return;
        }

        ScreenHandler handler = handled.getScreenHandler();
        if (handler == null || handler.syncId != pendingSyncId) {
            clear();
            return;
        }

        Integer slotId = pendingSlotIds.pollFirst();
        if (slotId == null) {
            clear();
            return;
        }

        client.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, client.player);

        if (pendingSlotIds.isEmpty()) {
            Runnable done = onDone;
            clear();
            if (done != null) done.run();
        }
    }
}
