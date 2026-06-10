package fr.hugman.build_rush.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.misc.Author;
import fr.hugman.build_rush.registry.BRRegistries;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.plasmid.api.util.PlasmidCodecs;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFileCodec;

public record Build(Identifier structure, Component name, Optional<Author> author) {
    public static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("structure").forGetter(Build::structure),
            PlasmidCodecs.TEXT.fieldOf("name").forGetter(Build::name),
            Author.CODEC.optionalFieldOf("author").forGetter(Build::author)
    ).apply(instance, Build::new));

    public static final Codec<Holder<Build>> REGISTRY_CODEC = RegistryFileCodec.create(BRRegistries.BUILD, CODEC);
    public static final Codec<HolderSet<Build>> LIST_CODEC = RegistryCodecs.homogeneousList(BRRegistries.BUILD, CODEC);
    public static final Codec<List<HolderSet<Build>>> LISTS_CODEC = RegistryCodecs.homogeneousList(BRRegistries.BUILD, CODEC, true).listOf();
}
