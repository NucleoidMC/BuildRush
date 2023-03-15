package fr.hugman.build_rush.event;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xyz.nucleoid.stimuli.event.StimulusEvent;

public class UseEvents {
	public static final StimulusEvent<UseBlockEvent> BLOCK = StimulusEvent.create(UseBlockEvent.class, ctx -> (state, world, pos, player, hand, hit) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onBlockUsed(state, world, pos, player, hand, hit);
				if(result != ActionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return ActionResult.PASS;
	});

	public static final StimulusEvent<UseItemOnBlockEvent> ITEM_ON_BLOCK = StimulusEvent.create(UseItemOnBlockEvent.class, ctx -> (stack, context) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onItemUsedOnBlock(stack, context);
				if(result != ActionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return ActionResult.PASS;
	});

	public interface UseBlockEvent {
		ActionResult onBlockUsed(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit);
	}

	public interface UseItemOnBlockEvent {
		ActionResult onItemUsedOnBlock(ItemStack stack, ItemUsageContext context);
	}
}
