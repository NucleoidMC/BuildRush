package fr.hugman.build_rush.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.util.PlasmidCodecs;

import java.util.Optional;

public record Build(Identifier id, Text name, Optional<Author> author) {
	public static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("structure").forGetter(Build::id),
			PlasmidCodecs.TEXT.fieldOf("name").forGetter(Build::name),
			Author.CODEC.optionalFieldOf("author").forGetter(Build::author)
	).apply(instance, Build::new));
}
