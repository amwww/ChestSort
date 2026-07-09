package dev.dromer.chestsort.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworkingUtil {
    private ClientNetworkingUtil() {
    }

    public static boolean canSend(CustomPacketPayload.Type<?> id) {
        if (id == null) return false;
        try {
            return ClientPlayNetworking.canSend(id);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean sendSafe(CustomPacketPayload payload) {
        if (payload == null) return false;
        if (!canSend(payload.type())) {
            System.err.println("[ChestSort] sendSafe: canSend() returned false for " + payload);
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }
}
