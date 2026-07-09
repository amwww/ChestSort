package dev.dromer.chestsort.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens a screen on the next tick where no screen is currently active. Needed because vanilla
 * closes the chat/command screen right after dispatching a command, which would otherwise
 * immediately stomp a screen opened synchronously from within a command's execution.
 */
public final class ClientPendingScreen {
    private static Screen pending = null;

    private ClientPendingScreen() {
    }

    public static void open(Screen screen) {
        pending = screen;
    }

    public static void tick(Minecraft client) {
        if (pending == null) return;
        if (client.gui.screen() != null) return;

        Screen screen = pending;
        pending = null;
        client.gui.setScreen(screen);
    }
}
