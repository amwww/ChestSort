package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> Server: import multiple presets (name + spec pairs) in one request. */
public record ImportPresetListPayload(List<String> names, List<ContainerFilterSpec> specs) implements CustomPayload {
    public static final Id<ImportPresetListPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "import_preset_list"));

    public static final PacketCodec<RegistryByteBuf, ImportPresetListPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            List<String> n = payload.names == null ? List.of() : payload.names;
            List<ContainerFilterSpec> s = payload.specs == null ? List.of() : payload.specs;

            int count = Math.min(n.size(), s.size());
            buf.writeVarInt(count);

            for (int i = 0; i < count; i++) {
                buf.writeString(n.get(i) == null ? "" : n.get(i));

                ContainerFilterSpec safe = s.get(i) == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : s.get(i);
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
        },
        buf -> {
            int count = buf.readVarInt();
            ArrayList<String> names = new ArrayList<>(Math.max(0, count));
            ArrayList<ContainerFilterSpec> specs = new ArrayList<>(Math.max(0, count));

            for (int i = 0; i < count; i++) {
                String name = buf.readString();

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

                names.add(name);
                specs.add(new ContainerFilterSpec(items, tags, List.of()).normalized());
            }

            return new ImportPresetListPayload(names, specs);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
