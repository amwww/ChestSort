package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> Client: request opening the preset UI inside the container filter screen. */
public record OpenPresetUiPayload(byte mode, String name) implements CustomPacketPayload {
    /** Open the preset editor for an existing/new preset name. */
    public static final byte MODE_EDIT = 0;
    /** Open the preset-import popup (paste export code). */
    public static final byte MODE_IMPORT = 1;
    /** Open the preset-export popup (copy export code). */
    public static final byte MODE_EXPORT = 2;
    /** Open the preset-export-all screen (exports all presets as a presetList). */
    public static final byte MODE_EXPORT_ALL = 3;
    /** Open the preset-export-select screen (choose presets to export as a presetList). */
    public static final byte MODE_EXPORT_SELECT = 4;

    public static final CustomPacketPayload.Type<OpenPresetUiPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "open_preset_ui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPresetUiPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeByte(payload.mode);
            buf.writeUtf(payload.name == null ? "" : payload.name);
        },
        buf -> new OpenPresetUiPayload(buf.readByte(), buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
