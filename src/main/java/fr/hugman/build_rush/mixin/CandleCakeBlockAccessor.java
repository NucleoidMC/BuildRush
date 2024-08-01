package fr.hugman.build_rush.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.CandleCakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CandleCakeBlock.class)
public interface CandleCakeBlockAccessor {
	@Accessor("candle")
	Block buildrush$getCandle();
}
