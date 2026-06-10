package fr.hugman.build_rush.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.stimuli.event.StimulusEvent;

/**
 * Called when a block is broken in a {@link Level}.
 *
 * <p>Upon return:
 * <ul>
 * <li>{@link InteractionResult#SUCCESS} cancels further processing and allows the break.
 * <li>{@link InteractionResult#FAIL} cancels further processing and cancels the break.
 * <li>{@link InteractionResult#PASS} moves on to the next listener.</ul>
 * <p>
 * If all listeners return {@link InteractionResult#PASS}, the break succeeds and proceeds with normal logic.
 */
public interface WorldBlockBreakEvent {
	StimulusEvent<WorldBlockBreakEvent> EVENT = StimulusEvent.create(WorldBlockBreakEvent.class, ctx -> (pos, drop, breakingEntity, maxUpdateDepth) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onBreakBlock(pos, drop, breakingEntity, maxUpdateDepth);
				if(result != InteractionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return InteractionResult.PASS;
	});

	InteractionResult onBreakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth);
}