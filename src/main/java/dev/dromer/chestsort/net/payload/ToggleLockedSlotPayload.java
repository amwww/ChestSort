package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: toggle whether a player inventory slot is protected from sorting. */
public record ToggleLockedSlotPayload(int playerInventoryIndex) implements CustomPayload {
    public static final Id<ToggleLockedSlotPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "toggle_locked_slot"));

    public static final PacketCodec<RegistryByteBuf, ToggleLockedSlotPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> buf.writeVarInt(payload.playerInventoryIndex),
        buf -> new ToggleLockedSlotPayload(buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
