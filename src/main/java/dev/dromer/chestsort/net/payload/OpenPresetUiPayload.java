package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> Client: request opening the preset UI inside the container filter screen. */
public record OpenPresetUiPayload(byte mode, String name) implements CustomPayload {
    /** Open the preset editor for an existing/new preset name. */
    public static final byte MODE_EDIT = 0;
    /** Open the preset-import popup (paste export code). */
    public static final byte MODE_IMPORT = 1;
    /** Open the preset-export popup (copy export code). */
    public static final byte MODE_EXPORT = 2;

    public static final Id<OpenPresetUiPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "open_preset_ui"));

    public static final PacketCodec<RegistryByteBuf, OpenPresetUiPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeByte(payload.mode);
            buf.writeString(payload.name == null ? "" : payload.name);
        },
        buf -> new OpenPresetUiPayload(buf.readByte(), buf.readString())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
