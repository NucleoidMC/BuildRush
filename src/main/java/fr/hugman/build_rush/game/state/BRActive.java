package fr.hugman.build_rush.game.state;

import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.game.BRPlayerData;
import fr.hugman.build_rush.game.BRRound;
import fr.hugman.build_rush.plot.PlotStructure;
import fr.hugman.build_rush.plot.PlotUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.block.BlockPlaceEvent;
import xyz.nucleoid.stimuli.event.block.BlockPunchEvent;
import xyz.nucleoid.stimuli.event.block.FluidPlaceEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.ArrayList;
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
	private final List<PlotStructure> usedPlotStructures;

	private final HashMap<UUID, BRPlayerData> playerDataMap;
	private PlotStructure currentPlotStructure;
	private final List<ItemStack> inventory;

	private int tick;
	private final BRRound round;
	private boolean canBuild;

	public BRActive(BRConfig config, GameSpace space, ServerWorld world, BlockBounds center, BlockBounds centerPlot, StructureTemplate platform, StructureTemplate plotGround, List<PlotStructure> plotStructures) {
		this.config = config;
		this.space = space;
		this.world = world;
		this.center = center;
		this.centerPlot = centerPlot;
		this.platform = platform;
		this.plotGround = plotGround;
		this.plotStructures = plotStructures;
		this.usedPlotStructures = new ArrayList<>();

		this.playerDataMap = new HashMap<>();
		this.currentPlotStructure = null;
		this.inventory = new ArrayList<>();

		this.round = new BRRound(this, 20, 60);
		this.tick = 0;
		this.canBuild = false;
	}

	public GameResult transferActivity() {
		this.space.setActivity(activity -> {
			activity.deny(GameRuleType.BREAK_BLOCKS);
			activity.deny(GameRuleType.FIRE_TICK);

			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.HUNGER);
			activity.deny(GameRuleType.FALL_DAMAGE);

			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.PORTALS);
			activity.deny(GameRuleType.BLOCK_DROPS);
			activity.deny(GameRuleType.THROW_ITEMS);
			activity.deny(GameRuleType.PICKUP_ITEMS);

			activity.listen(GameActivityEvents.ENABLE, this::enable);
			activity.listen(GameActivityEvents.TICK, this::tick);

			//activity.listen(GamePlayerEvents.OFFER, active::offerPlayer);

			activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
				if(source.isOutOfWorld()) {
					this.resetPlayer(player);
				}
				return ActionResult.FAIL;
			});
			activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
				this.resetPlayer(player);
				return ActionResult.FAIL;
			});
			activity.listen(BlockPlaceEvent.BEFORE, this::placeBlock);
			activity.listen(FluidPlaceEvent.EVENT, this::placeFluid);
			activity.listen(BlockPunchEvent.EVENT, this::punchBlock);
		});

		return GameResult.ok();
	}

	/*=========*/
	/*  LOGIC  */
	/*=========*/

	public void enable() {
		for(var player : this.space.getPlayers()) {
			this.playerDataMap.put(player.getUuid(), new BRPlayerData());
		}
		if(BuildRush.DEBUG) {
			this.playerDataMap.put(UUID.randomUUID(), new BRPlayerData());
			this.playerDataMap.put(UUID.randomUUID(), new BRPlayerData());
		}
		this.calcPlatformsAndPlots();
		this.placeAlivePlayerPlatforms();

		for(var player : this.space.getPlayers()) {
			this.resetPlayer(player);
		}
	}

	public void tick() {
		this.tick++;
		this.round.tick();
		for(var playerData : this.playerDataMap.values()) {
			playerData.tick();
		}
		if(this.tick % 20 == 0) {
			// update sidebar
		}
	}

	public void canBuild(boolean canBuild) {
		this.canBuild = canBuild;
	}

	public void eliminateLastPlayer() {
		//TODO
	}

	public void calcInventory() {
		this.inventory.clear();
		for(var pos : this.centerPlot) {
			var stack = PlotUtil.stackForBlock(world, pos);
			this.inventory.add(stack);
		}
	}

	public void giveInventory() {
		for(var player : this.space.getPlayers()) {
			var data = this.playerDataMap.get(player.getUuid());
			if(data == null || data.eliminated) {
				continue;
			}
			for(var stack : inventory) {
				this.give(player, stack);
			}
		}
	}

	public void clearInventory() {
		for(var player : this.space.getPlayers()) {
			var data = this.playerDataMap.get(player.getUuid());
			if(data == null || data.eliminated) {
				continue;
			}
			player.getInventory().clear();
		}
	}

	public void giveBlock(PlayerEntity player, BlockPos pos) {
		this.give(player, PlotUtil.stackForBlock(world, pos));
	}

	public void give(PlayerEntity player, ItemStack stack) {
		if(stack.isOf(Items.FLINT_AND_STEEL)) {
			// only give it if they don't have it already
			for(var item : player.getInventory().main) {
				if(item.isOf(Items.FLINT_AND_STEEL)) {
					return;
				}
			}
			if(player.getInventory().offHand.get(0).isOf(Items.FLINT_AND_STEEL)) {
				return;
			}
		}

		if(stack.isOf(Items.WATER_BUCKET)) {
			// only give it if they don't have it already
			for(var item : player.getInventory().main) {
				if(item.isOf(Items.WATER_BUCKET)) {
					return;
				}
			}
		}
		player.giveItemStack(stack.copy());
	}

	/*=============*/
	/*  LISTENERS  */
	/*=============*/

	private ActionResult placeBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state, ItemUsageContext itemUsageContext) {
		var data = this.playerDataMap.get(player.getUuid());
		if(data == null || data.eliminated) {
			return ActionResult.FAIL;
		}
		if(this.canBuild && data.plot.contains(pos)) {
			return ActionResult.SUCCESS;
		}
		return ActionResult.FAIL;
	}


	private ActionResult placeFluid(ServerWorld world, BlockPos pos, @Nullable ServerPlayerEntity player, @Nullable BlockHitResult blockHitResult) {
		if(player == null) {
			return ActionResult.FAIL;
		}
		return placeBlock(player, world, pos, world.getBlockState(pos), null);

	}

	private ActionResult punchBlock(ServerPlayerEntity player, Direction direction, BlockPos pos) {
		var data = this.playerDataMap.get(player.getUuid());
		if(data == null || data.eliminated) {
			return ActionResult.FAIL;
		}
		if(this.canBuild && data.plot.contains(pos)) {
			/*
			  This currently doesn't work very well, so I'm disabling it for now
			  If you hold the click it won't break the second block if you're still holding the click
			if(data.breakingCooldown > 0) {
				return ActionResult.FAIL;
			}
			 */
			var state = this.world.getBlockState(pos);
			var center = pos.toCenterPos();

			this.giveBlock(player, pos);
			this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
			this.world.spawnParticles(ParticleTypes.CRIT, center.getX(), center.getY(), center.getZ(), 5, 0.1D, 0.1D, 0.1D, 0.03D);
			this.world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0f, 0.8f);
			data.breakingCooldown = BRPlayerData.BREAKING_COOLDOWN;
			return ActionResult.SUCCESS;
		}
		return ActionResult.FAIL;
	}

	/*===========*/
	/*  UTILITY  */
	/*===========*/

	public void sendMessage(String text) {
		this.space.getPlayers().sendMessage(Text.literal(text));
	}

	public List<BRPlayerData> getAliveDatas() {
		return this.playerDataMap.values().stream().filter(p -> !p.eliminated).toList();
	}

	public void pickPlotStructure() {
		if(this.plotStructures.isEmpty()) {
			this.plotStructures.addAll(this.usedPlotStructures);
			this.usedPlotStructures.clear();
		}
		if(this.plotStructures.isEmpty()) {
			throw new GameOpenException(Text.translatable("error.build_rush.plot_structure.none.weird"));
		}
		this.currentPlotStructure = this.plotStructures.get(this.world.random.nextInt(this.plotStructures.size()));
		this.plotStructures.remove(this.currentPlotStructure);
		this.usedPlotStructures.add(this.currentPlotStructure);
	}

	public void resetAlivePlayers() {
		for(var player : this.space.getPlayers()) {
			var data = this.playerDataMap.get(player.getUuid());
			if(data != null && !data.eliminated) {
				this.resetPlayer(player);
			}
		}
	}

	public void resetPlayer(ServerPlayerEntity player) {
		var data = playerDataMap.get(player.getUuid());
		boolean spectator = data == null || data.eliminated;

		if(spectator) {
			var pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(center.center())).toCenterPos();
			player.teleport(pos.getX(), pos.getY(), pos.getZ());
		}
		else {
			Vec3d pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(data.plot.center()).add(0, 0, data.plot.size().getZ())).toCenterPos();
			for(int i = 5; i > 0; i--) {
				var newPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(data.plot.center().add(0, 0, i)));
				if(world.getBlockState(newPos.down()).hasSolidTopSurface(world, newPos.down(), player)) {
					pos = newPos.toCenterPos();
					break;
				}
			}
			player.teleport(pos.getX(), pos.getY(), pos.getZ());
		}

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

	public void removeBlock(BlockPos pos) {
		if(!world.getBlockState(pos).isAir()) {
			var particlePos = pos.toCenterPos();
			this.world.spawnParticles(ParticleTypes.CLOUD, particlePos.getX(), particlePos.getY(), particlePos.getZ(), 2, 0.5, 0.5, 0.5, 0.1D);
			this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
		}
	}

	public void calcPlatformsAndPlots() {
		var aliveDatas = getAliveDatas();
		int n = aliveDatas.size();

		var platformSize = this.platform.getSize();
		var plotOffset = this.config.map().plotOffset();
		var plotSize = this.plotGround.getSize().getX();

		int centerSizeX = this.center.max().getX() - this.center.min().getX();
		int centerSizeY = this.center.max().getZ() - this.center.min().getZ();

		// C = (sqrt((A/2)^2 + (B/2)^2) + X/2) / sin(pi/N)
		double r = (Math.sqrt(Math.pow(centerSizeX / 2.0, 2) + Math.pow(centerSizeY / 2.0, 2)) + (Math.max(platformSize.getX(), platformSize.getZ()) / 2.0) + this.config.map().platformSpacing()) / Math.sin(Math.PI / n);
		// I owe chat gpt a beer for this one ^
		double thetaStep = 2 * Math.PI / n;

		int index = 0;
		for(var aliveData : aliveDatas) {
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

			aliveData.platform = BlockBounds.of(minPos, maxPos);

			int xPlot = aliveData.platform.min().getX() + plotOffset.getX();
			int yPlot = aliveData.platform.min().getY() + plotOffset.getY();
			int zPlot = aliveData.platform.min().getZ() + plotOffset.getZ();
			int size = plotSize - 1;

			aliveData.plot = BlockBounds.of(xPlot, yPlot, zPlot, xPlot + size, yPlot + size, zPlot + size);
		}
	}

	public void placeAlivePlayerPlatforms() {
		var aliveDatas = getAliveDatas();

		for(var aliveData : aliveDatas) {
			var platformPos = aliveData.platform.min();
			this.platform.place(world, platformPos, platformPos, new StructurePlacementData(), this.world.getRandom(), 2);
			BlockBounds barrier = BlockBounds.of(aliveData.plot.min().add(0, -2, 0), aliveData.plot.max().add(0, -2, 0));
			barrier.forEach(pos -> {
				if(world.getBlockState(pos).isAir()) {
					world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
				}
			});
		}
	}

	public void placeAlivePlayerPlots() {
		var aliveDatas = getAliveDatas();

		var structure = this.world.getStructureTemplateManager().getTemplate(this.currentPlotStructure.id()).orElseThrow();
		boolean shouldPlacePlotGround = structure.getSize().getY() > this.plotGround.getSize().getX();
		for(var aliveData : aliveDatas) {
			this.world.playSound(null, new BlockPos(aliveData.plot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 0.9f);
			var plotPos = aliveData.plot.min();
			if(shouldPlacePlotGround) {
				plotPos = plotPos.down();
			}
			structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
		}

		// if the player is inside a block, teleport them on top
		for(var uuid : this.playerDataMap.keySet()) {
			var player = this.world.getPlayerByUuid(uuid);
			if(player instanceof ServerPlayerEntity serverPlayer) {
				if(!this.world.getBlockState(player.getBlockPos()).isAir() || !this.world.getBlockState(player.getBlockPos().up()).isAir()) {
					this.resetPlayer(serverPlayer);
				}
			}
		}
	}

	public void placeAlivePlayerPlotGrounds() {
		var aliveDatas = getAliveDatas();

		for(var aliveData : aliveDatas) {
			var plotPos = aliveData.plot.min().down();
			this.plotGround.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
		}
	}

	public void removeAlivePlayerPlots() {
		var aliveDatas = getAliveDatas();
		for(var aliveData : aliveDatas) {
			var plot = aliveData.plot;
			this.world.playSound(null, new BlockPos(aliveData.plot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 1.1f);
			for(var pos : plot) {
				this.removeBlock(pos);
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
		this.world.playSound(null, new BlockPos(this.centerPlot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 0.9f);
		structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
		this.calcInventory();
	}

	public void placeCenterPlotGround() {
		var plotPos = this.centerPlot.min().down();
		this.plotGround.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
	}

	public void removeCenterPlot() {
		this.world.playSound(null, new BlockPos(this.centerPlot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 1.1f);
		for(var pos : this.centerPlot) {
			this.removeBlock(pos);
		}
	}
}
