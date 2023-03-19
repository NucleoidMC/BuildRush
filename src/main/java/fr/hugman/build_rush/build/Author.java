package fr.hugman.build_rush.build;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.util.Optional;
import java.util.UUID;


public record Author(String name, Optional<UUID> uuid) {
	public static final Codec<Author> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("name").forGetter(Author::name),
			Uuids.CODEC.optionalFieldOf("uuid").forGetter(Author::uuid)
	).apply(instance, Author::new));

	public static final Codec<Author> CODEC = Codec.either(BASE_CODEC, Codec.STRING).xmap(
			either -> either.map(
					author -> author,
					string -> new Author(string, Optional.empty())
			),
			author -> author.uuid().isEmpty() ? Either.right(author.name()) : Either.left(author)
	);
}
