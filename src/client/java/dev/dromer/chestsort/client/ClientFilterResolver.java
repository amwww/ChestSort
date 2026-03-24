package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.filter.TagFilterSpec;

public final class ClientFilterResolver {
    private ClientFilterResolver() {
    }

    /**
     * Client-side equivalent of ChestSortState#resolveWithAppliedPresets.
     * Applied presets contribute only their items/tags (not their own applied preset lists).
     */
    public static ContainerFilterSpec resolveWithAppliedPresets(ContainerFilterSpec base) {
        if (base == null) return new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        ContainerFilterSpec normalizedBase = base.normalized();
        List<String> applied = normalizedBase.presets();
        if (applied == null || applied.isEmpty()) return normalizedBase;

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>(normalizedBase.items() == null ? List.of() : normalizedBase.items());
        ArrayList<TagFilterSpec> mergedTags = new ArrayList<>(normalizedBase.tags() == null ? List.of() : normalizedBase.tags());

        for (String presetName : applied) {
            if (presetName == null) continue;
            ContainerFilterSpec preset = ClientPresetRegistry.get(presetName);
            if (preset == null) continue;
            ContainerFilterSpec p = preset.normalized();
            if (p.items() != null) mergedItems.addAll(p.items());
            if (p.tags() != null) mergedTags.addAll(p.tags());
        }

        return new ContainerFilterSpec(List.copyOf(mergedItems), mergedTags, List.of(), normalizedBase.autosort()).normalized();
    }

    /** Like resolveWithAppliedPresets, but uses the blacklist side of presets. */
    public static ContainerFilterSpec resolveBlacklistWithAppliedPresets(ContainerFilterSpec base) {
        if (base == null) return new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        ContainerFilterSpec normalizedBase = base.normalized();
        List<String> applied = normalizedBase.presets();
        if (applied == null || applied.isEmpty()) return normalizedBase;

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>(normalizedBase.items() == null ? List.of() : normalizedBase.items());
        ArrayList<TagFilterSpec> mergedTags = new ArrayList<>(normalizedBase.tags() == null ? List.of() : normalizedBase.tags());

        for (String presetName : applied) {
            if (presetName == null) continue;
            ContainerFilterSpec preset = ClientPresetRegistry.getBlacklist(presetName);
            if (preset == null) continue;
            ContainerFilterSpec p = preset.normalized();
            if (p.items() != null) mergedItems.addAll(p.items());
            if (p.tags() != null) mergedTags.addAll(p.tags());
        }

        return new ContainerFilterSpec(List.copyOf(mergedItems), mergedTags, List.of(), false).normalized();
    }
}
