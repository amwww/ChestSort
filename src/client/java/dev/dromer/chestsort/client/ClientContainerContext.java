package dev.dromer.chestsort.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.dromer.chestsort.filter.ContainerFilterSpec;

public final class ClientContainerContext {
    private static String dimensionId = "";
    private static long posLong = 0L;
    private static String containerType = "";
    private static List<String> filterItems = List.of();
    private static ContainerFilterSpec filterSpec = new ContainerFilterSpec(List.of(), List.of(), List.of());
    private static ContainerFilterSpec blacklistSpec = new ContainerFilterSpec(List.of(), List.of(), List.of());
    private static boolean whitelistPriority = true;

    private ClientContainerContext() {
    }

    public static void set(String dimId, long pos, String type, List<String> filter) {
        String newDim = dimId == null ? "" : dimId;
        String newType = type == null ? "" : type;
        List<String> newItems = filter == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(filter));

        boolean sameKey = newDim.equals(dimensionId) && pos == posLong && newType.equals(containerType);

        dimensionId = newDim;
        posLong = pos;
        containerType = newType;
        filterItems = newItems;

        if (sameKey && filterSpec != null) {
            // Legacy payload only carries items; preserve any richer state from v2 (tags/presets/autosort)
            // in case packets arrive out-of-order.
            filterSpec = new ContainerFilterSpec(
                filterItems,
                filterSpec.tags(),
                filterSpec.presets(),
                filterSpec.autosort()
            ).normalized();
        } else {
            filterSpec = ContainerFilterSpec.fromLegacyItems(filterItems).normalized();
            blacklistSpec = new ContainerFilterSpec(List.of(), List.of(), List.of()).normalized();
            whitelistPriority = true;
        }
    }

    public static void set(String dimId, long pos, String type, ContainerFilterSpec spec) {
        String newDim = dimId == null ? "" : dimId;
        String newType = type == null ? "" : type;
        boolean sameKey = newDim.equals(dimensionId) && pos == posLong && newType.equals(containerType);

        dimensionId = newDim;
        posLong = pos;
        containerType = newType;
        filterSpec = (spec == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : spec).normalized();
        filterItems = Collections.unmodifiableList(new ArrayList<>(filterSpec.items()));

        if (!sameKey) {
            blacklistSpec = new ContainerFilterSpec(List.of(), List.of(), List.of()).normalized();
            whitelistPriority = true;
        }
    }

    public static void set(String dimId, long pos, String type, ContainerFilterSpec whitelist, ContainerFilterSpec blacklist, boolean whitelistPriority) {
        dimensionId = dimId == null ? "" : dimId;
        posLong = pos;
        containerType = type == null ? "" : type;
        filterSpec = (whitelist == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : whitelist).normalized();
        blacklistSpec = (blacklist == null ? new ContainerFilterSpec(List.of(), List.of(), List.of()) : blacklist).normalized();
        ClientContainerContext.whitelistPriority = whitelistPriority;
        filterItems = Collections.unmodifiableList(new ArrayList<>(filterSpec.items()));
    }

    public static void clear() {
        dimensionId = "";
        posLong = 0L;
        containerType = "";
        filterItems = List.of();
        filterSpec = new ContainerFilterSpec(List.of(), List.of(), List.of());
        blacklistSpec = new ContainerFilterSpec(List.of(), List.of(), List.of());
        whitelistPriority = true;
    }

    public static boolean isChestOrBarrel() {
        return "chest".equals(containerType) || "barrel".equals(containerType);
    }

    public static String dimensionId() {
        return dimensionId;
    }

    public static long posLong() {
        return posLong;
    }

    public static String containerType() {
        return containerType;
    }

    public static List<String> filterItems() {
        return filterItems;
    }

    public static ContainerFilterSpec filterSpec() {
        return filterSpec;
    }

    public static ContainerFilterSpec blacklistSpec() {
        return blacklistSpec;
    }

    public static boolean whitelistPriority() {
        return whitelistPriority;
    }

    public static boolean hasFilter() {
        return filterSpec != null && !filterSpec.isEmpty();
    }
}
