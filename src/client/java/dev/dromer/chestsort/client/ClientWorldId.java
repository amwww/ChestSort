package dev.dromer.chestsort.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Stable identifier for "which world/server am I connected to right now", used to scope
 * per-container sorting data (filters, autosort, find index) so unrelated worlds/servers
 * that happen to share block coordinates don't collide. Presets and global settings are
 * intentionally NOT scoped by this - those stay shared across every world.
 */
public final class ClientWorldId {
    private ClientWorldId() {
    }

    /** Never empty; falls back to a generic bucket if nothing is available. */
    public static String current() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return "unknown";

        try {
            if (client.isLocalServer() && client.hasSingleplayerServer()) {
                var server = client.getSingleplayerServer();
                if (server != null) {
                    String folder = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
                    if (folder != null && !folder.isEmpty()) return "sp:" + folder;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            var serverData = client.getCurrentServer();
            if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
                return "mp:" + serverData.ip.toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }

        return "unknown";
    }
}
