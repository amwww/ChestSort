package dev.dromer.chestsort.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ContainerSnapshot {
    public static final Codec<ContainerSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("dim").forGetter(ContainerSnapshot::dimensionId),
        Codec.LONG.fieldOf("pos").forGetter(ContainerSnapshot::posLong),
        Codec.STRING.fieldOf("type").forGetter(ContainerSnapshot::containerType),
        Codec.LONG.fieldOf("updated").forGetter(ContainerSnapshot::updatedAtMs),
        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("items").forGetter(ContainerSnapshot::itemCounts)
    ).apply(instance, ContainerSnapshot::new));

    private final String dimensionId;
    private final long posLong;
    private final String containerType;
    private final long updatedAtMs;
    private final Map<String, Integer> itemCounts;

    public ContainerSnapshot(String dimensionId, long posLong, String containerType, long updatedAtMs, Map<String, Integer> itemCounts) {
        this.dimensionId = dimensionId;
        this.posLong = posLong;
        this.containerType = containerType;
        this.updatedAtMs = updatedAtMs;
        this.itemCounts = Collections.unmodifiableMap(new HashMap<>(itemCounts));
    }

    public String dimensionId() {
        return dimensionId;
    }

    public long posLong() {
        return posLong;
    }

    public BlockPos pos() {
        return BlockPos.fromLong(posLong);
    }

    public String containerType() {
        return containerType;
    }

    public long updatedAtMs() {
        return updatedAtMs;
    }

    public Map<String, Integer> itemCounts() {
        return itemCounts;
    }

    public boolean containsItem(String itemId) {
        return itemCounts.getOrDefault(itemId, 0) > 0;
    }

    public int countOf(String itemId) {
        return itemCounts.getOrDefault(itemId, 0);
    }

    public record Match(String dimensionId, BlockPos pos, int count) {
    }
}
