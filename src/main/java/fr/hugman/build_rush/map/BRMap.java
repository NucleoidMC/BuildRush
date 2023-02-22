package fr.hugman.build_rush.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.codec.BRCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;

public record BRMap(Identifier template, Identifier platform, Identifier plotGround, BlockPos centerPlotOffset, BlockPos plotOffset, int platformSpacing) {
	public static final Codec<BRMap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.fieldOf("template").forGetter(BRMap::template),
			Identifier.CODEC.fieldOf("platform").forGetter(BRMap::platform),
			Identifier.CODEC.fieldOf("plot_ground").forGetter(BRMap::plotGround),
			BRCodecs.BLOCK_POS.fieldOf("center_plot_offset").forGetter(BRMap::centerPlotOffset),
			BRCodecs.BLOCK_POS.fieldOf("platform_plot_offset").forGetter(BRMap::plotOffset),
			Codecs.NONNEGATIVE_INT.optionalFieldOf("platform_spacing", 0).forGetter(BRMap::platformSpacing)
	).apply(instance, BRMap::new));


}
