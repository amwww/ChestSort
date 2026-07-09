package dev.dromer.chestsort.net.payload;

import dev.dromer.chestsort.Chestsort;
import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ContainerContextV3Payload(
    String dimensionId,
    long posLong,
    String containerType,
    ContainerFilterSpec whitelist,
    ContainerFilterSpec blacklist,
    boolean whitelistPriority
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ContainerContextV3Payload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Chestsort.MOD_ID, "container_context_v3"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerContextV3Payload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.dimensionId == null ? "" : payload.dimensionId);
            buf.writeLong(payload.posLong);
            buf.writeUtf(payload.containerType == null ? "" : payload.containerType);

            writeSpec(buf, payload.whitelist);
            writeSpec(buf, payload.blacklist);

            buf.writeBoolean(payload.whitelistPriority);
        },
        buf -> {
            String dim = buf.readUtf();
            long pos = buf.readLong();
            String type = buf.readUtf();

            ContainerFilterSpec whitelist = readSpec(buf);
            ContainerFilterSpec blacklist = readSpec(buf);

            boolean whitelistPriority = buf.readBoolean();

            return new ContainerContextV3Payload(dim, pos, type, whitelist, blacklist, whitelistPriority);
        }
    );

    private static void writeSpec(RegistryFriendlyByteBuf buf, ContainerFilterSpec specRaw) {
        ContainerFilterSpec spec = specRaw == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : specRaw.normalized();
        List<String> items = spec.items() == null ? List.of() : spec.items();
        List<TagFilterSpec> tags = spec.tags() == null ? List.of() : spec.tags();
        List<String> presets = spec.presets() == null ? List.of() : spec.presets();

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

        buf.writeVarInt(presets.size());
        for (String name : presets) {
            buf.writeUtf(name == null ? "" : name);
        }

        buf.writeBoolean(spec.autosort());
    }

    private static ContainerFilterSpec readSpec(RegistryFriendlyByteBuf buf) {
        int nItems = buf.readVarInt();
        List<String> items = new ArrayList<>(Math.max(0, nItems));
        for (int i = 0; i < nItems; i++) {
            items.add(buf.readUtf());
        }

        int nTags = buf.readVarInt();
        List<TagFilterSpec> tags = new ArrayList<>(Math.max(0, nTags));
        for (int i = 0; i < nTags; i++) {
            String tagId = buf.readUtf();
            int nExc = buf.readVarInt();
            List<String> exceptions = new ArrayList<>(Math.max(0, nExc));
            for (int j = 0; j < nExc; j++) {
                exceptions.add(buf.readUtf());
            }
            tags.add(new TagFilterSpec(tagId, exceptions));
        }

        int nPresets = buf.readVarInt();
        List<String> presets = new ArrayList<>(Math.max(0, nPresets));
        for (int i = 0; i < nPresets; i++) {
            presets.add(buf.readUtf());
        }

        boolean autosort = buf.readBoolean();

        return new ContainerFilterSpec(items, tags, presets, autosort).normalized();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
