package fr.hugman.build_rush.mixin;

import fr.hugman.build_rush.event.UseEvents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class AbstractBlockStateMixin {
	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void onUse(Level world, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if(!world.isClientSide()) {
			var events = Stimuli.select();
			try(var invokers = events.forEntityAt(player, hit.getBlockPos())) {
				var state = world.getBlockState(hit.getBlockPos());
				var result = invokers.get(UseEvents.BLOCK).onBlockUsed(state, world, hit.getBlockPos(), player, hit);

				if (result == InteractionResult.FAIL) {
					// notify the client that this action did not go through
					var stack = player.getMainHandItem();
					((ServerPlayer)player).connection.send(new ClientboundContainerSetSlotPacket(0, 0, player.getInventory().getSelectedSlot(), stack));

					cir.setReturnValue(InteractionResult.FAIL);
				}
			}
		}
	}
}
