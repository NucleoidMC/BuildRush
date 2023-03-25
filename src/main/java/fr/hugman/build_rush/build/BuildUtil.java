package fr.hugman.build_rush.build;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.registry.tag.BRTags;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SkullItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BuildUtil {
	public static boolean areEqual(BlockState sourceState, @Nullable NbtCompound sourceNbt, BlockState targetState, @Nullable NbtCompound targetNbt) {
		return sourceState.equals(targetState);
	}

	public static List<ItemStack> stacksForBlock(World world, BlockPos pos) {
		List<ItemStack> stacks = new ArrayList<>();

		var state = world.getBlockState(pos);
		var block = state.getBlock();
		var fluidState = state.getFluidState();
		var fluid = fluidState.getFluid();

		var pickStack = block.getPickStack(world, pos, state);
		stacks.add(pickStack);

		if(block instanceof CandleBlock) {
			pickStack.setCount(state.get(CandleBlock.CANDLES));
			if(state.get(CandleBlock.LIT)) {
				var flintStack = new ItemStack(Items.FLINT_AND_STEEL);
				flintStack.getOrCreateNbt().putBoolean("Unbreakable", true);
				stacks.add(flintStack);
			}
		}
		if(block instanceof SeaPickleBlock) {
			pickStack.setCount(state.get(SeaPickleBlock.PICKLES));
		}
		if(block instanceof VineBlock) {
			int count = 0;
			if(state.get(VineBlock.UP)) count++;
			if(state.get(VineBlock.NORTH)) count++;
			if(state.get(VineBlock.EAST)) count++;
			if(state.get(VineBlock.SOUTH)) count++;
			if(state.get(VineBlock.WEST)) count++;
			pickStack.setCount(count);
		}
		if(block instanceof MultifaceGrowthBlock) {
			pickStack.setCount(MultifaceGrowthBlock.collectDirections(state).size());
		}
		if(block instanceof SlabBlock) {
			pickStack.setCount(state.get(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1);
		}
		if(block instanceof EndPortalFrameBlock) {
			if(state.get(EndPortalFrameBlock.EYE)) {
				stacks.add(new ItemStack(Items.ENDER_EYE));
			}
		}
		if(block instanceof RespawnAnchorBlock) {
			stacks.add(new ItemStack(Items.GLOWSTONE, state.get(RespawnAnchorBlock.CHARGES)));
		}


		if((state.isIn(BlockTags.PORTALS) || state.isIn(BlockTags.FIRE))) {
			pickStack = new ItemStack(Items.FLINT_AND_STEEL);
			pickStack.getOrCreateNbt().putBoolean("Unbreakable", true);
		}
		if(fluid == Fluids.WATER) {
			stacks.add(new ItemStack(Items.WATER_BUCKET));
		}
		if(fluid == Fluids.LAVA) {
			stacks.add(new ItemStack(Items.LAVA_BUCKET));
		}

		if(state.hasBlockEntity()) {
			var blockEntity = world.getBlockEntity(pos);
			if(blockEntity == null) {
				BuildRush.LOGGER.warn("Block entity was null for " + state.getBlock() + " even though the game said it had one");
			}
			else {
				BuildUtil.addBlockEntityNbt(pickStack, blockEntity);
			}
		}
		return stacks;
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

	public static int getBuildComplexity(CachedBuild build) {
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

	public static void addBlockEntityNbt(ItemStack stack, BlockEntity blockEntity) {
		NbtCompound nbtCompound = blockEntity.createNbtWithIdentifyingData();
		BlockItem.setBlockEntityNbt(stack, blockEntity.getType(), nbtCompound);
		if (stack.getItem() instanceof SkullItem && nbtCompound.contains("SkullOwner")) {
			NbtCompound nbtCompound2 = nbtCompound.getCompound("SkullOwner");
			NbtCompound nbtCompound3 = stack.getOrCreateNbt();
			nbtCompound3.put("SkullOwner", nbtCompound2);
			NbtCompound nbtCompound4 = nbtCompound3.getCompound("BlockEntityTag");
			nbtCompound4.remove("SkullOwner");
			nbtCompound4.remove("x");
			nbtCompound4.remove("y");
			nbtCompound4.remove("z");
		}
	}
}
