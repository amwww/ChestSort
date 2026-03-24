package dev.dromer.chestsort.client;

import java.util.ArrayDeque;
import java.util.Collection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/** Runs a sequence of vanilla slot click actions over time (one per tick). */
public final class ClientClickQueue {
    public record ClickAction(int slotId, int button, SlotActionType actionType) {
    }

    private static final ArrayDeque<ClickAction> pending = new ArrayDeque<>();
    private static int pendingSyncId = -1;
    private static Runnable onDone;

    private ClientClickQueue() {
    }

    public static boolean isActive() {
        return !pending.isEmpty();
    }

    public static void clear() {
        pending.clear();
        pendingSyncId = -1;
        onDone = null;
    }

    public static void start(ScreenHandler handler, Collection<ClickAction> actions, Runnable onDoneCallback) {
        clear();
        if (handler == null || actions == null || actions.isEmpty()) return;
        pendingSyncId = handler.syncId;
        pending.addAll(actions);
        onDone = onDoneCallback;
    }

    public static void tick(MinecraftClient client) {
        if (client == null) return;
        if (pending.isEmpty()) return;

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

        ClickAction action = pending.pollFirst();
        if (action == null) {
            clear();
            return;
        }

        client.interactionManager.clickSlot(handler.syncId, action.slotId(), action.button(), action.actionType(), client.player);

        if (pending.isEmpty()) {
            Runnable done = onDone;
            clear();
            if (done != null) done.run();
        }
    }
}
