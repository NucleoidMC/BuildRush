package fr.hugman.build_rush.registry;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;

public class BRRegistries {
	public static final ReloadableResourceManager<Build> BUILD = ReloadableResourceManager.of(Build.CODEC, "builds");

	public static void register() {
		BUILD.register(BuildRush.id("build"));
	}
}
