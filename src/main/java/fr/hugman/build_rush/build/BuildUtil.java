package fr.hugman.build_rush.build;

import fr.hugman.build_rush.misc.CachedBlocks;
import fr.hugman.build_rush.registry.tag.BRTags;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.MultifaceSpreadeableBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class BuildUtil {
	public static boolean areEqual(BlockState sourceState, @Nullable CompoundTag sourceNbt, BlockState targetState, @Nullable CompoundTag targetNbt) {
		var sourceBlock = sourceState.getBlock();
		var targetBlock = targetState.getBlock();

		if(sourceBlock != targetBlock) return false;

		if(sourceBlock instanceof ButtonBlock) {
			if(sourceState.getValue(ButtonBlock.FACE) == AttachFace.WALL) {
				return sourceState.getValue(ButtonBlock.FACING) == targetState.getValue(ButtonBlock.FACING);
			}

			if((sourceState.getValue(ButtonBlock.FACING) == Direction.NORTH && targetState.getValue(ButtonBlock.FACING) == Direction.SOUTH) ||
					(sourceState.getValue(ButtonBlock.FACING) == Direction.SOUTH && targetState.getValue(ButtonBlock.FACING) == Direction.NORTH) ||
					(sourceState.getValue(ButtonBlock.FACING) == Direction.EAST && targetState.getValue(ButtonBlock.FACING) == Direction.WEST) ||
					(sourceState.getValue(ButtonBlock.FACING) == Direction.WEST && targetState.getValue(ButtonBlock.FACING) == Direction.EAST))
				return true;

			return sourceState.getValue(ButtonBlock.FACING) == targetState.getValue(ButtonBlock.FACING);
		}

		return sourceState.equals(targetState);
	}

	public static int getStateComplexity(BlockState state) {
		var block = state.getBlock();
		if(state.isAir())
			return 0;
		if(block instanceof StairBlock)
			return 2;
		if(block instanceof ButtonBlock)
			return 2;
		if(block instanceof ChainBlock)
			return 2;
		if(block instanceof VineBlock)
			return 3;
		if(block instanceof MultifaceSpreadeableBlock)
			return 3;
		return 1;
	}

	public static int getBuildComplexity(CachedBlocks build) {
		int complexity = 0;
		var blockList = new ArrayList<Block>();
		for(var pos : build.positions()) {
			var state = build.state(pos);
			if(state.is(BRTags.IGNORED_IN_COMPARISON)) {
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
