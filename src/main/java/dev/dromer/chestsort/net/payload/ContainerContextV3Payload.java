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

public record ContainerContextV3Payload(
    String dimensionId,
    long posLong,
    String containerType,
    ContainerFilterSpec whitelist,
    ContainerFilterSpec blacklist,
    boolean whitelistPriority
) implements CustomPayload {
    public static final Id<ContainerContextV3Payload> ID = new Id<>(Identifier.of(Chestsort.MOD_ID, "container_context_v3"));

    public static final PacketCodec<RegistryByteBuf, ContainerContextV3Payload> CODEC = PacketCodec.ofStatic(
        (buf, payload) -> {
            buf.writeString(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeString(payload.containerType == null ? "" : payload.containerType);

            writeSpec(buf, payload.whitelist);
            writeSpec(buf, payload.blacklist);

            buf.writeBoolean(payload.whitelistPriority);
        },
        buf -> {
            String dim = buf.readString();
            long pos = buf.readLong();
            String type = buf.readString();

            ContainerFilterSpec whitelist = readSpec(buf);
            ContainerFilterSpec blacklist = readSpec(buf);

            boolean whitelistPriority = buf.readBoolean();

            return new ContainerContextV3Payload(dim, pos, type, whitelist, blacklist, whitelistPriority);
        }
    );

    private static void writeSpec(RegistryByteBuf buf, ContainerFilterSpec specRaw) {
        ContainerFilterSpec spec = specRaw == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : specRaw.normalized();
        List<String> items = spec.items() == null ? List.of() : spec.items();
        List<TagFilterSpec> tags = spec.tags() == null ? List.of() : spec.tags();
        List<String> presets = spec.presets() == null ? List.of() : spec.presets();

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

        buf.writeVarInt(presets.size());
        for (String name : presets) {
            buf.writeString(name == null ? "" : name);
        }

        buf.writeBoolean(spec.autosort());
    }

    private static ContainerFilterSpec readSpec(RegistryByteBuf buf) {
        int nItems = buf.readVarInt();
        List<String> items = new ArrayList<>(Math.max(0, nItems));
        for (int i = 0; i < nItems; i++) {
            items.add(buf.readString());
        }

        int nTags = buf.readVarInt();
        List<TagFilterSpec> tags = new ArrayList<>(Math.max(0, nTags));
        for (int i = 0; i < nTags; i++) {
            String tagId = buf.readString();
            int nExc = buf.readVarInt();
            List<String> exceptions = new ArrayList<>(Math.max(0, nExc));
            for (int j = 0; j < nExc; j++) {
                exceptions.add(buf.readString());
            }
            tags.add(new TagFilterSpec(tagId, exceptions));
        }

        int nPresets = buf.readVarInt();
        List<String> presets = new ArrayList<>(Math.max(0, nPresets));
        for (int i = 0; i < nPresets; i++) {
            presets.add(buf.readString());
        }

        boolean autosort = buf.readBoolean();

        return new ContainerFilterSpec(items, tags, presets, autosort).normalized();
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
