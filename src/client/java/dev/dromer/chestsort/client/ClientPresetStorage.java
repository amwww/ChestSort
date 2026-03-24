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

public final class ClientPresetStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Codec<Map<String, ContainerFilterSpec>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, ContainerFilterSpec.CODEC);

    private static final Codec<PresetFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MAP_CODEC.optionalFieldOf("whitelists", Map.of()).forGetter(PresetFile::whitelists),
        MAP_CODEC.optionalFieldOf("blacklists", Map.of()).forGetter(PresetFile::blacklists)
    ).apply(instance, PresetFile::new));

    private ClientPresetStorage() {
    }

    public record PresetFile(Map<String, ContainerFilterSpec> whitelists, Map<String, ContainerFilterSpec> blacklists) {
        public PresetFile {
            if (whitelists == null) whitelists = Map.of();
            if (blacklists == null) blacklists = Map.of();
        }
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestsort-presets.json");
    }

    public static void loadIntoRegistry() {
        Path path = filePath();
        if (!Files.exists(path)) return;

        ClientPresetRegistry.setPersistenceEnabled(false);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            PresetFile decoded = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

            // Apply in name order for stability.
            Map<String, ContainerFilterSpec> wl = new LinkedHashMap<>(decoded.whitelists());
            Map<String, ContainerFilterSpec> bl = new LinkedHashMap<>(decoded.blacklists());

            for (var e : wl.entrySet()) {
                String name = e.getKey();
                if (name == null || name.trim().isEmpty()) continue;
                ClientPresetRegistry.putLocal(name, e.getValue());
            }
            for (var e : bl.entrySet()) {
                String name = e.getKey();
                if (name == null || name.trim().isEmpty()) continue;
                ClientPresetRegistry.putLocalBlacklist(name, e.getValue());
            }
        } catch (Throwable ignored) {
            // If parsing fails, don't crash the client.
        } finally {
            ClientPresetRegistry.setPersistenceEnabled(true);
        }
    }

    public static void saveFromRegistry() {
        Path path = filePath();

        // Copy current state.
        Map<String, ContainerFilterSpec> wl = ClientPresetRegistry.all();
        Map<String, ContainerFilterSpec> bl = ClientPresetRegistry.allBlacklists();
        PresetFile file = new PresetFile(wl, bl);

        try {
            Files.createDirectories(path.getParent());
            JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, file).getOrThrow();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable ignored) {
        }
    }
}
