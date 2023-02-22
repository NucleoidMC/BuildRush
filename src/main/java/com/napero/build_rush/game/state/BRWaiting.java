package com.napero.build_rush.game.state;

import com.napero.build_rush.BRConfig;
import com.napero.build_rush.BuildRush;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.map_templates.TemplateRegion;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BRWaiting {
	public static GameOpenProcedure open(GameOpenContext<BRConfig> context) {
		BRConfig config = context.config();

		try {
			var template = MapTemplateSerializer.loadFromResource(context.server(), config.map().template());
			var chunkGenerator = new TemplateChunkGenerator(context.server(), template);
			var worldConfig = new RuntimeWorldConfig().setGenerator(chunkGenerator);

			var centerRegion = template.getMetadata().getFirstRegion("center");
			if(centerRegion == null) {
				throw new IllegalStateException("Map does not have a center region");
			}

			return context.openWithWorld(worldConfig, (activity, world) -> {
				GameWaitingLobby.addTo(activity, config.playerConfig());

				StructureTemplateManager templateManager = world.getStructureTemplateManager();

				var centerBounds = centerRegion.getBounds();
				var platform = templateManager.getTemplate(config.map().platform()).orElseThrow(() -> new IllegalStateException("Platform structure not found"));
				var plotGround = templateManager.getTemplate(config.map().plotGround()).orElseThrow(() -> new IllegalStateException("Plot ground structure not found"));
				if(plotGround.getSize().getX() != plotGround.getSize().getZ()) {
					throw new GameOpenException(Text.literal("Plot ground structure must be square"));
				}
				if(plotGround.getSize().getY() != 1) {
					throw new GameOpenException(Text.literal("Plot ground structure must be 1 block high"));
				}
				int plotSize = plotGround.getSize().getX();
				BuildRush.debug("Plot ground structure size: " + plotSize);
				var plotStructures = getPlotStructures(plotSize, config, templateManager);
				var centerPlot = getCenterPlot(centerRegion, config.map().centerPlotOffset(), plotSize);

				activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
					resetPlayer(player);
					var pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(centerBounds.center())).toCenterPos();
					player.teleport(pos.getX(), pos.getY(), pos.getZ());
					return ActionResult.FAIL;
				});

				activity.listen(GamePlayerEvents.OFFER, offer ->
						offer.accept(world, world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(centerBounds.center())).toCenterPos()).and(() ->
								resetPlayer(offer.player())));

				activity.listen(GameActivityEvents.REQUEST_START, () -> {
					var active = new BRActive(config, activity.getGameSpace(), world, centerBounds, centerPlot, platform, plotGround, plotStructures);
					return active.transferActivity();
				});
			});
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void resetPlayer(ServerPlayerEntity player) {
		player.setHealth(20.0f);
		player.changeGameMode(GameMode.ADVENTURE);
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20.0f);
	}

	public static BlockBounds getCenterPlot(TemplateRegion centerRegion, BlockPos offset, int size) {
		var min = centerRegion.getBounds().min();

		var x = min.getX() + offset.getX();
		var y = min.getY() + offset.getY();
		var z = min.getZ() + offset.getZ();

		return BlockBounds.of(x, y, z, x + size, y + size, z + size);
	}


	public static List<StructureTemplate> getPlotStructures(int plotSize, BRConfig config, StructureTemplateManager manager) {
		List<StructureTemplate> plotStructures = new ArrayList<>();
		for(Identifier plot : config.structures()) {
			// Get the structure
			var structure = manager.getTemplate(plot).orElseThrow(() -> new IllegalStateException("Could not find structure: " + plot.toString()));

			// Verify that the structure is the correct size
			if(structure.getSize().getX() != plotSize ||
					structure.getSize().getZ() != plotSize ||
					structure.getSize().getY() < plotSize || structure.getSize().getY() > plotSize + 1) {
				throw new IllegalStateException("Structure: " + plot.toString() + " is not the correct size! (Expected " +
						plotSize + "," + plotSize + "," + plotSize + ", or " +
						plotSize + "," + (plotSize + 1) + "," + plotSize + " got " +
						structure.getSize().getX() + "," + structure.getSize().getY() + "," + structure.getSize().getZ() + ")");
			}
			plotStructures.add(structure);
		}
		if(plotStructures.size() == 0) {
			throw new IllegalStateException("No structures were found!");
		}
		return plotStructures;
	}
}
