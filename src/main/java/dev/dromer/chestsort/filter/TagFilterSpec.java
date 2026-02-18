package dev.dromer.chestsort.filter;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TagFilterSpec(String tagId, List<String> exceptions) {
    public static final Codec<TagFilterSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("tag", "").forGetter(TagFilterSpec::tagId),
        Codec.STRING.listOf().optionalFieldOf("except", List.of()).forGetter(TagFilterSpec::exceptions)
    ).apply(instance, TagFilterSpec::new));

    public TagFilterSpec {
        if (tagId == null) tagId = "";
        if (exceptions == null) exceptions = List.of();
    }
}
