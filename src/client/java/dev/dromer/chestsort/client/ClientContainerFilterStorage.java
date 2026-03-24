package dev.dromer.chestsort.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.dromer.chestsort.filter.ContainerFilterSpec;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-only per-container filter persistence.
 * Keyed by (dimensionId, posLong) where posLong is BlockPos.asLong().
 */
public final class ClientContainerFilterStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Codec<ContainerFilterSpec> SPEC_CODEC = ContainerFilterSpec.CODEC;

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SPEC_CODEC.optionalFieldOf("whitelist", new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false)).forGetter(Entry::whitelist),
        SPEC_CODEC.optionalFieldOf("blacklist", new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false)).forGetter(Entry::blacklist),
        Codec.BOOL.optionalFieldOf("whitelistPriority", true).forGetter(Entry::whitelistPriority)
    ).apply(instance, Entry::new));

    private static final Codec<Map<String, Entry>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, ENTRY_CODEC);

    private static Map<String, Entry> byKey = Map.of();

    private ClientContainerFilterStorage() {
    }

    public record Entry(ContainerFilterSpec whitelist, ContainerFilterSpec blacklist, boolean whitelistPriority) {
        public Entry {
            if (whitelist == null) whitelist = new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false);
            if (blacklist == null) blacklist = new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false);
        }
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestsort-containers.json");
    }

    private static String key(String dimId, long posLong) {
        String d = dimId == null ? "" : dimId;
        return d + "|" + posLong;
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) {
            byKey = Map.of();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            Map<String, Entry> decoded = MAP_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            byKey = decoded == null || decoded.isEmpty() ? Map.of() : Map.copyOf(decoded);
        } catch (Throwable ignored) {
            byKey = Map.of();
        }
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonElement json = MAP_CODEC.encodeStart(JsonOps.INSTANCE, byKey).getOrThrow();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    public static Entry get(String dimId, long posLong) {
        if (dimId == null || dimId.isEmpty()) return null;
        return byKey.get(key(dimId, posLong));
    }

    public static void put(String dimId, long posLong, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist, boolean whitelistPriority) {
        if (dimId == null || dimId.isEmpty()) return;
        if (posLong == 0L) return;

        ContainerFilterSpec wl = (whitelist == null ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false) : whitelist).normalized();
        ContainerFilterSpec bl = (blacklist == null ? new ContainerFilterSpec(java.util.List.of(), java.util.List.of(), java.util.List.of(), false) : blacklist).normalized();

        Entry entry = new Entry(wl, bl, whitelistPriority);
        LinkedHashMap<String, Entry> copy = new LinkedHashMap<>(byKey);
        copy.put(key(dimId, posLong), entry);
        byKey = Map.copyOf(copy);
        save();
    }
}
