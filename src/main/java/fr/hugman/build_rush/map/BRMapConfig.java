package fr.hugman.build_rush.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

public record BRMapConfig(Identifier template, int nametagOffset, float nametagSize) {
	public static final Codec<BRMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("template").forGetter(BRMapConfig::template),
			Codec.INT.optionalFieldOf("nametag_offset", 10).forGetter(BRMapConfig::nametagOffset),
			Codec.FLOAT.optionalFieldOf("nametag_size", 5.0f).forGetter(BRMapConfig::nametagSize)
	).apply(instance, BRMapConfig::new));
}
