package fr.hugman.build_rush;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.map.BRMapConfig;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;

import java.util.Optional;
import net.minecraft.core.HolderSet;

public record BRConfig(WaitingLobbyConfig playerConfig, BRMapConfig mapConfig, Optional<HolderSet<Build>> builds) {
	public static final MapCodec<BRConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(BRConfig::playerConfig),
			BRMapConfig.CODEC.fieldOf("map").forGetter(BRConfig::mapConfig),
			Build.LIST_CODEC.optionalFieldOf("builds").forGetter(BRConfig::builds)
	).apply(instance, BRConfig::new));
}
