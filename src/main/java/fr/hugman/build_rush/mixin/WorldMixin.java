package fr.hugman.build_rush.mixin;

import fr.hugman.build_rush.event.WorldBlockBreakEvent;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(World.class)
public class WorldMixin {
	@Inject(
			method = "breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void onBreakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
		var world = (World) (Object) this;
		var events = Stimuli.select();

		try(var invokers = breakingEntity != null ? events.forEntityAt(breakingEntity, pos) : events.at(world, pos)) {
			var result = invokers.get(WorldBlockBreakEvent.EVENT).onBreakBlock(pos, drop, breakingEntity, maxUpdateDepth);
			if(result == ActionResult.FAIL) {
				cir.setReturnValue(false);
			}
		}
	}
}
