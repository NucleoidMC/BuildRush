package fr.hugman.build_rush;

import fr.hugman.build_rush.game.state.BRWaiting;
import fr.hugman.build_rush.registry.BRRegistries;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.game.GameType;

public class BuildRush implements ModInitializer {
	public static final boolean DEBUG = false;

	public static final String MOD_ID = "build_rush";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BRRegistries.register();
		GameType.register(BuildRush.id("standard"), BRConfig.CODEC, BRWaiting::open);
	}

	public static Identifier id(String s) {
		return new Identifier(MOD_ID, s);
	}

	public static void debug(String s) {
		if(DEBUG) LOGGER.info(s);
	}
}
