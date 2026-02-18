package dev.dromer.chestsort.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ContainerFilterSpec(List<String> items, List<TagFilterSpec> tags, List<String> presets, boolean autosort) {
    public static final Codec<ContainerFilterSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(ContainerFilterSpec::items),
        TagFilterSpec.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(ContainerFilterSpec::tags),
        Codec.STRING.listOf().optionalFieldOf("presets", List.of()).forGetter(ContainerFilterSpec::presets),
        Codec.BOOL.optionalFieldOf("autosort", false).forGetter(ContainerFilterSpec::autosort)
    ).apply(instance, ContainerFilterSpec::new));

    public ContainerFilterSpec(List<String> items, List<TagFilterSpec> tags, List<String> presets) {
        this(items, tags, presets, false);
    }

    public ContainerFilterSpec {
        if (items == null) items = List.of();
        if (tags == null) tags = List.of();
        if (presets == null) presets = List.of();
    }

    public static ContainerFilterSpec fromLegacyItems(List<String> items) {
        return new ContainerFilterSpec(items == null ? List.of() : items, List.of(), List.of());
    }

    public boolean isEmpty() {
        return (items == null || items.isEmpty())
            && (tags == null || tags.isEmpty())
            && (presets == null || presets.isEmpty());
    }

    public ContainerFilterSpec normalized() {
        List<String> normalizedItems = normalizeStrings(items);
        List<String> normalizedPresets = normalizeStrings(presets);

        // Merge duplicate tag ids (keep order), union exceptions.
        Map<String, LinkedHashSet<String>> byTag = new LinkedHashMap<>();
        if (tags != null) {
            for (TagFilterSpec tag : tags) {
                if (tag == null) continue;
                String tagId = normalizeTagId(tag.tagId());
                if (tagId.isEmpty()) continue;
                LinkedHashSet<String> exc = byTag.computeIfAbsent(tagId, k -> new LinkedHashSet<>());
                exc.addAll(normalizeStrings(tag.exceptions()));
            }
        }

        List<TagFilterSpec> normalizedTags = new ArrayList<>(byTag.size());
        for (var e : byTag.entrySet()) {
            normalizedTags.add(new TagFilterSpec(e.getKey(), List.copyOf(e.getValue())));
        }

        return new ContainerFilterSpec(normalizedItems, normalizedTags, normalizedPresets, autosort);
    }

    public static List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : values) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        return List.copyOf(out);
    }

    public static String normalizeTagId(String tagId) {
        if (tagId == null) return "";
        String t = tagId.trim();
        if (t.isEmpty()) return "";
        if (t.charAt(0) != '#') t = "#" + t;
        return t;
    }
}
