package fr.hugman.build_rush.plot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.util.PlasmidCodecs;

import java.util.Optional;

public record PlotStructure(Identifier id, Text name, Optional<Author> author) {
	public static final Codec<PlotStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("id").forGetter(PlotStructure::id),
			PlasmidCodecs.TEXT.fieldOf("name").forGetter(PlotStructure::name),
			Author.CODEC.optionalFieldOf("author").forGetter(PlotStructure::author)
	).apply(instance, PlotStructure::new));
}
