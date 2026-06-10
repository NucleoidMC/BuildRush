package fr.hugman.build_rush.mixin;

import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CandleCakeBlock.class)
public interface CandleCakeBlockAccessor {
    @Accessor("candleBlock")
    CandleBlock buildrush$getCandle();
}
