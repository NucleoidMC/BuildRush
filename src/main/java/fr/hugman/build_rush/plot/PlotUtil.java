package fr.hugman.build_rush.plot;

import fr.hugman.build_rush.BuildRush;
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
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.ArrayList;
import java.util.List;

public class PlotUtil {
	public static boolean areEqual(World world, BlockPos sourcePos, BlockPos targetPos) {
		var sourceState = world.getBlockState(sourcePos);
		var targetState = world.getBlockState(targetPos);
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
				PlotUtil.addBlockEntityNbt(pickStack, blockEntity);
			}
		}
		return stacks;
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

	/**
	 * Compares a structure template to the blocks present in certain bounds.
	 * <p>+2 for each block that perfectly matches<br>
	 * +1 for each block that is the correct block, but not the same state
	 *
	 * @param world
	 * @param original the original structure template
	 * @param bounds the bounds to compare to
	 * @return a double between 0 and 1, representing the percentage of blocks that match.
	 */
	public static double compare(World world, StructureTemplate original, BlockBounds bounds) {
		return 1.0D;
	}
}
