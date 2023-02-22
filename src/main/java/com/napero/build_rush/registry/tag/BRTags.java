package com.napero.build_rush.registry.tag;

import com.napero.build_rush.BuildRush;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public class BRTags {
	public static final TagKey<Block> IGNORED_IN_COMPARISON = TagKey.of(RegistryKeys.BLOCK, BuildRush.id("ignored_in_comparison"));
}
