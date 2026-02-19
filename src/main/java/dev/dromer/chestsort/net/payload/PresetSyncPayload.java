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

/** Server -> Client: full preset list sync. */
public record PresetSyncPayload(List<String> names, List<ContainerFilterSpec> specs) implements CustomPayload {
    public static final Id<PresetSyncPayload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "preset_sync"));

    public static final PacketCodec<RegistryByteBuf, PresetSyncPayload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            List<String> names = payload.names == null ? List.of() : payload.names;
            List<ContainerFilterSpec> specs = payload.specs == null ? List.of() : payload.specs;
            int n = Math.min(names.size(), specs.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                String name = names.get(i);
                buf.writeString(name == null ? "" : name);

                ContainerFilterSpec spec = specs.get(i);
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
        },
        buf -> {
            int n = buf.readVarInt();
            ArrayList<String> names = new ArrayList<>(Math.max(0, n));
            ArrayList<ContainerFilterSpec> specs = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
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
            return new PresetSyncPayload(names, specs);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
