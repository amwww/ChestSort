package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: toggle whether a player inventory slot is protected from sorting. */
public record ToggleLockedSlotPayload(int playerInventoryIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToggleLockedSlotPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "toggle_locked_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleLockedSlotPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeVarInt(payload.playerInventoryIndex),
        buf -> new ToggleLockedSlotPayload(buf.readVarInt())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
