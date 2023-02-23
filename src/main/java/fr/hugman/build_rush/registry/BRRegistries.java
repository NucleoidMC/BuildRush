package fr.hugman.build_rush.registry;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.plot.PlotStructure;

public class BRRegistries {
	public static final ReloadableResourceManager<PlotStructure> PLOT_STRUCTURE = ReloadableResourceManager.of(PlotStructure.CODEC, "plot_structures");

	public static void register() {
		PLOT_STRUCTURE.register(BuildRush.id("plot_structure"));
	}
}
