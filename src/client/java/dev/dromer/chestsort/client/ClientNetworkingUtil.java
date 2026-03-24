package dev.dromer.chestsort.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

public final class ClientNetworkingUtil {
    private ClientNetworkingUtil() {
    }

    public static boolean canSend(CustomPayload.Id<?> id) {
        if (id == null) return false;
        try {
            return ClientPlayNetworking.canSend(id);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean sendSafe(CustomPayload payload) {
        if (payload == null) return false;
        if (!canSend(payload.getId())) return false;
        ClientPlayNetworking.send(payload);
        return true;
    }
}
