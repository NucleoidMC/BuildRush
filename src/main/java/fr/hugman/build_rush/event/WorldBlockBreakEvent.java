package fr.hugman.build_rush.event;

import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.stimuli.event.StimulusEvent;

/**
 * Called when a block is broken in a {@link World}.
 *
 * <p>Upon return:
 * <ul>
 * <li>{@link ActionResult#SUCCESS} cancels further processing and allows the break.
 * <li>{@link ActionResult#FAIL} cancels further processing and cancels the break.
 * <li>{@link ActionResult#PASS} moves on to the next listener.</ul>
 * <p>
 * If all listeners return {@link ActionResult#PASS}, the break succeeds and proceeds with normal logic.
 */
public interface WorldBlockBreakEvent {
	StimulusEvent<WorldBlockBreakEvent> EVENT = StimulusEvent.create(WorldBlockBreakEvent.class, ctx -> (pos, drop, breakingEntity, maxUpdateDepth) -> {
		try {
			for(var listener : ctx.getListeners()) {
				var result = listener.onBreakBlock(pos, drop, breakingEntity, maxUpdateDepth);
				if(result != ActionResult.PASS) {
					return result;
				}
			}
		} catch(Throwable t) {
			ctx.handleException(t);
		}
		return ActionResult.PASS;
	});

	ActionResult onBreakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth);
}