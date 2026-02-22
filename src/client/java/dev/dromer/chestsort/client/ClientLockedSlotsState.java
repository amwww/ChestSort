package dev.dromer.chestsort.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientLockedSlotsState {
    private static Set<Integer> locked = Set.of();

    private ClientLockedSlotsState() {
    }

    public static void setFromSync(List<Integer> lockedSlots) {
        if (lockedSlots == null || lockedSlots.isEmpty()) {
            locked = Set.of();
            return;
        }

        HashSet<Integer> out = new HashSet<>();
        for (Integer i : lockedSlots) {
            if (i == null) continue;
            if (i < 0) continue;
            out.add(i);
        }
        locked = Set.copyOf(out);
    }

    public static boolean isLocked(int playerInventoryIndex) {
        if (playerInventoryIndex < 0) return false;
        return locked.contains(playerInventoryIndex);
    }

    public static void toggleLocal(int playerInventoryIndex) {
        if (playerInventoryIndex < 0) return;
        HashSet<Integer> out = new HashSet<>(locked);
        if (!out.add(playerInventoryIndex)) {
            out.remove(playerInventoryIndex);
        }
        locked = out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    public static void clear() {
        locked = Set.of();
    }
}
