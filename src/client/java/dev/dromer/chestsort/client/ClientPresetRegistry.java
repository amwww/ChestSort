package dev.dromer.chestsort.client;

import dev.dromer.chestsort.filter.ContainerFilterSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientPresetRegistry {
    private static final Map<String, ContainerFilterSpec> presetsByName = new HashMap<>();

    private static volatile byte pendingOpenMode = -1;
    private static volatile String pendingOpenName = "";

    private ClientPresetRegistry() {
    }

    public static void setFromSync(List<String> names, List<ContainerFilterSpec> specs) {
        presetsByName.clear();
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

    public static Map<String, ContainerFilterSpec> all() {
        return Map.copyOf(presetsByName);
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

    public static void putLocal(String name, ContainerFilterSpec spec) {
        if (name == null) return;
        String n = name.trim();
        if (n.isEmpty()) return;
        presetsByName.put(n, (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec).normalized());
    }

    public static void removeLocal(String name) {
        if (name == null) return;
        presetsByName.remove(name.trim());
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
