package fr.hugman.build_rush.registry.tag;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.registry.BRRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class BRTags {
	public static final TagKey<Block> IGNORED_IN_COMPARISON = TagKey.create(Registries.BLOCK, BuildRush.id("ignored_in_comparison"));
	public static final TagKey<Build> BLACKLIST = TagKey.create(BRRegistries.BUILD, BuildRush.id("blacklist"));
	public static final TagKey<Build> GENERIC = TagKey.create(BRRegistries.BUILD, BuildRush.id("generic"));
}
