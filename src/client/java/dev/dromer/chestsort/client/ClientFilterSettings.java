package dev.dromer.chestsort.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Global (not per-container) client-only settings: autosort mode, highlights mode, and the
 * item blacklist. This mod never relies on any server-side support for these.
 */
public final class ClientFilterSettings {
    public static final String AUTOSORT_NEVER = "never";
    public static final String AUTOSORT_SELECTED = "selected";
    public static final String AUTOSORT_ALWAYS = "always";

    public static final String HIGHLIGHTS_ON = "on";
    public static final String HIGHLIGHTS_OFF = "off";
    public static final String HIGHLIGHTS_UNTIL_OPENED = "until_opened";

    public static final String BLACKLIST_MODE_PREVENT_SORT = "preventsort";
    public static final String BLACKLIST_MODE_STRICT_PREVENT_SORT = "strictpreventsort";
    public static final String BLACKLIST_MODE_PREVENT_ENTRY = "prevententry";
    public static final String BLACKLIST_MODE_STRICT_PREVENT_ENTRY = "strictprevententry";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String autosortMode = AUTOSORT_SELECTED;
    private static String highlightsMode = HIGHLIGHTS_ON;
    private static String blacklistMode = BLACKLIST_MODE_PREVENT_SORT;
    private static Set<String> blacklistItems = new LinkedHashSet<>();
    private static String lastFindItem = "";
    private static boolean armClearHighlightsOnNextOpen = false;

    private static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("autosortMode", AUTOSORT_SELECTED).forGetter(Data::autosortMode),
        Codec.STRING.optionalFieldOf("highlightsMode", HIGHLIGHTS_ON).forGetter(Data::highlightsMode),
        Codec.STRING.optionalFieldOf("blacklistMode", BLACKLIST_MODE_PREVENT_SORT).forGetter(Data::blacklistMode),
        Codec.STRING.listOf().optionalFieldOf("blacklistItems", List.of()).forGetter(Data::blacklistItems),
        Codec.STRING.optionalFieldOf("lastFindItem", "").forGetter(Data::lastFindItem)
    ).apply(instance, Data::new));

    private record Data(String autosortMode, String highlightsMode, String blacklistMode, List<String> blacklistItems, String lastFindItem) {
    }

    private ClientFilterSettings() {
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestsort-settings.json");
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            Data decoded = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            autosortMode = decoded.autosortMode();
            highlightsMode = decoded.highlightsMode();
            blacklistMode = decoded.blacklistMode();
            blacklistItems = new LinkedHashSet<>(decoded.blacklistItems() == null ? List.of() : decoded.blacklistItems());
            lastFindItem = decoded.lastFindItem() == null ? "" : decoded.lastFindItem();
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Data data = new Data(autosortMode, highlightsMode, blacklistMode, List.copyOf(blacklistItems), lastFindItem);
            JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String autosortMode() {
        return autosortMode;
    }

    public static void setAutosortMode(String mode) {
        autosortMode = mode;
        save();
    }

    public static String highlightsMode() {
        return highlightsMode;
    }

    public static void setHighlightsMode(String mode) {
        highlightsMode = mode;
        save();
    }

    public static String blacklistMode() {
        return blacklistMode;
    }

    public static void setBlacklistMode(String mode) {
        blacklistMode = mode;
        save();
    }

    public static boolean blacklistModePreventsEntry() {
        return BLACKLIST_MODE_PREVENT_ENTRY.equals(blacklistMode) || BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(blacklistMode);
    }

    public static boolean blacklistModeStrict() {
        return BLACKLIST_MODE_STRICT_PREVENT_SORT.equals(blacklistMode) || BLACKLIST_MODE_STRICT_PREVENT_ENTRY.equals(blacklistMode);
    }

    public static Set<String> blacklistItems() {
        return Set.copyOf(blacklistItems);
    }

    public static boolean isBlacklisted(String itemId) {
        return itemId != null && blacklistItems.contains(itemId);
    }

    public static boolean addToBlacklist(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        boolean changed = blacklistItems.add(itemId);
        if (changed) save();
        return changed;
    }

    public static int addAllToBlacklist(java.util.Collection<String> itemIds) {
        if (itemIds == null) return 0;
        int added = 0;
        for (String id : itemIds) {
            if (id == null || id.isEmpty()) continue;
            if (blacklistItems.add(id)) added++;
        }
        if (added > 0) save();
        return added;
    }

    public static boolean removeFromBlacklist(String itemId) {
        if (itemId == null) return false;
        boolean changed = blacklistItems.remove(itemId);
        if (changed) save();
        return changed;
    }

    public static int clearBlacklist() {
        int size = blacklistItems.size();
        blacklistItems = new LinkedHashSet<>();
        if (size > 0) save();
        return size;
    }

    public static String lastFindItem() {
        return lastFindItem;
    }

    public static void setLastFindItem(String itemId) {
        lastFindItem = itemId == null ? "" : itemId;
        save();
    }

    public static boolean isArmClearHighlightsOnNextOpen() {
        return armClearHighlightsOnNextOpen;
    }

    public static void armClearHighlightsOnNextOpen() {
        armClearHighlightsOnNextOpen = true;
    }

    public static void clearHighlightsOnNextOpenFlag() {
        armClearHighlightsOnNextOpen = false;
    }
}
