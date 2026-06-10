package fr.hugman.build_rush.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import xyz.nucleoid.stimuli.event.StimulusEvent;

public class UseEvents {
	public static final StimulusEvent<UseBlockEvent> BLOCK = StimulusEvent.create(UseBlockEvent.class, ctx -> (state, world, pos, player, hit) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onBlockUsed(state, world, pos, player, hit);
				if(result != InteractionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return InteractionResult.PASS;
	});

	public static final StimulusEvent<UseItemOnBlockEvent> ITEM_ON_BLOCK = StimulusEvent.create(UseItemOnBlockEvent.class, ctx -> (stack, context) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onItemUsedOnBlock(stack, context);
				if(result != InteractionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return InteractionResult.PASS;
	});

	public interface UseBlockEvent {
		InteractionResult onBlockUsed(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit);
	}

	public interface UseItemOnBlockEvent {
		InteractionResult onItemUsedOnBlock(ItemStack stack, UseOnContext context);
	}
}
