package dev.dromer.chestsort.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-container "autosort on open" preference, stored entirely client-side. This lets autosort
 * work even when the connected server has no ChestSort support at all (or an older version
 * without server-side autosort persistence) - the client just remembers which containers it
 * should send a normal SortRequestPayload for whenever they're opened.
 */
public final class ClientAutosortState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Codec<List<String>> CODEC = Codec.STRING.listOf();

    private static final Set<String> enabledKeys = new HashSet<>();

    private ClientAutosortState() {
    }

    private static String key(String dimId, long posLong) {
        // Scope by world/server so two unrelated worlds sharing block coordinates don't collide.
        return ClientWorldId.current() + "|" + (dimId == null ? "" : dimId) + "|" + posLong;
    }

    public static boolean isEnabled(String dimId, long posLong) {
        if (dimId == null || dimId.isEmpty()) return false;
        return enabledKeys.contains(key(dimId, posLong));
    }

    public static void setEnabled(String dimId, long posLong, boolean enabled) {
        if (dimId == null || dimId.isEmpty()) return;
        String k = key(dimId, posLong);
        if (enabled) {
            enabledKeys.add(k);
        } else {
            enabledKeys.remove(k);
        }
        save();
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestsort-autosort.json");
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            List<String> decoded = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            enabledKeys.clear();
            for (String k : decoded) {
                if (k != null && !k.isEmpty()) enabledKeys.add(k);
            }
        } catch (Throwable ignored) {
            // If parsing fails, don't crash the client.
        }
    }

    public static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(enabledKeys)).getOrThrow();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }
}
