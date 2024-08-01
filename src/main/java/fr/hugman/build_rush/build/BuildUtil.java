package fr.hugman.build_rush.build;

import fr.hugman.build_rush.misc.CachedBlocks;
import fr.hugman.build_rush.registry.tag.BRTags;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BuildUtil {
	public static boolean areEqual(BlockState sourceState, @Nullable NbtCompound sourceNbt, BlockState targetState, @Nullable NbtCompound targetNbt) {
		var sourceBlock = sourceState.getBlock();
		var targetBlock = targetState.getBlock();

		if(sourceBlock != targetBlock) return false;

		if(sourceBlock instanceof ButtonBlock) {
			if(sourceState.get(ButtonBlock.FACE) == BlockFace.WALL) {
				return sourceState.get(ButtonBlock.FACING) == targetState.get(ButtonBlock.FACING);
			}

			if((sourceState.get(ButtonBlock.FACING) == Direction.NORTH && targetState.get(ButtonBlock.FACING) == Direction.SOUTH) ||
					(sourceState.get(ButtonBlock.FACING) == Direction.SOUTH && targetState.get(ButtonBlock.FACING) == Direction.NORTH) ||
					(sourceState.get(ButtonBlock.FACING) == Direction.EAST && targetState.get(ButtonBlock.FACING) == Direction.WEST) ||
					(sourceState.get(ButtonBlock.FACING) == Direction.WEST && targetState.get(ButtonBlock.FACING) == Direction.EAST))
				return true;

			return sourceState.get(ButtonBlock.FACING) == targetState.get(ButtonBlock.FACING);
		}

		return sourceState.equals(targetState);
	}

	public static int getStateComplexity(BlockState state) {
		var block = state.getBlock();
		if(state.isAir())
			return 0;
		if(block instanceof StairsBlock)
			return 2;
		if(block instanceof ButtonBlock)
			return 2;
		if(block instanceof ChainBlock)
			return 2;
		if(block instanceof VineBlock)
			return 3;
		if(block instanceof MultifaceGrowthBlock)
			return 3;
		return 1;
	}

	public static int getBuildComplexity(CachedBlocks build) {
		int complexity = 0;
		var blockList = new ArrayList<Block>();
		for(var pos : build.positions()) {
			var state = build.state(pos);
			if(state.isIn(BRTags.IGNORED_IN_COMPARISON)) {
				continue;
			}
			complexity += BuildUtil.getStateComplexity(state);
			if(!blockList.contains(state.getBlock())) {
				blockList.add(state.getBlock());
				complexity += 2;
			}
		}
		return complexity;
	}
}
