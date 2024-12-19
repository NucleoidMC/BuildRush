package fr.hugman.build_rush.mixin;

import fr.hugman.build_rush.event.UseEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin {
	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void onUse(World world, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		if(!world.isClient()) {
			var events = Stimuli.select();
			try(var invokers = events.forEntityAt(player, hit.getBlockPos())) {
				var state = world.getBlockState(hit.getBlockPos());
				var result = invokers.get(UseEvents.BLOCK).onBlockUsed(state, world, hit.getBlockPos(), player, hit);

				if (result == ActionResult.FAIL) {
					// notify the client that this action did not go through
					var stack = player.getMainHandStack();
					((ServerPlayerEntity)player).networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(0, 0, player.getInventory().selectedSlot, stack));

					cir.setReturnValue(ActionResult.FAIL);
				}
			}
		}
	}
}
