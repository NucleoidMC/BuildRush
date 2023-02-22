package com.napero.build_rush.plot;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.world.World;
import xyz.nucleoid.map_templates.BlockBounds;

public class PlotUtil {
	public static ItemStack convert(BlockState state) {
		return new ItemStack(state.getBlock());
	}

	/**
	 * Compares a structure template to the blocks present in certain bounds.
	 * <p>+2 for each block that perfectly matches<br>
	 * +1 for each block that is the correct block, but not the same state
	 *
	 * @param world
	 * @param original the original structure template
	 * @param bounds the bounds to compare to
	 * @return
	 */
	public static double compare(World world, StructureTemplate original, BlockBounds bounds) {
		return 1.0D;
	}
}
