package dev.dromer.chestsort.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import dev.dromer.chestsort.util.ContainerCanonicalizer;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.chunk.WorldChunk;

public class ChestSortState extends PersistentState {
    private static final String STATE_ID = "chestsort";

    public static final String AUTOSORT_NEVER = "never";
    public static final String AUTOSORT_SELECTED = "selected";
    public static final String AUTOSORT_ALWAYS = "always";

    public static final String HIGHLIGHTS_ON = "on";
    public static final String HIGHLIGHTS_OFF = "off";
    public static final String HIGHLIGHTS_UNTIL_OPENED = "until_opened";

    private record ContainerBlacklistBundle(
        Map<String, ContainerFilterSpec> blacklistSpecs,
        Map<String, Boolean> whitelistPriorityByContainer
    ) {
        static final MapCodec<ContainerBlacklistBundle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC)
                .optionalFieldOf("blacklistSpecs", Map.of())
                .forGetter(ContainerBlacklistBundle::blacklistSpecs),
            Codec.unboundedMap(Codec.STRING, Codec.BOOL)
                .optionalFieldOf("whitelistPriorityByContainer", Map.of())
                .forGetter(ContainerBlacklistBundle::whitelistPriorityByContainer)
        ).apply(instance, ContainerBlacklistBundle::new));
    }

    private record PresetBundle(
        Map<String, ContainerFilterSpec> presets,
        Map<String, ContainerFilterSpec> presetBlacklists
    ) {
        static final MapCodec<PresetBundle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC)
                .optionalFieldOf("presets", Map.of())
                .forGetter(PresetBundle::presets),
            Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC)
                .optionalFieldOf("presetBlacklist", Map.of())
                .forGetter(PresetBundle::presetBlacklists)
        ).apply(instance, PresetBundle::new));
    }

    public static final Codec<ChestSortState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.optionalFieldOf("lastFullScanMs", 0L).forGetter(s -> s.lastFullScanMs),
        ContainerSnapshot.CODEC.listOf().optionalFieldOf("snapshots", List.of()).forGetter(s -> new ArrayList<>(s.snapshotsByKey.values())),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("lastFind", Map.of()).forGetter(s -> s.lastFindItemByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("autosortModeByPlayer", Map.of()).forGetter(s -> s.autosortModeByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("highlightModeByPlayer", Map.of()).forGetter(s -> s.highlightModeByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("lockedSlotsByPlayer", Map.of()).forGetter(s -> s.lockedSlotsByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("itemBlacklistByPlayer", Map.of()).forGetter(s -> s.itemBlacklistByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.BOOL).optionalFieldOf("clearHighlightsOnNextOpenByPlayer", Map.of()).forGetter(s -> s.clearHighlightsOnNextOpenByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("wandItemByPlayer", Map.of()).forGetter(s -> s.wandItemIdByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("wandPos1ByPlayer", Map.of()).forGetter(s -> s.wandPos1KeyByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("wandPos2ByPlayer", Map.of()).forGetter(s -> s.wandPos2KeyByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC).optionalFieldOf("wandClipboardByPlayer", Map.of()).forGetter(s -> s.wandClipboardByPlayerUuid),
        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("filters", Map.of()).forGetter(s -> s.legacyFilterItemsByKey),
        Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC).optionalFieldOf("filterSpecs", Map.of()).forGetter(s -> s.filterSpecByKey),
        ContainerBlacklistBundle.CODEC.forGetter(s -> new ContainerBlacklistBundle(s.blacklistSpecByKey, s.whitelistPriorityByKey)),
        PresetBundle.CODEC.forGetter(s -> new PresetBundle(s.presetsByName, s.presetBlacklistByName))
    ).apply(instance, (lastFullScanMs, snapshots, lastFind, autosortModes, highlightModes, lockedSlotsByPlayer, itemBlacklistByPlayer, clearHighlightsOnNextOpen, wandItemByPlayer, wandPos1ByPlayer, wandPos2ByPlayer, wandClipboardByPlayer, legacyFilters, filterSpecs, blacklistBundle, presetBundle) ->
        new ChestSortState(
            lastFullScanMs,
            snapshots,
            lastFind,
            autosortModes,
            highlightModes,
            lockedSlotsByPlayer,
            itemBlacklistByPlayer,
            clearHighlightsOnNextOpen,
            wandItemByPlayer,
            wandPos1ByPlayer,
            wandPos2ByPlayer,
            wandClipboardByPlayer,
            legacyFilters,
            filterSpecs,
            blacklistBundle.blacklistSpecs(),
            blacklistBundle.whitelistPriorityByContainer(),
            presetBundle.presets(),
            presetBundle.presetBlacklists()
        )
    ));

    public static final PersistentStateType<ChestSortState> TYPE = new PersistentStateType<>(
        STATE_ID,
        ChestSortState::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, ContainerSnapshot> snapshotsByKey = new HashMap<>();
    private final Map<String, String> lastFindItemByPlayerUuid = new HashMap<>();
    private final Map<String, String> autosortModeByPlayerUuid = new HashMap<>();
    private final Map<String, String> highlightModeByPlayerUuid = new HashMap<>();
    private final Map<String, List<Integer>> lockedSlotsByPlayerUuid = new HashMap<>();
    private final Map<String, List<String>> itemBlacklistByPlayerUuid = new HashMap<>();
    private final Map<String, Boolean> clearHighlightsOnNextOpenByPlayerUuid = new HashMap<>();

    // Wand feature: per-player bound item id and selection positions.
    private final Map<String, String> wandItemIdByPlayerUuid = new HashMap<>();
    private final Map<String, String> wandPos1KeyByPlayerUuid = new HashMap<>();
    private final Map<String, String> wandPos2KeyByPlayerUuid = new HashMap<>();
    private final Map<String, ContainerFilterSpec> wandClipboardByPlayerUuid = new HashMap<>();
    // Legacy: item-id-only whitelist stored as a list of strings.
    // Kept for save backward-compat; new versions store full specs in filterSpecByKey.
    private final Map<String, List<String>> legacyFilterItemsByKey = new HashMap<>();
    private final Map<String, ContainerFilterSpec> filterSpecByKey = new HashMap<>();
    private final Map<String, ContainerFilterSpec> blacklistSpecByKey = new HashMap<>();
    private final Map<String, Boolean> whitelistPriorityByKey = new HashMap<>();
    private final Map<String, ContainerFilterSpec> presetsByName = new HashMap<>();
    private final Map<String, ContainerFilterSpec> presetBlacklistByName = new HashMap<>();
    private long lastFullScanMs = 0L;

    // Transient (non-persisted) state: last sort undo transaction per player.
    private final transient Map<String, SortUndoTransaction> lastSortUndoByPlayerUuid = new HashMap<>();
    private final transient AtomicInteger undoCounter = new AtomicInteger(0);

    public record SortUndoTransaction(
        String dimensionId,
        long posLong,
        long undoId,
        int movedTotal,
        java.util.List<dev.dromer.chestsort.net.payload.SortResultPayload.SortLine> lines,
        java.util.List<ItemStack> playerBefore,
        java.util.List<ItemStack> containerBefore,
        java.util.List<ItemStack> playerAfter,
        java.util.List<ItemStack> containerAfter
    ) {
    }

    public static ChestSortState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager mgr = overworld.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }

    public ChestSortState() {
    }

    private ChestSortState(long lastFullScanMs, List<ContainerSnapshot> snapshots, Map<String, String> lastFind, Map<String, String> autosortModes, Map<String, String> highlightModes, Map<String, List<Integer>> lockedSlotsByPlayer, Map<String, List<String>> itemBlacklistByPlayer, Map<String, Boolean> clearHighlightsOnNextOpen, Map<String, String> wandItemByPlayer, Map<String, String> wandPos1ByPlayer, Map<String, String> wandPos2ByPlayer, Map<String, ContainerFilterSpec> wandClipboardByPlayer, Map<String, List<String>> legacyFilters, Map<String, ContainerFilterSpec> filterSpecs, Map<String, ContainerFilterSpec> blacklistSpecs, Map<String, Boolean> whitelistPriorityByContainer, Map<String, ContainerFilterSpec> presets, Map<String, ContainerFilterSpec> presetBlacklists) {
        this.lastFullScanMs = lastFullScanMs;
        for (ContainerSnapshot snapshot : snapshots) {
            this.snapshotsByKey.put(key(snapshot.dimensionId(), snapshot.posLong()), snapshot);
        }
        this.lastFindItemByPlayerUuid.putAll(lastFind);

        if (autosortModes != null) {
            this.autosortModeByPlayerUuid.putAll(autosortModes);
        }

        if (highlightModes != null) {
            this.highlightModeByPlayerUuid.putAll(highlightModes);
        }

        if (lockedSlotsByPlayer != null) {
            this.lockedSlotsByPlayerUuid.putAll(lockedSlotsByPlayer);
        }

        if (itemBlacklistByPlayer != null) {
            this.itemBlacklistByPlayerUuid.putAll(itemBlacklistByPlayer);
        }

        if (clearHighlightsOnNextOpen != null) {
            this.clearHighlightsOnNextOpenByPlayerUuid.putAll(clearHighlightsOnNextOpen);
        }

        if (wandItemByPlayer != null) {
            this.wandItemIdByPlayerUuid.putAll(wandItemByPlayer);
        }

        if (wandPos1ByPlayer != null) {
            this.wandPos1KeyByPlayerUuid.putAll(wandPos1ByPlayer);
        }

        if (wandPos2ByPlayer != null) {
            this.wandPos2KeyByPlayerUuid.putAll(wandPos2ByPlayer);
        }

        if (wandClipboardByPlayer != null) {
            this.wandClipboardByPlayerUuid.putAll(wandClipboardByPlayer);
        }

        if (legacyFilters != null) {
            this.legacyFilterItemsByKey.putAll(legacyFilters);
        }

        if (filterSpecs != null) {
            this.filterSpecByKey.putAll(filterSpecs);
        }

        if (blacklistSpecs != null) {
            this.blacklistSpecByKey.putAll(blacklistSpecs);
        }

        if (whitelistPriorityByContainer != null) {
            this.whitelistPriorityByKey.putAll(whitelistPriorityByContainer);
        }

        if (presets != null) {
            this.presetsByName.putAll(presets);
        }

        if (presetBlacklists != null) {
            this.presetBlacklistByName.putAll(presetBlacklists);
        }

        // If the world only has legacy filters, lift them into filter specs.
        // (We keep legacyFilterItemsByKey so older versions can still read the save.)
        if (this.filterSpecByKey.isEmpty() && !this.legacyFilterItemsByKey.isEmpty()) {
            for (var e : this.legacyFilterItemsByKey.entrySet()) {
                this.filterSpecByKey.put(e.getKey(), ContainerFilterSpec.fromLegacyItems(e.getValue()).normalized());
            }
        }
    }

    public List<Integer> getLockedSlots(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return List.of();
        List<Integer> raw = lockedSlotsByPlayerUuid.get(playerUuid);
        if (raw == null || raw.isEmpty()) return List.of();

        java.util.LinkedHashSet<Integer> uniq = new java.util.LinkedHashSet<>();
        for (Integer i : raw) {
            if (i == null) continue;
            if (i < 0) continue;
            uniq.add(i);
        }
        if (uniq.isEmpty()) return List.of();
        return List.copyOf(uniq);
    }

    public boolean isSlotLocked(String playerUuid, int playerInventoryIndex) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        if (playerInventoryIndex < 0) return false;
        List<Integer> raw = lockedSlotsByPlayerUuid.get(playerUuid);
        if (raw == null || raw.isEmpty()) return false;
        for (Integer i : raw) {
            if (i != null && i == playerInventoryIndex) return true;
        }
        return false;
    }

    /**
     * Toggles whether a player inventory slot index is protected.
     *
     * @return true if the slot is now locked; false if now unlocked.
     */
    public boolean toggleLockedSlot(String playerUuid, int playerInventoryIndex) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        if (playerInventoryIndex < 0) return false;

        ArrayList<Integer> list = new ArrayList<>(lockedSlotsByPlayerUuid.getOrDefault(playerUuid, List.of()));

        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            Integer v = list.get(i);
            if (v != null && v == playerInventoryIndex) {
                list.remove(i);
                removed = true;
                break;
            }
        }

        boolean nowLocked;
        if (removed) {
            nowLocked = false;
        } else {
            list.add(playerInventoryIndex);
            nowLocked = true;
        }

        if (list.isEmpty()) {
            lockedSlotsByPlayerUuid.remove(playerUuid);
        } else {
            lockedSlotsByPlayerUuid.put(playerUuid, List.copyOf(list));
        }

        markDirty();
        return nowLocked;
    }

    public List<String> getItemBlacklist(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return List.of();
        List<String> raw = itemBlacklistByPlayerUuid.get(playerUuid);
        if (raw == null || raw.isEmpty()) return List.of();
        return ContainerFilterSpec.normalizeStrings(raw);
    }

    public boolean isItemBlacklisted(String playerUuid, String itemId) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        if (itemId == null) return false;
        String t = itemId.trim();
        if (t.isEmpty()) return false;

        List<String> raw = itemBlacklistByPlayerUuid.get(playerUuid);
        if (raw == null || raw.isEmpty()) return false;
        for (String s : raw) {
            if (s == null) continue;
            if (t.equals(s.trim())) return true;
        }
        return false;
    }

    public boolean addItemToBlacklist(String playerUuid, String itemId) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        if (itemId == null) return false;
        String t = itemId.trim();
        if (t.isEmpty()) return false;
        Identifier id = Identifier.tryParse(t);
        if (id == null) return false;

        ArrayList<String> list = new ArrayList<>(itemBlacklistByPlayerUuid.getOrDefault(playerUuid, List.of()));
        for (String s : list) {
            if (s != null && t.equals(s.trim())) return false;
        }
        list.add(t);

        itemBlacklistByPlayerUuid.put(playerUuid, List.copyOf(ContainerFilterSpec.normalizeStrings(list)));
        markDirty();
        return true;
    }

    /** Adds multiple item ids to a player's blacklist. Returns how many new ids were added. */
    public int addItemsToBlacklist(String playerUuid, java.util.Collection<String> itemIds) {
        if (playerUuid == null || playerUuid.isEmpty()) return 0;
        if (itemIds == null || itemIds.isEmpty()) return 0;

        LinkedHashSet<String> merged = new LinkedHashSet<>(getItemBlacklist(playerUuid));
        int before = merged.size();

        for (String itemId : itemIds) {
            if (itemId == null) continue;
            String t = itemId.trim();
            if (t.isEmpty()) continue;
            Identifier id = Identifier.tryParse(t);
            if (id == null) continue;
            merged.add(t);
        }

        int added = merged.size() - before;
        if (added <= 0) return 0;

        itemBlacklistByPlayerUuid.put(playerUuid, List.copyOf(merged));
        markDirty();
        return added;
    }

    public boolean removeItemFromBlacklist(String playerUuid, String itemId) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        if (itemId == null) return false;
        String t = itemId.trim();
        if (t.isEmpty()) return false;

        ArrayList<String> list = new ArrayList<>(itemBlacklistByPlayerUuid.getOrDefault(playerUuid, List.of()));
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            if (s != null && t.equals(s.trim())) {
                list.remove(i);
                removed = true;
                break;
            }
        }

        if (!removed) return false;

        List<String> norm = ContainerFilterSpec.normalizeStrings(list);
        if (norm.isEmpty()) {
            itemBlacklistByPlayerUuid.remove(playerUuid);
        } else {
            itemBlacklistByPlayerUuid.put(playerUuid, norm);
        }
        markDirty();
        return true;
    }

    public int clearItemBlacklist(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return 0;
        List<String> prev = itemBlacklistByPlayerUuid.remove(playerUuid);
        int removed = prev == null ? 0 : prev.size();
        if (removed > 0) markDirty();
        return removed;
    }

    public ContainerFilterSpec getFilterSpec(String dimId, long posLong) {
        ContainerFilterSpec spec = filterSpecByKey.get(key(dimId, posLong));
        if (spec != null) return spec;
        // Fallback for saves that only have legacy filters.
        List<String> legacy = legacyFilterItemsByKey.getOrDefault(key(dimId, posLong), List.of());
        return ContainerFilterSpec.fromLegacyItems(legacy).normalized();
    }

    public ContainerFilterSpec getBlacklistSpec(String dimId, long posLong) {
        ContainerFilterSpec spec = blacklistSpecByKey.get(key(dimId, posLong));
        return (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : spec).normalized();
    }

    public boolean whitelistPriority(String dimId, long posLong) {
        Boolean v = whitelistPriorityByKey.get(key(dimId, posLong));
        return v == null ? true : v;
    }

    /**
     * Builds an effective filter spec by unioning this container's items/tags with all applied presets.
     * Preset specs contribute only items/tags (not their own applied preset lists).
     */
    public ContainerFilterSpec resolveWithAppliedPresets(ContainerFilterSpec base) {
        if (base == null) return new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        ContainerFilterSpec normalizedBase = base.normalized();
        List<String> applied = normalizedBase.presets();
        if (applied == null || applied.isEmpty()) return normalizedBase;

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>(normalizedBase.items() == null ? List.of() : normalizedBase.items());
        ArrayList<dev.dromer.chestsort.filter.TagFilterSpec> mergedTags = new ArrayList<>(normalizedBase.tags() == null ? List.of() : normalizedBase.tags());

        for (String presetName : applied) {
            if (presetName == null) continue;
            ContainerFilterSpec preset = getPreset(presetName);
            if (preset == null) continue;
            ContainerFilterSpec p = preset.normalized();
            if (p.items() != null) mergedItems.addAll(p.items());
            if (p.tags() != null) mergedTags.addAll(p.tags());
        }

        return new ContainerFilterSpec(List.copyOf(mergedItems), mergedTags, List.of(), normalizedBase.autosort()).normalized();
    }

    /** Like {@link #resolveWithAppliedPresets(ContainerFilterSpec)}, but applies the blacklist side of presets. */
    public ContainerFilterSpec resolveBlacklistWithAppliedPresets(ContainerFilterSpec base) {
        if (base == null) return new ContainerFilterSpec(List.of(), List.of(), List.of(), false);
        ContainerFilterSpec normalizedBase = base.normalized();
        List<String> applied = normalizedBase.presets();
        if (applied == null || applied.isEmpty()) return normalizedBase;

        LinkedHashSet<String> mergedItems = new LinkedHashSet<>(normalizedBase.items() == null ? List.of() : normalizedBase.items());
        ArrayList<dev.dromer.chestsort.filter.TagFilterSpec> mergedTags = new ArrayList<>(normalizedBase.tags() == null ? List.of() : normalizedBase.tags());

        for (String presetName : applied) {
            if (presetName == null) continue;
            ContainerFilterSpec preset = getPresetBlacklist(presetName);
            if (preset == null) continue;
            ContainerFilterSpec p = preset.normalized();
            if (p.items() != null) mergedItems.addAll(p.items());
            if (p.tags() != null) mergedTags.addAll(p.tags());
        }

        return new ContainerFilterSpec(List.copyOf(mergedItems), mergedTags, List.of(), normalizedBase.autosort()).normalized();
    }

    public void setFilterSpec(String dimId, long posLong, ContainerFilterSpec spec) {
        String k = key(dimId, posLong);
        ContainerFilterSpec normalized = (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : spec).normalized();
        boolean noRules = normalized.isEmpty();
        if (noRules && !normalized.autosort()) {
            filterSpecByKey.remove(k);
            legacyFilterItemsByKey.remove(k);
        } else {
            filterSpecByKey.put(k, normalized);
            // Also mirror items into legacy list for backward compatibility.
            legacyFilterItemsByKey.put(k, normalized.items());
        }
        markDirty();
    }

    public void setBlacklistSpec(String dimId, long posLong, ContainerFilterSpec spec) {
        String k = key(dimId, posLong);
        ContainerFilterSpec normalized = (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : spec).normalized();
        if (normalized.isEmpty()) {
            blacklistSpecByKey.remove(k);
        } else {
            // Autosort is a per-container setting stored on the whitelist spec; blacklist ignores it.
            blacklistSpecByKey.put(k, new ContainerFilterSpec(normalized.items(), normalized.tags(), normalized.presets(), false).normalized());
        }
        markDirty();
    }

    public void setWhitelistPriority(String dimId, long posLong, boolean whitelistPriority) {
        String k = key(dimId, posLong);
        if (whitelistPriority) {
            // default; avoid writing save noise
            whitelistPriorityByKey.remove(k);
        } else {
            whitelistPriorityByKey.put(k, false);
        }
        markDirty();
    }

    public List<String> getFilter(String dimId, long posLong) {
        return getFilterSpec(dimId, posLong).items();
    }

    public void setFilter(String dimId, long posLong, List<String> filterItems) {
        // Legacy setter: only updates item whitelist, preserving any existing tag filters.
        ContainerFilterSpec existing = getFilterSpec(dimId, posLong);
        ContainerFilterSpec updated = new ContainerFilterSpec(filterItems == null ? List.of() : filterItems, existing.tags(), existing.presets(), existing.autosort()).normalized();
        setFilterSpec(dimId, posLong, updated);
    }

    public String getAutosortMode(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return AUTOSORT_SELECTED;
        String v = autosortModeByPlayerUuid.getOrDefault(playerUuid, AUTOSORT_SELECTED);
        if (AUTOSORT_NEVER.equals(v) || AUTOSORT_SELECTED.equals(v) || AUTOSORT_ALWAYS.equals(v)) return v;
        return AUTOSORT_SELECTED;
    }

    public long nextUndoId(String playerUuid) {
        // Undo ids are only used transiently; uniqueness just needs to be good enough per run.
        int c = undoCounter.incrementAndGet();
        long t = System.currentTimeMillis();
        return (t << 20) ^ (c & 0xFFFFF);
    }

    public void setLastSortUndo(String playerUuid, SortUndoTransaction tx) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        if (tx == null) {
            lastSortUndoByPlayerUuid.remove(playerUuid);
            return;
        }
        lastSortUndoByPlayerUuid.put(playerUuid, tx);
    }

    public SortUndoTransaction getLastSortUndo(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        return lastSortUndoByPlayerUuid.get(playerUuid);
    }

    public void clearLastSortUndo(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        lastSortUndoByPlayerUuid.remove(playerUuid);
    }

    public void setAutosortMode(String playerUuid, String mode) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        String m = (mode == null ? "" : mode.trim().toLowerCase());
        if (!AUTOSORT_NEVER.equals(m) && !AUTOSORT_SELECTED.equals(m) && !AUTOSORT_ALWAYS.equals(m)) {
            m = AUTOSORT_SELECTED;
        }
        autosortModeByPlayerUuid.put(playerUuid, m);
        markDirty();
    }

    public String getHighlightsMode(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return HIGHLIGHTS_ON;
        String v = highlightModeByPlayerUuid.getOrDefault(playerUuid, HIGHLIGHTS_ON);
        if (HIGHLIGHTS_ON.equals(v) || HIGHLIGHTS_OFF.equals(v) || HIGHLIGHTS_UNTIL_OPENED.equals(v)) return v;
        return HIGHLIGHTS_ON;
    }

    public void setHighlightsMode(String playerUuid, String mode) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        String m = (mode == null ? "" : mode.trim().toLowerCase());
        if (!HIGHLIGHTS_ON.equals(m) && !HIGHLIGHTS_OFF.equals(m) && !HIGHLIGHTS_UNTIL_OPENED.equals(m)) {
            m = HIGHLIGHTS_ON;
        }
        highlightModeByPlayerUuid.put(playerUuid, m);
        if (HIGHLIGHTS_OFF.equals(m)) {
            clearHighlightsOnNextOpenByPlayerUuid.remove(playerUuid);
        }
        markDirty();
    }

    public void armClearHighlightsOnNextOpen(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        clearHighlightsOnNextOpenByPlayerUuid.put(playerUuid, Boolean.TRUE);
        markDirty();
    }

    public boolean shouldClearHighlightsOnNextOpen(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return false;
        return Boolean.TRUE.equals(clearHighlightsOnNextOpenByPlayerUuid.get(playerUuid));
    }

    public void clearHighlightsOnNextOpen(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        clearHighlightsOnNextOpenByPlayerUuid.remove(playerUuid);
        markDirty();
    }

    public record WandPos(String dimensionId, long posLong) {
        public BlockPos pos() {
            return BlockPos.fromLong(posLong);
        }
    }

    public String getWandItemId(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return "";
        return wandItemIdByPlayerUuid.getOrDefault(playerUuid, "");
    }

    public void setWandItemId(String playerUuid, String itemId) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        String v = (itemId == null ? "" : itemId.trim());
        if (v.isEmpty()) {
            wandItemIdByPlayerUuid.remove(playerUuid);
        } else {
            wandItemIdByPlayerUuid.put(playerUuid, v);
        }
        markDirty();
    }

    private static String wandPosKey(String dimId, long posLong) {
        return key(dimId, posLong);
    }

    private static WandPos parseWandPosKey(String key) {
        if (key == null || key.isEmpty()) return null;
        int idx = key.indexOf('|');
        if (idx <= 0 || idx >= key.length() - 1) return null;
        String dimId = key.substring(0, idx);
        try {
            long posLong = Long.parseLong(key.substring(idx + 1));
            return new WandPos(dimId, posLong);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setWandPos1(String playerUuid, String dimId, long posLong) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        if (dimId == null || dimId.isEmpty()) return;
        wandPos1KeyByPlayerUuid.put(playerUuid, wandPosKey(dimId, posLong));
        markDirty();
    }

    public void setWandPos2(String playerUuid, String dimId, long posLong) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        if (dimId == null || dimId.isEmpty()) return;
        wandPos2KeyByPlayerUuid.put(playerUuid, wandPosKey(dimId, posLong));
        markDirty();
    }

    public void clearWandPos1(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        wandPos1KeyByPlayerUuid.remove(playerUuid);
        markDirty();
    }

    public void clearWandPos2(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        wandPos2KeyByPlayerUuid.remove(playerUuid);
        markDirty();
    }

    public void clearWandSelection(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        wandPos1KeyByPlayerUuid.remove(playerUuid);
        wandPos2KeyByPlayerUuid.remove(playerUuid);
        markDirty();
    }

    public void clearWandClipboard(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        wandClipboardByPlayerUuid.remove(playerUuid);
        markDirty();
    }

    public WandPos getWandPos1(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        return parseWandPosKey(wandPos1KeyByPlayerUuid.get(playerUuid));
    }

    public WandPos getWandPos2(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        return parseWandPosKey(wandPos2KeyByPlayerUuid.get(playerUuid));
    }

    public void setWandClipboard(String playerUuid, ContainerFilterSpec spec) {
        if (playerUuid == null || playerUuid.isEmpty()) return;
        if (spec == null) {
            wandClipboardByPlayerUuid.remove(playerUuid);
        } else {
            wandClipboardByPlayerUuid.put(playerUuid, spec.normalized());
        }
        markDirty();
    }

    public ContainerFilterSpec getWandClipboard(String playerUuid) {
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        ContainerFilterSpec s = wandClipboardByPlayerUuid.get(playerUuid);
        return s == null ? null : s.normalized();
    }

    public void setLastFullScanMs(long ms) {
        this.lastFullScanMs = ms;
        markDirty();
    }

    public long getLastFullScanMs() {
        return lastFullScanMs;
    }

    public void setLastFindItem(String playerUuid, String itemId) {
        lastFindItemByPlayerUuid.put(playerUuid, itemId);
        markDirty();
    }

    public String getLastFindItem(String playerUuid) {
        return lastFindItemByPlayerUuid.getOrDefault(playerUuid, "");
    }

    public Map<String, ContainerFilterSpec> getPresets() {
        return Map.copyOf(presetsByName);
    }

    public Map<String, ContainerFilterSpec> getPresetBlacklists() {
        return Map.copyOf(presetBlacklistByName);
    }

    public ContainerFilterSpec getPreset(String name) {
        if (name == null) return null;
        return presetsByName.get(name.trim());
    }

    public ContainerFilterSpec getPresetBlacklist(String name) {
        if (name == null) return null;
        return presetBlacklistByName.get(name.trim());
    }

    public boolean hasPreset(String name) {
        if (name == null) return false;
        return presetsByName.containsKey(name.trim());
    }

    public boolean addPreset(String name, ContainerFilterSpec spec) {
        String n = (name == null ? "" : name.trim());
        if (n.isEmpty()) return false;
        if (presetsByName.containsKey(n)) return false;
        ContainerFilterSpec normalized = (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : spec).normalized();
        // Preset definitions should not themselves carry applied preset lists.
        normalized = new ContainerFilterSpec(normalized.items(), normalized.tags(), List.of(), false).normalized();
        presetsByName.put(n, normalized);
        presetBlacklistByName.putIfAbsent(n, new ContainerFilterSpec(List.of(), List.of(), List.of(), false).normalized());
        markDirty();
        return true;
    }

    public void setPreset(String name, ContainerFilterSpec spec) {
        setPreset(name, spec, null);
    }

    public void setPreset(String name, ContainerFilterSpec whitelistSpec, ContainerFilterSpec blacklistSpec) {
        String n = (name == null ? "" : name.trim());
        if (n.isEmpty()) return;

        ContainerFilterSpec wl = (whitelistSpec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : whitelistSpec).normalized();
        wl = new ContainerFilterSpec(wl.items(), wl.tags(), List.of(), false).normalized();
        presetsByName.put(n, wl);

        if (blacklistSpec != null) {
            ContainerFilterSpec bl = (blacklistSpec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of(), false) : blacklistSpec).normalized();
            bl = new ContainerFilterSpec(bl.items(), bl.tags(), List.of(), false).normalized();
            presetBlacklistByName.put(n, bl);
        } else {
            presetBlacklistByName.putIfAbsent(n, new ContainerFilterSpec(List.of(), List.of(), List.of(), false).normalized());
        }
        markDirty();
    }

    public boolean removePreset(String name) {
        String n = (name == null ? "" : name.trim());
        if (n.isEmpty()) return false;
        ContainerFilterSpec removed = presetsByName.remove(n);
        presetBlacklistByName.remove(n);
        if (removed != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean renamePreset(String oldName, String newName) {
        String a = (oldName == null ? "" : oldName.trim());
        String b = (newName == null ? "" : newName.trim());
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (!presetsByName.containsKey(a)) return false;
        if (presetsByName.containsKey(b)) return false;
        ContainerFilterSpec spec = presetsByName.remove(a);
        presetsByName.put(b, spec);

        ContainerFilterSpec bl = presetBlacklistByName.remove(a);
        if (bl != null) {
            presetBlacklistByName.put(b, bl);
        }
        markDirty();
        return true;
    }

    public int scanLoadedContainers(MinecraftServer server) {
        long nowMs = System.currentTimeMillis();
        AtomicInteger scanned = new AtomicInteger();

        // Dedupe multi-block containers (double chests) by canonical position.
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        for (ServerWorld world : server.getWorlds()) {
            String dimId = world.getRegistryKey().getValue().toString();

            world.getChunkManager().chunkLoadingManager.forEachChunk((WorldChunk chunk) -> {
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity chest) {
                        BlockPos pos = chest.getPos();
                        var canonical = ContainerCanonicalizer.canonicalize(world, pos, chest);
                        String k = key(dimId, canonical.posLong());
                        if (seen.add(k)) {
                            Inventory inv = canonical.snapshotInventory() == null ? chest : canonical.snapshotInventory();
                            putSnapshot(dimId, canonical.posLong(), canonical.containerType(), nowMs, snapshotInventory(inv));
                            scanned.incrementAndGet();
                        }
                    } else if (be instanceof BarrelBlockEntity barrel) {
                        long posLong = barrel.getPos().asLong();
                        String k = key(dimId, posLong);
                        if (seen.add(k)) {
                            putSnapshot(dimId, posLong, "barrel", nowMs, snapshotInventory(barrel));
                            scanned.incrementAndGet();
                        }
                    }
                }
            });
        }

        int totalScanned = scanned.get();
        if (totalScanned > 0) markDirty();
        return totalScanned;
    }

    public void updateFromBlockEntity(ServerWorld world, BlockPosLike posLike, Inventory inv, String containerType) {
        String dimId = world.getRegistryKey().getValue().toString();
        long nowMs = System.currentTimeMillis();
        putSnapshot(dimId, posLike.asLong(), containerType, nowMs, snapshotInventory(inv));
        markDirty();
    }

    public boolean containerContains(ServerWorld world, long posLong, String itemId) {
        String dimId = world.getRegistryKey().getValue().toString();
        ContainerSnapshot snapshot = snapshotsByKey.get(key(dimId, posLong));
        return snapshot != null && snapshot.containsItem(itemId);
    }

    public List<ContainerSnapshot.Match> find(String itemId) {
        List<ContainerSnapshot.Match> matches = new ArrayList<>();
        for (ContainerSnapshot snapshot : snapshotsByKey.values()) {
            int c = snapshot.countOf(itemId);
            if (c > 0) {
                matches.add(new ContainerSnapshot.Match(snapshot.dimensionId(), snapshot.pos(), c));
            }
        }
        return matches;
    }

    private void putSnapshot(String dimId, long posLong, String type, long updatedMs, Map<String, Integer> counts) {
        snapshotsByKey.put(key(dimId, posLong), new ContainerSnapshot(dimId, posLong, type, updatedMs, counts));
    }

    private static Map<String, Integer> snapshotInventory(Inventory inv) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            Identifier id = Registries.ITEM.getId(stack.getItem());
            String key = id.toString();
            counts.put(key, counts.getOrDefault(key, 0) + stack.getCount());
        }
        return counts;
    }

    private static String key(String dimId, long posLong) {
        return dimId + "|" + posLong;
    }

    /** Minimal adapter to avoid hard dependencies on BlockPos in callers. */
    public interface BlockPosLike {
        long asLong();
    }
}
