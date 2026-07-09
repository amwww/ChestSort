package dev.dromer.chestsort.util;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/**
 * Resolves a container to a canonical position key so multi-block containers (double chests)
 * share the same stored filter/snapshot regardless of which half was interacted with.
 */
public final class ContainerCanonicalizer {
    private ContainerCanonicalizer() {
    }

    public record Canonicalized(long posLong, Container snapshotInventory, String containerType) {
    }

    public static Canonicalized canonicalize(ServerLevel world, BlockPos clickedPos, BlockEntity be) {
        if (be instanceof ChestBlockEntity chest) {
            return canonicalizeChest(world, clickedPos, chest);
        }
        if (be instanceof BarrelBlockEntity barrel) {
            return new Canonicalized(clickedPos.asLong(), barrel, "barrel");
        }
        return new Canonicalized(clickedPos.asLong(), null, "");
    }

    private static Canonicalized canonicalizeChest(ServerLevel world, BlockPos clickedPos, ChestBlockEntity chest) {
        var state = chest.getBlockState();
        if (!(state.getBlock() instanceof ChestBlock chestBlock)) {
            return new Canonicalized(clickedPos.asLong(), chest, "chest");
        }

        var source = chestBlock.combine(state, world, clickedPos, true);
        return source.apply(new DoubleBlockCombiner.Combiner<ChestBlockEntity, Canonicalized>() {
            @Override
            public Canonicalized acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
                long p1 = first.getBlockPos().asLong();
                long p2 = second.getBlockPos().asLong();
                long canonical = Math.min(p1, p2);
                Container inv = new CompoundContainer(first, second);
                return new Canonicalized(canonical, inv, "chest");
            }

            @Override
            public Canonicalized acceptSingle(ChestBlockEntity single) {
                return new Canonicalized(single.getBlockPos().asLong(), single, "chest");
            }

            @Override
            public Canonicalized acceptNone() {
                return new Canonicalized(clickedPos.asLong(), chest, "chest");
            }
        });
    }
}
