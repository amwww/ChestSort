package dev.dromer.chestsort.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Client-only index of "what's in containers I've opened", used by /cs find and /cs scan.
 * Unlike the old server-side ContainerSnapshot system, this is only ever populated by the
 * local player actually opening a container - there is no world-wide server scan. Scoped by
 * world/server so unrelated worlds that share block coordinates don't collide.
 */
public final class ClientContainerFindIndex {
    public record Entry(String worldId, String dimensionId, long posLong, List<String> itemIds) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("worldId", "unknown").forGetter(Entry::worldId),
        Codec.STRING.fieldOf("dimensionId").forGetter(Entry::dimensionId),
        Codec.LONG.fieldOf("posLong").forGetter(Entry::posLong),
        Codec.STRING.listOf().optionalFieldOf("itemIds", List.of()).forGetter(Entry::itemIds)
    ).apply(instance, Entry::new));
    private static final Codec<List<Entry>> CODEC = ENTRY_CODEC.listOf();

    private static final Map<String, Entry> byKey = new LinkedHashMap<>();

    private ClientContainerFindIndex() {
    }

    private static String key(String worldId, String dimId, long posLong) {
        return worldId + "|" + (dimId == null ? "" : dimId) + "|" + posLong;
    }

    public static void record(String dimId, long posLong, List<String> itemIds) {
        if (dimId == null || dimId.isEmpty()) return;
        String worldId = ClientWorldId.current();
        byKey.put(key(worldId, dimId, posLong), new Entry(worldId, dimId, posLong, itemIds == null ? List.of() : List.copyOf(itemIds)));
        save();
    }

    public record Match(String dimensionId, BlockPos pos, int count) {
    }

    public static List<Match> find(String itemId) {
        List<Match> out = new ArrayList<>();
        if (itemId == null || itemId.isEmpty()) return out;
        String worldId = ClientWorldId.current();
        for (Entry e : byKey.values()) {
            if (!worldId.equals(e.worldId())) continue;
            if (e.itemIds() == null) continue;
            int count = 0;
            for (String id : e.itemIds()) {
                if (itemId.equals(id)) count++;
            }
            if (count > 0) {
                out.add(new Match(e.dimensionId(), BlockPos.of(e.posLong()), count));
            }
        }
        return out;
    }

    /** Number of known containers in the CURRENT world/server only. */
    public static int size() {
        String worldId = ClientWorldId.current();
        int count = 0;
        for (Entry e : byKey.values()) {
            if (worldId.equals(e.worldId())) count++;
        }
        return count;
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestsort-find-index.json");
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            List<Entry> decoded = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            byKey.clear();
            for (Entry e : decoded) {
                if (e == null || e.dimensionId() == null || e.dimensionId().isEmpty()) continue;
                byKey.put(key(e.worldId(), e.dimensionId(), e.posLong()), e);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(byKey.values())).getOrThrow();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }
}
