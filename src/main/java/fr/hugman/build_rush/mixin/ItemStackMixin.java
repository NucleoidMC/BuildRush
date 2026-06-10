package fr.hugman.build_rush.mixin;

import fr.hugman.build_rush.event.UseEvents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.stimuli.Stimuli;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Inject(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"), cancellable = true)
	private void useOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		var world = context.getLevel();
		if(!context.getLevel().isClientSide()) {
			var events = Stimuli.select();
			var player = context.getPlayer();
			var pos = context.getClickedPos();
			try(var invokers = player == null ? events.at(world,pos) : events.forEntityAt(player, pos)) {
				var result = invokers.get(UseEvents.ITEM_ON_BLOCK).onItemUsedOnBlock((ItemStack) (Object) this, context);

				if (result == InteractionResult.FAIL) {
					// notify the client that this action did not go through
					int slot = context.getHand() == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
					var stack = context.getItemInHand();
					((ServerPlayer)player).connection.send(new ClientboundContainerSetSlotPacket(0, 0, slot, stack));

					cir.setReturnValue(InteractionResult.FAIL);
				}
			}
		}
	}
}
