package fr.hugman.build_rush.registry.tag;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.registry.BRRegistries;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class BRTags {
	public static final TagKey<Block> IGNORED_IN_COMPARISON = TagKey.of(RegistryKeys.BLOCK, BuildRush.id("ignored_in_comparison"));
	public static final TagKey<Build> BLACKLIST = TagKey.of(BRRegistries.BUILD, BuildRush.id("blacklist"));
	public static final TagKey<Build> GENERIC = TagKey.of(BRRegistries.BUILD, BuildRush.id("generic"));
}
