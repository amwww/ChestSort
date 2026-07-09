package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> Server: import a preset from a cs2 export string (must be a named preset like `cs2|preset/<name>|...`). */
public record ImportPresetPayload(String data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ImportPresetPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "import_preset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportPresetPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.data == null ? "" : payload.data),
        buf -> new ImportPresetPayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
