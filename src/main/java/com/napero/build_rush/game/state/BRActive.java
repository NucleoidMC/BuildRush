package com.napero.build_rush.game.state;

import com.napero.build_rush.BRConfig;
import com.napero.build_rush.game.PlayerData;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;

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
	private final List<StructureTemplate> plotStructures;

	private final HashMap<UUID, PlayerData> playerDataMap;
	private StructureTemplate plotStructure;

	public BRActive(BRConfig config, GameSpace space, ServerWorld world, BlockBounds center, BlockBounds centerPlot, StructureTemplate platform, StructureTemplate plotGround, List<StructureTemplate> plotStructures) {
		this.config = config;
		this.space = space;
		this.world = world;
		this.center = center;
		this.centerPlot = centerPlot;
		this.platform = platform;
		this.plotGround = plotGround;
		this.plotStructures = plotStructures;

		this.playerDataMap = new HashMap<>();
		this.plotStructure = null;
	}

	public GameResult transferActivity() {
		this.space.setActivity(activity -> {
			activity.allow(GameRuleType.INTERACTION);

			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.PORTALS);
			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.HUNGER);
			activity.deny(GameRuleType.FALL_DAMAGE);
			activity.deny(GameRuleType.BLOCK_DROPS);
			activity.deny(GameRuleType.THROW_ITEMS);

			var active = new BRActive(config, space, world, center, centerPlot, platform, plotGround, plotStructures);

			activity.listen(GameActivityEvents.ENABLE, active::enable);
			activity.listen(GameActivityEvents.TICK, active::tick);

			//activity.listen(GamePlayerEvents.OFFER, active::offerPlayer);

			//activity.listen(PlayerDeathEvent.EVENT, active::killPlayer);
			//activity.listen(ItemThrowEvent.EVENT, active::dropItem);
			//activity.listen(BlockPlaceEvent.BEFORE, active::placeBlock);
			//activity.listen(BlockBreakEvent.EVENT, active::breakBlock);
			//activity.listen(BlockUseEvent.EVENT, active::useBlock);
		});

		return GameResult.ok();
	}

	public List<PlayerData> getAlivePlayers() {
		return this.playerDataMap.values().stream().filter(p -> !p.eliminated).toList();
	}

	public void enable() {
		this.pickPlotStructure();
		for(var player : this.space.getPlayers()) {
			this.playerDataMap.putIfAbsent(player.getUuid(), new PlayerData());
		}
		//TODO: remove this
		for(int i = 0; i < 1; i++) {
			this.playerDataMap.putIfAbsent(UUID.randomUUID(), new PlayerData());
		}
		this.calcPlatformsAndPlots();
		this.placePlatforms(true);

		for(var player : this.space.getPlayers()) {
			this.resetPlayer(player);
		}
	}

	public void tick() {

	}

	public void pickPlotStructure() {
		this.plotStructure = this.plotStructures.get(this.world.random.nextInt(this.plotStructures.size()));
	}

	public void resetPlayer(ServerPlayerEntity player) {
		var data = playerDataMap.get(player.getUuid());

		var pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(data == null || data.eliminated ? center.center() : data.plot.center())).toCenterPos();
		player.teleport(pos.getX(), pos.getY(), pos.getZ());

		player.setHealth(20.0f);
		player.changeGameMode(GameMode.ADVENTURE);
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(20.0f);
	}

	public void calcPlatformsAndPlots() {
		var alivePlayers = getAlivePlayers();

		//TODO: remove previous platforms here

		var structureSize = this.platform.getSize();
		var plotOffset = this.config.map().plotOffset();
		var plotSize = this.plotGround.getSize().getX();

		int centerSizeX = this.center.max().getX() - this.center.min().getX();
		int centerSizeY = this.center.max().getZ() - this.center.min().getZ();
		double r = 0.5 * Math.max(Math.max(centerSizeX, centerSizeY), Math.sqrt(alivePlayers.size()) * Math.max(structureSize.getX(), structureSize.getZ()) + this.config.map().platformSpacing());
		// i owe chat gpt a beer for this one ^
		double thetaStep = 2 * Math.PI / alivePlayers.size();

		int index = 0;
		for(var alivePlayer : alivePlayers) {
			double theta = index++ * thetaStep;

			int x = MathHelper.floor(Math.cos(theta) * r);
			int y = center.max().getY() + structureSize.getY() - this.config.map().plotOffset().getY();
			int z = MathHelper.floor(Math.sin(theta) * r);

			int x1 = MathHelper.floor(structureSize.getX() / 2.0);
			int y1 = MathHelper.floor(structureSize.getY() / 2.0);
			int z1 = MathHelper.floor(structureSize.getZ() / 2.0);
			int x2 = structureSize.getX() - x1 - 1;
			int y2 = structureSize.getY() - y1 - 1;
			int z2 = structureSize.getZ() - z1 - 1;

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

	public void placePlatforms(boolean spawnPlots) {
		var alivePlayers = getAlivePlayers();
		for(var alivePlayer : alivePlayers) {
			var platformPos = alivePlayer.platform.min();
			this.platform.place(world, platformPos, platformPos, new StructurePlacementData(), this.world.getRandom(), 2);
		}

		if(spawnPlots) {
			boolean shouldPlacePlotGround = this.plotStructure.getSize().getY() > this.plotGround.getSize().getX();
			for(var alivePlayer : alivePlayers) {
				var plotPos = alivePlayer.plot.min();
				if(shouldPlacePlotGround) {
					plotPos = plotPos.down();
				}
				this.plotStructure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
			}
		}
	}

	public void destroyPlayerPlots() {
		var alivePlayers = getAlivePlayers();
		for(var alivePlayer : alivePlayers) {
			var plot = alivePlayer.plot;
			for(var pos : plot) {
				if(world.getBlockState(pos).getBlock() != Blocks.AIR) {
					world.addParticle(ParticleTypes.CLOUD, pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0);
					world.setBlockState(pos, Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	public void placeCenterPlatform(boolean spawnPlot) {
		var plotPos = this.center.min();
		boolean shouldPlacePlotGround = this.plotStructure.getSize().getY() > this.plotGround.getSize().getX();
		if(shouldPlacePlotGround) {
			plotPos = plotPos.down();
		}
		this.plotStructure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
	}

	public void destroyCenterPlot() {
		var plot = this.center;
		for(var pos : plot) {
			if(world.getBlockState(pos).getBlock() != Blocks.AIR) {
				world.addParticle(ParticleTypes.CLOUD, pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0);
				world.setBlockState(pos, Blocks.AIR.getDefaultState());
			}
		}
	}
}
