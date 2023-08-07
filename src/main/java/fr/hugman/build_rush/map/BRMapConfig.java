package fr.hugman.build_rush.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.misc.Author;
import net.minecraft.util.Identifier;

import java.util.Optional;

public record BRMapConfig(Identifier template, Optional<Author> author, int nametagOffset, float nametagSize) {
	public static final Codec<BRMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("template").forGetter(BRMapConfig::template),
			Author.CODEC.optionalFieldOf("author").forGetter(BRMapConfig::author),
			Codec.INT.optionalFieldOf("nametag_offset", 10).forGetter(BRMapConfig::nametagOffset),
			Codec.FLOAT.optionalFieldOf("nametag_size", 5.0f).forGetter(BRMapConfig::nametagSize)
	).apply(instance, BRMapConfig::new));
}
