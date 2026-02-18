package dev.dromer.chestsort.util;

import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Resolves a container to a canonical position key so multi-block containers (double chests)
 * share the same stored filter/snapshot regardless of which half was interacted with.
 */
public final class ContainerCanonicalizer {
    private ContainerCanonicalizer() {
    }

    public record Canonicalized(long posLong, Inventory snapshotInventory, String containerType) {
    }

    public static Canonicalized canonicalize(ServerWorld world, BlockPos clickedPos, BlockEntity be) {
        if (be instanceof ChestBlockEntity chest) {
            return canonicalizeChest(world, clickedPos, chest);
        }
        if (be instanceof BarrelBlockEntity barrel) {
            return new Canonicalized(clickedPos.asLong(), barrel, "barrel");
        }
        return new Canonicalized(clickedPos.asLong(), null, "");
    }

    private static Canonicalized canonicalizeChest(ServerWorld world, BlockPos clickedPos, ChestBlockEntity chest) {
        var state = chest.getCachedState();
        if (!(state.getBlock() instanceof ChestBlock chestBlock)) {
            return new Canonicalized(clickedPos.asLong(), chest, "chest");
        }

        var source = chestBlock.getBlockEntitySource(state, world, clickedPos, true);
        return source.apply(new DoubleBlockProperties.PropertyRetriever<ChestBlockEntity, Canonicalized>() {
            @Override
            public Canonicalized getFromBoth(ChestBlockEntity first, ChestBlockEntity second) {
                long p1 = first.getPos().asLong();
                long p2 = second.getPos().asLong();
                long canonical = Math.min(p1, p2);
                Inventory inv = new DoubleInventory(first, second);
                return new Canonicalized(canonical, inv, "chest");
            }

            @Override
            public Canonicalized getFrom(ChestBlockEntity single) {
                return new Canonicalized(single.getPos().asLong(), single, "chest");
            }

            @Override
            public Canonicalized getFallback() {
                return new Canonicalized(clickedPos.asLong(), chest, "chest");
            }
        });
    }
}
