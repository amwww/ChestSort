package dev.dromer.chestsort.client;

import java.util.ArrayDeque;
import java.util.Collection;

import dev.dromer.chestsort.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/** Runs a sequence of vanilla slot click actions over time (one per tick). */
public final class ClientClickQueue {
    public record ClickAction(int slotId, int button, ContainerInput actionType) {
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

    public static void start(AbstractContainerMenu handler, Collection<ClickAction> actions, Runnable onDoneCallback) {
        clear();
        if (handler == null || actions == null || actions.isEmpty()) return;
        pendingSyncId = handler.containerId;
        pending.addAll(actions);
        onDone = onDoneCallback;
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        if (pending.isEmpty()) return;

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

        ClickAction action = pending.pollFirst();
        if (action == null) {
            clear();
            return;
        }

        client.gameMode.handleContainerInput(handler.containerId, action.slotId(), action.button(), action.actionType(), client.player);

        if (pending.isEmpty()) {
            Runnable done = onDone;
            clear();
            if (done != null) done.run();
        }
    }
}
