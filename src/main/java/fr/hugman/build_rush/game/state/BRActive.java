package fr.hugman.build_rush.game.state;

import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.game.BRPlayerData;
import fr.hugman.build_rush.plot.PlotStructure;
import fr.hugman.build_rush.plot.PlotUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.block.BlockPlaceEvent;
import xyz.nucleoid.stimuli.event.block.BlockPunchEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BRActive {
	private final BRConfig config;
	private final GameSpace space;
	private final ServerWorld world;
	private final BlockBounds center;
	private final BlockBounds centerPlot;
	private final StructureTemplate platform;
	private final StructureTemplate plotGround;
	private final List<PlotStructure> plotStructures;

	private final HashMap<UUID, BRPlayerData> playerDataMap;
	private PlotStructure currentPlotStructure;

	public BRActive(BRConfig config, GameSpace space, ServerWorld world, BlockBounds center, BlockBounds centerPlot, StructureTemplate platform, StructureTemplate plotGround, List<PlotStructure> plotStructures) {
		this.config = config;
		this.space = space;
		this.world = world;
		this.center = center;
		this.centerPlot = centerPlot;
		this.platform = platform;
		this.plotGround = plotGround;
		this.plotStructures = plotStructures;

		this.playerDataMap = new HashMap<>();
		this.currentPlotStructure = null;
	}

	public GameResult transferActivity() {
		this.space.setActivity(activity -> {
			activity.allow(GameRuleType.INTERACTION);

			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.PORTALS);
			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.BREAK_BLOCKS);
			activity.deny(GameRuleType.HUNGER);
			activity.deny(GameRuleType.FALL_DAMAGE);
			activity.deny(GameRuleType.BLOCK_DROPS);
			activity.deny(GameRuleType.THROW_ITEMS);

			var active = new BRActive(config, space, world, center, centerPlot, platform, plotGround, plotStructures);

			activity.listen(GameActivityEvents.ENABLE, active::enable);
			activity.listen(GameActivityEvents.TICK, active::tick);

			//activity.listen(GamePlayerEvents.OFFER, active::offerPlayer);

			activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
				this.resetPlayer(player);
				return ActionResult.FAIL;
			});
			activity.listen(BlockPlaceEvent.BEFORE, active::placeBlock);
			activity.listen(BlockPunchEvent.EVENT, active::punchBlock);
		});

		return GameResult.ok();
	}

	public List<BRPlayerData> getAlivePlayers() {
		return this.playerDataMap.values().stream().filter(p -> !p.eliminated).toList();
	}

	public void pickPlotStructure() {
		this.currentPlotStructure = this.plotStructures.get(this.world.random.nextInt(this.plotStructures.size()));
	}

	public void resetPlayer(ServerPlayerEntity player) {
		var data = playerDataMap.get(player.getUuid());
		boolean spectator = data == null || data.eliminated;

		var pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(spectator ? center.center() : data.plot.center())).toCenterPos();
		player.teleport(pos.getX(), pos.getY(), pos.getZ());

		player.setHealth(20.0f);
		player.changeGameMode(spectator ? GameMode.SPECTATOR : GameMode.SURVIVAL);
		if(!spectator) {
			player.getAbilities().allowFlying = true;
			player.getAbilities().flying = false;
			player.sendAbilitiesUpdate();
		}
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20.0f);
	}

	public void enable() {
		this.pickPlotStructure();
		for(var player : this.space.getPlayers()) {
			this.playerDataMap.put(player.getUuid(), new BRPlayerData());
		}
		if(BuildRush.DEBUG) {
			this.playerDataMap.put(UUID.randomUUID(), new BRPlayerData());
			this.playerDataMap.put(UUID.randomUUID(), new BRPlayerData());
		}
		this.calcPlatformsAndPlots();
		this.placePlayerPlatforms(true);

		for(var player : this.space.getPlayers()) {
			this.resetPlayer(player);
		}
	}

	public void tick() {

	}

	private ActionResult placeBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state, ItemUsageContext itemUsageContext) {
		var data = this.playerDataMap.get(player.getUuid());
		if(data == null || data.eliminated) {
			return ActionResult.FAIL;
		}
		if(data.plot.contains(pos)) {
			return ActionResult.SUCCESS;
		}
		return ActionResult.FAIL;
	}

	private ActionResult punchBlock(ServerPlayerEntity player, Direction direction, BlockPos pos) {
		var data = this.playerDataMap.get(player.getUuid());
		if(data == null || data.eliminated) {
			return ActionResult.FAIL;
		}
		if(data.plot.contains(pos)) {
			var state = this.world.getBlockState(pos);
			var center = pos.toCenterPos();

			this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
			this.world.spawnParticles(ParticleTypes.CRIT, center.getX(), center.getY(), center.getZ(), 5, 0.1D, 0.1D, 0.1D, 0.03D);
			this.world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 0.8f);
			player.giveItemStack(PlotUtil.convert(state));
			return ActionResult.SUCCESS;
		}
		return ActionResult.FAIL;
	}

	public void calcPlatformsAndPlots() {
		var alivePlayers = getAlivePlayers();
		int n = alivePlayers.size();

		//TODO: remove previous platforms here

		var platformSize = this.platform.getSize();
		var plotOffset = this.config.map().plotOffset();
		var plotSize = this.plotGround.getSize().getX();

		int centerSizeX = this.center.max().getX() - this.center.min().getX();
		int centerSizeY = this.center.max().getZ() - this.center.min().getZ();

		// C = (sqrt((A/2)^2 + (B/2)^2) + X/2) / sin(pi/N)
		double r = (Math.sqrt(Math.pow(centerSizeX / 2.0, 2) + Math.pow(centerSizeY / 2.0, 2)) + Math.max(platformSize.getX(), platformSize.getZ()) / 2.0) / Math.sin(Math.PI / n);
		// i owe chat gpt a beer for this one ^
		double thetaStep = 2 * Math.PI / n;

		int index = 0;
		for(var alivePlayer : alivePlayers) {
			double theta = index++ * thetaStep;

			int x = MathHelper.floor(Math.cos(theta) * r);
			int y = center.max().getY() + platformSize.getY() - this.config.map().plotOffset().getY();
			int z = MathHelper.floor(Math.sin(theta) * r);

			int x1 = MathHelper.floor(platformSize.getX() / 2.0);
			int y1 = MathHelper.floor(platformSize.getY() / 2.0);
			int z1 = MathHelper.floor(platformSize.getZ() / 2.0);
			int x2 = platformSize.getX() - x1 - 1;
			int y2 = platformSize.getY() - y1 - 1;
			int z2 = platformSize.getZ() - z1 - 1;

			BlockPos.Mutable minPos = new BlockPos.Mutable(x - x1, y - y1, z - z1);
			BlockPos.Mutable maxPos = new BlockPos.Mutable(x + x2, y + y2, z + z2);

			alivePlayer.platform = BlockBounds.of(minPos, maxPos);

			int xPlot = alivePlayer.platform.min().getX() + plotOffset.getX();
			int yPlot = alivePlayer.platform.min().getY() + plotOffset.getY();
			int zPlot = alivePlayer.platform.min().getZ() + plotOffset.getZ();
			int size = plotSize - 1;

			alivePlayer.plot = BlockBounds.of(xPlot, yPlot, zPlot, xPlot + size, yPlot + size, zPlot + size);
		}
	}

	public void placePlayerPlatforms(boolean spawnPlots) {
		var alivePlayers = getAlivePlayers();

		for(var alivePlayer : alivePlayers) {
			var platformPos = alivePlayer.platform.min();
			this.platform.place(world, platformPos, platformPos, new StructurePlacementData(), this.world.getRandom(), 2);
		}

		if(spawnPlots) {
			var structure = this.world.getStructureTemplateManager().getTemplate(this.currentPlotStructure.id()).orElseThrow();
			boolean shouldPlacePlotGround = structure.getSize().getY() > this.plotGround.getSize().getX();
			for(var alivePlayer : alivePlayers) {
				var plotPos = alivePlayer.plot.min();
				if(shouldPlacePlotGround) {
					plotPos = plotPos.down();
				}
				structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
			}
		}
	}

	public void destroyPlayerPlots() {
		var alivePlayers = getAlivePlayers();
		for(var alivePlayer : alivePlayers) {
			var plot = alivePlayer.plot;
			for(var pos : plot) {
				if(world.getBlockState(pos).getBlock() != Blocks.AIR) {
					this.world.spawnParticles(ParticleTypes.CLOUD, pos.getX(), pos.getY(), pos.getZ(), 10, 0, 0, 0, 1);
					this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	public void placeCenterPlot() {
		var plotPos = this.centerPlot.min();
		var structure = this.world.getStructureTemplateManager().getTemplate(this.currentPlotStructure.id()).orElseThrow();
		boolean shouldPlacePlotGround = structure.getSize().getY() > this.plotGround.getSize().getX();
		if(shouldPlacePlotGround) {
			plotPos = plotPos.down();
		}
		structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
	}

	public void destroyCenterPlot() {
		for(var pos : this.center) {
			if(world.getBlockState(pos).getBlock() != Blocks.AIR) {
				world.addParticle(ParticleTypes.CLOUD, pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0);
				world.setBlockState(pos, Blocks.AIR.getDefaultState());
			}
		}
	}
}
