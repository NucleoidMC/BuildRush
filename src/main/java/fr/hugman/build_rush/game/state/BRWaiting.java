package fr.hugman.build_rush.game.state;

import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.plot.PlotStructure;
import fr.hugman.build_rush.registry.BRRegistries;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
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
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
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
			var worldConfig = new RuntimeWorldConfig().setGenerator(chunkGenerator)
					.setGameRule(GameRules.DO_FIRE_TICK, false)
					.setGameRule(GameRules.FIRE_DAMAGE, false)
					.setGameRule(GameRules.FREEZE_DAMAGE, false)
					.setGameRule(GameRules.DO_MOB_GRIEFING, false)
					.setGameRule(GameRules.DO_MOB_SPAWNING, false)
					.setGameRule(GameRules.RANDOM_TICK_SPEED, 0)
					.setGameRule(GameRules.WATER_SOURCE_CONVERSION, false)
					.setGameRule(GameRules.LAVA_SOURCE_CONVERSION, false);

			var centerRegion = template.getMetadata().getFirstRegion("center");
			if(centerRegion == null) {
				throw new GameOpenException(Text.translatable("error.build_rush.region.center.not_found"));
			}

			return context.openWithWorld(worldConfig, (activity, world) -> {
				GameWaitingLobby.addTo(activity, config.playerConfig());

				StructureTemplateManager templateManager = world.getStructureTemplateManager();

				var centerBounds = centerRegion.getBounds();
				var platform = templateManager.getTemplate(config.map().platform()).orElseThrow(() -> new GameOpenException(Text.translatable("error.build_rush.platform.not_found", config.map().platform())));
				var plotGround = templateManager.getTemplate(config.map().plotGround()).orElseThrow(() -> new GameOpenException(Text.translatable("error.build_rush.plot_ground.not_found", config.map().plotGround())));
				if(plotGround.getSize().getX() != plotGround.getSize().getZ()) {
					throw new GameOpenException(Text.translatable("error.build_rush.plot_ground.invalid_width_length", plotGround.getSize().getX(), plotGround.getSize().getZ()));
				}
				if(plotGround.getSize().getY() != 1) {
					throw new GameOpenException(Text.translatable("error.build_rush.plot_ground.invalid_height", plotGround.getSize().getY()));
				}
				int plotSize = plotGround.getSize().getX();
				var plotStructures = getPlotStructures(plotSize, config, templateManager);
				var centerPlot = getCenterPlot(centerRegion, config.map().centerPlotOffset(), plotSize);
				var spawnPos = BlockPos.ofFloored(centerBounds.center());

				activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
					if(source.isOf(DamageTypes.OUT_OF_WORLD)) {
						resetPlayer(player, world, spawnPos);
					}
					return ActionResult.FAIL;
				});
				activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
					resetPlayer(player, world, spawnPos);
					return ActionResult.FAIL;
				});

				activity.listen(GamePlayerEvents.OFFER, offer -> offer.accept(world, centerBounds.center().withAxis(Direction.Axis.Y, world.getTopY())).and(() -> resetPlayer(offer.player(), world, spawnPos)));

				activity.listen(GameActivityEvents.REQUEST_START, () -> {
					var active = new BRActive(config, activity.getGameSpace(), world, centerBounds, centerPlot, platform, plotGround, plotStructures);
					return active.transferActivity();
				});
			});
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void resetPlayer(ServerPlayerEntity player, World world, BlockPos blockPos) {
		var pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, blockPos).toCenterPos();
		player.teleport(pos.getX(), pos.getY(), pos.getZ());

		player.setHealth(20.0f);
		player.changeGameMode(GameMode.ADVENTURE);
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20.0f);
	}

	public static BlockBounds getCenterPlot(TemplateRegion centerRegion, BlockPos offset, int size) {
		var min = centerRegion.getBounds().min();

		size--;

		var x = min.getX() + offset.getX();
		var y = min.getY() + offset.getY();
		var z = min.getZ() + offset.getZ();

		return BlockBounds.of(x, y, z, x + size, y + size, z + size);
	}


	public static List<PlotStructure> getPlotStructures(int plotSize, BRConfig config, StructureTemplateManager manager) {
		List<PlotStructure> plotStructures = new ArrayList<>();
		for(Identifier plot : config.plotStructures()) {
			// Get the plot structure
			var plotStructure = BRRegistries.PLOT_STRUCTURE.get(plot);

			if(plotStructure == null) {
				throw new GameOpenException(Text.translatable("error.build_rush.plot_structure.not_found", plot.toString()));
			}

			// Verify that the structure is here and is of the correct size
			var structure = manager.getTemplate(plotStructure.id()).orElseThrow(() -> new GameOpenException(Text.translatable("structure_block.load_not_found", plotStructure.toString())));
			if(structure.getSize().getX() != plotSize ||
					structure.getSize().getZ() != plotSize ||
					structure.getSize().getY() < plotSize || structure.getSize().getY() > plotSize + 1) {
				throw new GameOpenException(Text.translatable("error.build_rush.plot_structure.invalid_size", plot.toString(), structure.getSize().getX(), structure.getSize().getY(), structure.getSize().getZ(), plotSize, plotSize, plotSize, plotSize, plotSize, plotSize));
			}
			plotStructures.add(plotStructure);
		}
		if(plotStructures.size() == 0) {
			throw new GameOpenException(Text.translatable("error.build_rush.plot_structure.none"));
		}
		return plotStructures;
	}
}
