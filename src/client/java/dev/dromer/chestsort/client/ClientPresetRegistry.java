package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.dromer.chestsort.filter.ContainerFilterSpec;

public final class ClientPresetRegistry {
    private static final Map<String, ContainerFilterSpec> presetsByName = new HashMap<>();
    private static final Map<String, ContainerFilterSpec> presetBlacklistsByName = new HashMap<>();

    private static volatile boolean persistenceEnabled = true;

    private static volatile byte pendingOpenMode = -1;
    private static volatile String pendingOpenName = "";

    private ClientPresetRegistry() {
    }

    public static void setFromSync(List<String> names, List<ContainerFilterSpec> specs) {
        presetsByName.clear();
        presetBlacklistsByName.clear();
        if (names == null || specs == null) return;
        int n = Math.min(names.size(), specs.size());
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            if (name == null) continue;
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            ContainerFilterSpec spec = specs.get(i);
            presetsByName.put(trimmed, (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec).normalized());
        }
    }

    public static void setFromSyncV2(List<String> names, List<ContainerFilterSpec> whitelists, List<ContainerFilterSpec> blacklists) {
        presetsByName.clear();
        presetBlacklistsByName.clear();
        if (names == null || whitelists == null || blacklists == null) return;
        int n = Math.min(names.size(), Math.min(whitelists.size(), blacklists.size()));
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            if (name == null) continue;
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;

            ContainerFilterSpec wl = whitelists.get(i);
            ContainerFilterSpec bl = blacklists.get(i);
            presetsByName.put(trimmed, (wl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : wl).normalized());
            presetBlacklistsByName.put(trimmed, (bl == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : bl).normalized());
        }
    }

    public static Map<String, ContainerFilterSpec> all() {
        return Map.copyOf(presetsByName);
    }

    public static Map<String, ContainerFilterSpec> allBlacklists() {
        return Map.copyOf(presetBlacklistsByName);
    }

    public static List<String> namesSorted() {
        ArrayList<String> out = new ArrayList<>(presetsByName.keySet());
        out.sort(Comparator.comparing(s -> s.toLowerCase(java.util.Locale.ROOT)));
        return Collections.unmodifiableList(out);
    }

    public static ContainerFilterSpec get(String name) {
        if (name == null) return null;
        return presetsByName.get(name.trim());
    }

    public static ContainerFilterSpec getBlacklist(String name) {
        if (name == null) return null;
        return presetBlacklistsByName.get(name.trim());
    }

    public static void putLocal(String name, ContainerFilterSpec spec) {
        if (name == null) return;
        String n = name.trim();
        if (n.isEmpty()) return;
        presetsByName.put(n, (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec).normalized());
        if (persistenceEnabled) ClientPresetStorage.saveFromRegistry();
    }

    public static void putLocalBlacklist(String name, ContainerFilterSpec spec) {
        if (name == null) return;
        String n = name.trim();
        if (n.isEmpty()) return;
        presetBlacklistsByName.put(n, (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec).normalized());
        if (persistenceEnabled) ClientPresetStorage.saveFromRegistry();
    }

    public static void removeLocal(String name) {
        if (name == null) return;
        String n = name.trim();
        presetsByName.remove(n);
        presetBlacklistsByName.remove(n);
        if (persistenceEnabled) ClientPresetStorage.saveFromRegistry();
    }

    static void setPersistenceEnabled(boolean enabled) {
        persistenceEnabled = enabled;
    }

    public static void requestOpen(byte mode, String name) {
        pendingOpenMode = mode;
        pendingOpenName = name == null ? "" : name;
    }

    public static boolean hasPendingOpen() {
        return pendingOpenMode >= 0;
    }

    public static byte pendingOpenMode() {
        return pendingOpenMode;
    }

    public static String pendingOpenName() {
        return pendingOpenName;
    }

    public static void clearPendingOpen() {
        pendingOpenMode = -1;
        pendingOpenName = "";
    }
}
