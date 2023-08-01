package fr.hugman.build_rush.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.misc.Author;
import fr.hugman.build_rush.registry.BRRegistries;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.util.PlasmidCodecs;

import java.util.List;
import java.util.Optional;

public record Build(Identifier structure, Text name, Optional<Author> author) {
    public static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("structure").forGetter(Build::structure),
            PlasmidCodecs.TEXT.fieldOf("name").forGetter(Build::name),
            Author.CODEC.optionalFieldOf("author").forGetter(Build::author)
    ).apply(instance, Build::new));

    public static final Codec<RegistryEntry<Build>> REGISTRY_CODEC = RegistryElementCodec.of(BRRegistries.BUILD, CODEC);
    public static final Codec<RegistryEntryList<Build>> LIST_CODEC = RegistryCodecs.entryList(BRRegistries.BUILD, CODEC);
    public static final Codec<List<RegistryEntryList<Build>>> LISTS_CODEC = RegistryCodecs.entryList(BRRegistries.BUILD, CODEC, true).listOf();
}
