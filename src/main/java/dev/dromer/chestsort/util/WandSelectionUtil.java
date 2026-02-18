package dev.dromer.chestsort.util;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public final class WandSelectionUtil {
    private WandSelectionUtil() {
    }

    public static long volume(BlockPos a, BlockPos b) {
        if (a == null || b == null) return 0L;
        long dx = (long) Math.abs(a.getX() - b.getX()) + 1L;
        long dy = (long) Math.abs(a.getY() - b.getY()) + 1L;
        long dz = (long) Math.abs(a.getZ() - b.getZ()) + 1L;
        return dx * dy * dz;
    }

    /** Returns canonical container positions (deduping multi-block containers) for LOADED chunks only. */
    public static Set<Long> findLoadedContainerCanonicalPosLongsInBox(ServerWorld world, BlockPos a, BlockPos b) {
        if (world == null || a == null || b == null) return Set.of();

        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        LinkedHashSet<Long> out = new LinkedHashSet<>();
        LinkedHashSet<Long> seenCanonical = new LinkedHashSet<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                var chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(chunk instanceof WorldChunk worldChunk)) continue;

                for (var be : worldChunk.getBlockEntities().values()) {
                    if (be == null) continue;
                    BlockPos pos = be.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX) continue;
                    if (pos.getY() < minY || pos.getY() > maxY) continue;
                    if (pos.getZ() < minZ || pos.getZ() > maxZ) continue;

                    var canonical = ContainerCanonicalizer.canonicalize(world, pos, be);
                    if (canonical.snapshotInventory() == null) continue;

                    long canonicalPosLong = canonical.posLong();
                    if (!seenCanonical.add(canonicalPosLong)) continue;
                    out.add(canonicalPosLong);
                }
            }
        }

        return Set.copyOf(out);
    }
}
