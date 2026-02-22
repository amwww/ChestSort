package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Client -> Server: set (create/update) a preset by name (whitelist + blacklist). */
public record SetPresetV2Payload(String name, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist) implements CustomPayload {
    public static final Id<SetPresetV2Payload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "set_preset_v2"));

    public static final PacketCodec<RegistryByteBuf, SetPresetV2Payload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.name == null ? "" : payload.name);
            writeSpec(buf, payload.whitelist);
            writeSpec(buf, payload.blacklist);
        },
        buf -> {
            String name = buf.readString();
            ContainerFilterSpec wl = readSpec(buf);
            ContainerFilterSpec bl = readSpec(buf);
            return new SetPresetV2Payload(name, wl, bl);
        }
    );

    private static void writeSpec(RegistryByteBuf buf, ContainerFilterSpec spec) {
        ContainerFilterSpec safe = spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec;
        List<String> items = safe.items() == null ? List.of() : safe.items();
        List<TagFilterSpec> tags = safe.tags() == null ? List.of() : safe.tags();

        buf.writeVarInt(items.size());
        for (String itemId : items) {
            buf.writeString(itemId == null ? "" : itemId);
        }

        buf.writeVarInt(tags.size());
        for (TagFilterSpec tag : tags) {
            String tagId = tag == null ? "" : (tag.tagId() == null ? "" : tag.tagId());
            buf.writeString(tagId);
            List<String> exceptions = (tag == null || tag.exceptions() == null) ? List.of() : tag.exceptions();
            buf.writeVarInt(exceptions.size());
            for (String exc : exceptions) {
                buf.writeString(exc == null ? "" : exc);
            }
        }
    }

    private static ContainerFilterSpec readSpec(RegistryByteBuf buf) {
        int itemsN = buf.readVarInt();
        ArrayList<String> items = new ArrayList<>(Math.max(0, itemsN));
        for (int j = 0; j < itemsN; j++) {
            items.add(buf.readString());
        }

        int tagsN = buf.readVarInt();
        ArrayList<TagFilterSpec> tags = new ArrayList<>(Math.max(0, tagsN));
        for (int t = 0; t < tagsN; t++) {
            String tagId = buf.readString();
            int excN = buf.readVarInt();
            ArrayList<String> exc = new ArrayList<>(Math.max(0, excN));
            for (int e = 0; e < excN; e++) {
                exc.add(buf.readString());
            }
            tags.add(new TagFilterSpec(tagId, exc));
        }

        return new ContainerFilterSpec(items, tags, List.of()).normalized();
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
