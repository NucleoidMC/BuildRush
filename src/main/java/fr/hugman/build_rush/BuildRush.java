package fr.hugman.build_rush;

import fr.hugman.build_rush.game.state.BRWaiting;
import fr.hugman.build_rush.registry.BRRegistries;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.game.GameType;

public class BuildRush implements ModInitializer {
	public static final String ID = "build_rush";
	public static final Logger LOGGER = LogManager.getLogger(ID);

	@Override
	public void onInitialize() {
		BRRegistries.register();
		GameType.register(BuildRush.id("standard"), BRConfig.CODEC, BRWaiting::open);
	}

	public static Identifier id(String s) {
		return new Identifier(ID, s);
	}

	public static void debug(String s) {
		if(debug()) LOGGER.info(s);
	}

	public static boolean debug() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}
}
