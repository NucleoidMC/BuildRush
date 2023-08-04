package fr.hugman.build_rush;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.map.BRMapConfig;
import net.minecraft.registry.entry.RegistryEntryList;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

public record BRConfig(PlayerConfig playerConfig, BRMapConfig mapConfig, RegistryEntryList<Build> builds) {
	public static final Codec<BRConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PlayerConfig.CODEC.fieldOf("players").forGetter(BRConfig::playerConfig),
			BRMapConfig.CODEC.fieldOf("map").forGetter(BRConfig::mapConfig),
			Build.LIST_CODEC.fieldOf("builds").forGetter(BRConfig::builds)
	).apply(instance, BRConfig::new));
}
