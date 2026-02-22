package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> Server: import a preset from a cs2 export string (must be a named preset like `cs2|preset/<name>|...`). */
public record ImportPresetPayload(String data) implements CustomPayload {
    public static final Id<ImportPresetPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "import_preset"));

    public static final PacketCodec<RegistryByteBuf, ImportPresetPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> buf.writeString(payload.data == null ? "" : payload.data),
        buf -> new ImportPresetPayload(buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
