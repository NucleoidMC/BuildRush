package fr.hugman.build_rush.mixin;

import fr.hugman.build_rush.event.UseEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockMixin {
	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void onUse(World world, PlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		if(!world.isClient()) {
			var events = Stimuli.select();
			try(var invokers = events.forEntityAt(player, hit.getBlockPos())) {
				var state = world.getBlockState(hit.getBlockPos());
				var result = invokers.get(UseEvents.USE_BLOCK).onBlockUsed(state, world, hit.getBlockPos(), player, hand, hit);
				if(result != ActionResult.PASS) {
					cir.setReturnValue(result);
				}
			}
		}
	}
}
