package dev.dromer.chestsort.net.payload;

import java.util.ArrayList;
import java.util.List;

import dev.dromer.chestsort.Chestsort;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> Server: import multiple presets (name + spec pairs) in one request. */
public record ImportPresetListPayload(List<String> names, List<ContainerFilterSpec> specs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ImportPresetListPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "import_preset_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ImportPresetListPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            List<String> n = payload.names == null ? List.of() : payload.names;
            List<ContainerFilterSpec> s = payload.specs == null ? List.of() : payload.specs;

            int count = Math.min(n.size(), s.size());
            buf.writeVarInt(count);

            for (int i = 0; i < count; i++) {
                buf.writeUtf(n.get(i) == null ? "" : n.get(i));

                ContainerFilterSpec safe = s.get(i) == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : s.get(i);
                List<String> items = safe.items() == null ? List.of() : safe.items();
                List<TagFilterSpec> tags = safe.tags() == null ? List.of() : safe.tags();

                buf.writeVarInt(items.size());
                for (String itemId : items) {
                    buf.writeUtf(itemId == null ? "" : itemId);
                }

                buf.writeVarInt(tags.size());
                for (TagFilterSpec tag : tags) {
                    String tagId = tag == null ? "" : (tag.tagId() == null ? "" : tag.tagId());
                    buf.writeUtf(tagId);
                    List<String> exceptions = (tag == null || tag.exceptions() == null) ? List.of() : tag.exceptions();
                    buf.writeVarInt(exceptions.size());
                    for (String exc : exceptions) {
                        buf.writeUtf(exc == null ? "" : exc);
                    }
                }
            }
        },
        buf -> {
            int count = buf.readVarInt();
            ArrayList<String> names = new ArrayList<>(Math.max(0, count));
            ArrayList<ContainerFilterSpec> specs = new ArrayList<>(Math.max(0, count));

            for (int i = 0; i < count; i++) {
                String name = buf.readUtf();

                int itemsN = buf.readVarInt();
                ArrayList<String> items = new ArrayList<>(Math.max(0, itemsN));
                for (int j = 0; j < itemsN; j++) {
                    items.add(buf.readUtf());
                }

                int tagsN = buf.readVarInt();
                ArrayList<TagFilterSpec> tags = new ArrayList<>(Math.max(0, tagsN));
                for (int t = 0; t < tagsN; t++) {
                    String tagId = buf.readUtf();
                    int excN = buf.readVarInt();
                    ArrayList<String> exc = new ArrayList<>(Math.max(0, excN));
                    for (int e = 0; e < excN; e++) {
                        exc.add(buf.readUtf());
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
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
