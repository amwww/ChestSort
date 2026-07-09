package dev.dromer.chestsort.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Resolves a container to a canonical position key so multi-block containers (double chests)
 * share the same stored filter regardless of which half was interacted with. Client-side
 * equivalent of the old server-side ContainerCanonicalizer.
 */
public final class ClientContainerCanonicalizer {
    private ClientContainerCanonicalizer() {
    }

    public record Canonical(long posLong, String containerType) {
    }

    /** Returns null if the block entity at clickedPos is not a supported container. */
    public static Canonical canonicalize(Level level, BlockPos clickedPos, BlockEntity be) {
        if (be instanceof ChestBlockEntity chest) {
            var state = chest.getBlockState();
            if (state.getBlock() instanceof ChestBlock chestBlock) {
                var source = chestBlock.combine(state, level, clickedPos, true);
                return source.apply(new DoubleBlockCombiner.Combiner<ChestBlockEntity, Canonical>() {
                    @Override
                    public Canonical acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                        long p1 = first.getBlockPos().asLong();
                        long p2 = second.getBlockPos().asLong();
                        return new Canonical(Math.min(p1, p2), "chest");
                    }

                    @Override
                    public Canonical acceptSingle(ChestBlockEntity single) {
                        return new Canonical(single.getBlockPos().asLong(), "chest");
                    }

                    @Override
                    public Canonical acceptNone() {
                        return new Canonical(clickedPos.asLong(), "chest");
                    }
                });
            }
            return new Canonical(clickedPos.asLong(), "chest");
        }
        if (be instanceof BarrelBlockEntity) {
            return new Canonical(clickedPos.asLong(), "barrel");
        }
        return null;
    }
}
