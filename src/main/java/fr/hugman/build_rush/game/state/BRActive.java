package fr.hugman.build_rush.game.state;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.sidebars.api.Sidebar;
import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.build.BuildUtil;
import fr.hugman.build_rush.event.UseEvents;
import fr.hugman.build_rush.event.WorldBlockBreakEvent;
import fr.hugman.build_rush.game.Judge;
import fr.hugman.build_rush.game.PlayerData;
import fr.hugman.build_rush.game.RoundManager;
import fr.hugman.build_rush.map.BRMap;
import fr.hugman.build_rush.map.Plot;
import fr.hugman.build_rush.misc.CachedBlocks;
import fr.hugman.build_rush.registry.tag.BRTags;
import fr.hugman.build_rush.song.SongManager;
import fr.hugman.build_rush.statistics.BRStatistics;
import fr.hugman.build_rush.text.TextUtil;
import net.minecraft.block.*;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.game.stats.GameStatisticBundle;
import xyz.nucleoid.stimuli.event.block.BlockPlaceEvent;
import xyz.nucleoid.stimuli.event.block.BlockPunchEvent;
import xyz.nucleoid.stimuli.event.block.FluidPlaceEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BRActive {
    private final ServerWorld world;
    private final GameSpace space;
    private final BRConfig config;

    private final int size;
    private final Plot centerPlot;
    private final HashMap<UUID, PlayerData> playerDataMap;

    private final List<Build> builds;
    private final List<Build> usedBuilds;
    private Build currentBuild;
    private CachedBlocks cachedBuild;
    private final List<ItemStack> buildItems;
    private boolean canInteractWithWorld;

    private int maxScore;
    private int perfectRoundsInARow;
    private UUID loserUuid;

    private long tick;
    private long closeTick;
    private long closeTicks;
    private boolean shouldClose;
    private final RoundManager roundManager;

    public final Sidebar sidebar;

    public final Judge judge;

    public final SongManager songManager;
    public final GameStatisticBundle statistics;

    public BRActive(ServerWorld world, GameSpace space, BRConfig config, int size, Plot centerPlot, List<Build> builds, SongManager songManager) {
        this.world = world;
        this.config = config;
        this.space = space;

        this.size = size;
        this.centerPlot = centerPlot;
        this.playerDataMap = new HashMap<>();

        this.builds = builds;
        this.usedBuilds = new ArrayList<>();
        this.currentBuild = null;
        this.buildItems = new ArrayList<>();
        this.canInteractWithWorld = false;

        this.perfectRoundsInARow = 0;

        this.tick = 0;
        this.closeTick = Long.MAX_VALUE;
        this.shouldClose = false;
        this.roundManager = new RoundManager(this, 10, 40);

        this.sidebar = new Sidebar(Sidebar.Priority.MEDIUM);

        this.judge = Judge.of(roundManager, world, this.centerPlot.groundBounds().center().add(0, size + 3, 0));

        this.songManager = songManager;
        this.statistics = space.getStatistics().bundle(BuildRush.ID);
    }

    public static BRActive create(BRConfig config, GameSpace space, ServerWorld world, BRMap map, List<Build> builds) {
        var centerPlot = map.centerPlot();
        var size = centerPlot.buildBounds().size().getX() + 1;

        var songManager = new SongManager(space);

        try {
            songManager.addSongs(
                    BuildRush.id("super_bell_hill"),
                    BuildRush.id("bob_omb_battlefield"),
                    BuildRush.id("nsmb_castle"),
                    BuildRush.id("dire_dire_docks"),
                    BuildRush.id("space_junk_galaxy"),
                    BuildRush.id("mk8_rainbow_road"),
                    BuildRush.id("smm_title"),
                    BuildRush.id("the_grand_finale"),
                    BuildRush.id("gang_plank_galleon"),
                    BuildRush.id("zelda_lullaby"),
                    BuildRush.id("driftveil_city"),
                    BuildRush.id("green_greens"),
                    BuildRush.id("deluge_dirge"),
                    BuildRush.id("spiral_mountain"),
                    BuildRush.id("clanker_cavern"),
                    BuildRush.id("walrus_cove"),
                    BuildRush.id("life_will_change"),
                    BuildRush.id("asgore"),
                    BuildRush.id("fields_of_hopes_and_dreams"),
                    BuildRush.id("rude_buster"),
                    BuildRush.id("menu_ssb4"),
                    BuildRush.id("lifelight"),
                    BuildRush.id("death_wish"),
                    BuildRush.id("rush_hour")
            );
        } catch (IOException e) {
            throw new GameOpenException(Text.of("Could not find songs"), e);
        }

        BRActive active = new BRActive(world, space, config, size, centerPlot, builds, songManager);

        space.setActivity(activity -> {
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
            activity.deny(GameRuleType.CORAL_DEATH);
            activity.deny(GameRuleType.ICE_MELT);

            activity.listen(GameActivityEvents.ENABLE, () -> active.enable(map.plots()));
            activity.listen(GameActivityEvents.TICK, active::tick);
            activity.listen(GameActivityEvents.DESTROY, active::onClose);

            activity.listen(GamePlayerEvents.OFFER, offer -> offer.accept(world, centerPlot.groundBounds().center()));
            activity.listen(GamePlayerEvents.ADD, active::addPlayer);
            activity.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
                if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
                    active.resetPlayer(player, true);
                }
                return ActionResult.FAIL;
            });
            activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
                active.resetPlayer(player, true);
                return ActionResult.FAIL;
            });
            activity.listen(BlockPlaceEvent.BEFORE, (player, world1, pos, state, context) -> active.onWorldInteraction(player, pos));
            activity.listen(BlockPlaceEvent.AFTER, (player, world1, pos, state) -> active.onBlockPlaced(player));
            activity.listen(FluidPlaceEvent.EVENT, (world1, pos, player, hitResult) -> active.onWorldInteraction(player, pos));
            activity.listen(BlockPunchEvent.EVENT, active::punchBlock);
            activity.listen(WorldBlockBreakEvent.EVENT, active::onBlockBroken);
            activity.listen(UseEvents.BLOCK, active::onBlockUsed);
            activity.listen(UseEvents.ITEM_ON_BLOCK, (stack, context) ->
                    active.onWorldInteraction((ServerPlayerEntity) context.getPlayer(), context.getBlockPos().add(context.getSide().getVector()))
            );
        });

        return active;
    }

    /*=========*/
    /*  LOGIC  */
    /*=========*/

    public void enable(List<Plot> plots) {
        var players = this.space.getPlayers();
        var playerCount = BuildRush.debug() ? players.size() + 2 : players.size();
        int i = 0;
        for (var player : players) {
            var data = new PlayerData();

            var plot = plots.get(this.world.random.nextInt(plots.size()));
            data.plot = plot;
            plots.remove(plot);

            data.join(player);
            Vec3d pos = plot.buildBounds().centerTop().add(0, this.config.mapConfig().nametagOffset(), 0);

            data.playerNameHolder = new ElementHolder();
            ChunkAttachment.of(data.playerNameHolder, world, pos);
            data.playerNameElement = new TextDisplayElement(player == null ? Text.of("???") : player.getDisplayName());
            data.playerNameElement.setBillboardMode(DisplayEntity.BillboardMode.VERTICAL);
            data.playerNameElement.setScale(new Vector3f(5, 5, 5));

            data.playerNameHolder.addElement(data.playerNameElement);
            data.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);

            this.playerDataMap.put(player.getUuid(), data);
        }
        if (BuildRush.debug()) {
            var data1 = new PlayerData();
            var data2 = new PlayerData();

            var plot1 = plots.get(this.world.random.nextInt(plots.size()));
            plots.remove(plot1);
            var plot2 = plots.get(this.world.random.nextInt(plots.size()));
            plots.remove(plot2);

            data1.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);
            data2.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);
            data1.plot = plot1;
            data2.plot = plot2;

            this.playerDataMap.put(UUID.randomUUID(), data1);
            this.playerDataMap.put(UUID.randomUUID(), data2);
        }
        this.refreshSidebar();
        this.sidebar.show();

        for (var player : this.space.getPlayers()) {
            this.resetPlayer(player, true);
            this.sidebar.addPlayer(player);
        }

        this.songManager.addPlayers(this.space.getPlayers());
        this.songManager.setPlaying(true);
    }

    public void tick() {
        this.tick++;
        if (this.isClosing()) {
            var progress = (float) (this.closeTick - this.tick) / this.closeTicks;
            this.songManager.setVolume((byte) (progress * 100));
            if (this.tick >= this.closeTick) {
                this.space.close(GameCloseReason.FINISHED);
            }
            return;
        }
        this.roundManager.tick();
        this.judge.tick();

        for (var data : this.playerDataMap.values()) {
            data.tick();
        }

        var showCountdown = this.roundManager.getState() == RoundManager.BUILD || this.roundManager.getState() == RoundManager.MEMORIZE;
        var stateTick = this.roundManager.getStateTick();
        var stateTotalTicks = this.roundManager.getLength(this.roundManager.getState());
        var statePercent = (float) stateTick / stateTotalTicks;

        var stateTicksLeft = stateTotalTicks - stateTick;
        var stateMinutes = stateTicksLeft / 20 / 60;
        var stateSeconds = stateTicksLeft / 20 % 60;

        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (!player.isSpectator() && this.canInteractWithWorld) {
                // if the player is in another's safe zone, teleport them back to their own plot
                for (var otherData : this.playerDataMap.values()) {
                    if (otherData != data && otherData.plot != null && otherData.plot.safeZone().contains(player.getBlockPos())) {
                        resetPlayer(player, true);
                        player.sendMessage(TextUtil.translatable(TextUtil.WARNING, TextUtil.DANGER, "text.build_rush.do_not_disturb"));
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.PLAYERS, 1, 1);
                        break;
                    }
                }
            }
            if (data != null) {
                if (!showCountdown) {
                    if (data.bar.isVisible()) {
                        data.bar.setVisible(false);
                    }
                } else {
                    if (!data.bar.isVisible()) {
                        data.bar.setVisible(true);
                    }
                    if (data.score == this.maxScore) {
                        data.bar.setName(Text.translatable("bar.build_rush.perfect_build"));
                        data.bar.setColor(BossBar.Color.GREEN);
                        data.bar.setPercent(1);
                    } else {
                        data.bar.setName(Text.translatable("bar.build_rush.time_left", String.format("%d", stateMinutes), String.format("%02d", stateSeconds)));

                        if (stateTicksLeft % 20 == 0) {
                            if (stateMinutes == 0) {
                                if (stateSeconds >= 30) {
                                    data.bar.setColor(BossBar.Color.GREEN);
                                } else if (stateSeconds >= 15) {
                                    data.bar.setColor(BossBar.Color.YELLOW);
                                } else if (stateSeconds <= 10) {
                                    data.bar.setColor(BossBar.Color.RED);
                                }
                                if (stateSeconds == 30 || stateSeconds == 15 || stateSeconds == 10) {
                                    TextUtil.sendSubtitle(player, Text.literal(String.valueOf(stateSeconds)).setStyle(Style.EMPTY.withColor(Formatting.YELLOW)), 0, 30, 10);
                                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1, 1.3f);
                                }
                                if (stateSeconds <= 5) {
                                    TextUtil.sendSubtitle(player, Text.literal(String.valueOf(stateSeconds)).setStyle(Style.EMPTY.withColor(Formatting.RED)), 0, 20, 0);
                                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1, 1.6f);
                                }
                            } else {
                                data.bar.setColor(BossBar.Color.GREEN);
                            }
                            if (stateSeconds == 0 && (stateMinutes == 1 || stateMinutes == 2)) {
                                TextUtil.sendSubtitle(player, Text.literal(String.valueOf(60)).setStyle(Style.EMPTY.withColor(Formatting.GREEN)), 0, 40, 20);
                                player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 1, 1);
                            }
                        }
                        data.bar.setPercent(statePercent);
                    }
                }
            }
        }

        if (this.tick % 20 == 0) {
            this.refreshSidebar();
        }
    }

    private void startClosing() {
        this.closeTicks = 20 * 10;
        this.closeTick = this.tick + this.closeTicks;
        for (var player : this.space.getPlayers()) {
            player.getInventory().clear();
            this.resetPlayer(player, false);

            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
    }

    private boolean isClosing() {
        return this.closeTick != Long.MAX_VALUE;
    }

    private void onClose(GameCloseReason gameCloseReason) {
        this.sidebar.hide();
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data != null) {
                data.leave(player);
            }
            this.sidebar.removePlayer(player);
        }
        this.songManager.destroy();
    }

    public void canInteract(boolean canBuild) {
        this.canInteractWithWorld = canBuild;
    }

    public void eliminate(PlayerData data) {
        ServerPlayerEntity player = null;
        for (var uuid : this.playerDataMap.keySet()) {
            if (this.playerDataMap.get(uuid) == data) {
                for (var p : this.space.getPlayers()) {
                    if (p.getUuid().equals(uuid)) {
                        player = p;
                        break;
                    }
                }
                break;
            }
        }
        if (data == null) {
            BuildRush.LOGGER.error("Tried to eliminate a player but they have no data!");
            return;
        }
        if (data.eliminated) {
            BuildRush.LOGGER.error("Tried to eliminate a player but they are already eliminated!");
            return;
        }
        float score = data.score / (float) this.maxScore;
        data.score = 0;
        data.eliminated = true;
        data.setNameHologramColor(TextUtil.NEUTRAL_S);
        if (player != null) {
            this.resetPlayer(player, false);
            String scoreAsPercent = String.format("%.2f", score * 100).replaceAll("0*$", "").replaceAll("[,.]$", "");
            for (var p : this.space.getPlayers()) {
                if (p == player) continue;
                p.sendMessage(TextUtil.translatable(TextUtil.SKULL, TextUtil.DANGER, "text.build_rush.eliminated", player.getName().getString(), scoreAsPercent));
            }
            player.sendMessage(TextUtil.translatable(TextUtil.SKULL, TextUtil.DANGER, "text.build_rush.eliminated.self", player.getName().getString()));
            TextUtil.clearSubtitle(player);
            TextUtil.sendTitle(player, TextUtil.translatable(TextUtil.DANGER, "title.build_rush.eliminated"), 0, 5 * 20, 20);
            player.playSound(SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.MASTER, 1, 2f);
        }
        this.refreshSidebar();

        var aliveDatas = this.getAliveDatas();
        if (aliveDatas.size() <= 1) {
            for (var uuid : this.playerDataMap.keySet()) {
                var d = this.playerDataMap.get(uuid);
                if (d != null && !d.eliminated) {
                    var winner = this.space.getPlayers().getEntity(uuid);
                    if (winner == null) {
                        BuildRush.LOGGER.error("Tried to find winner but they were not found in the game!");
                        break;
                    }
                    for (var p : this.space.getPlayers()) {
                        if (p == winner) {
                            p.sendMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.LEGENDARY, "text.build_rush.win.self", this.roundManager.getNumber()));
                        } else {
                            p.sendMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.EPIC, "text.build_rush.win", winner.getName().getString(), this.roundManager.getNumber()));
                        }
                    }
                    shouldClose = true;
                    return;
                }
            }
            this.space.getPlayers().sendMessage(TextUtil.translatable(TextUtil.FLAG, TextUtil.EPIC, "text.build_rush.win.unknown", this.roundManager.getNumber()));
            shouldClose = true;
        }
    }

    public void giveInventory() {
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data == null || data.eliminated) {
                continue;
            }
            for (var stack : buildItems) {
                this.give(player, stack, false);
            }
        }
    }

    public void giveBlock(PlayerEntity player, BlockPos pos) {
        var stacks = BuildUtil.stacksForBlock(world, pos);
        if (stacks.isEmpty()) {
            return;
        }
        var firstStack = stacks.get(0);

        this.give(player, firstStack, true);
        for (int i = 1; i < stacks.size(); i++) {
            this.give(player, stacks.get(i), false);
        }
    }

    public void give(PlayerEntity player, ItemStack stack, boolean giveToHand) {
        if (stack.isOf(Items.FLINT_AND_STEEL)) {
            // only give it if they don't have it already
            for (var item : player.getInventory().main) {
                if (item.isOf(Items.FLINT_AND_STEEL)) {
                    return;
                }
            }
            if (player.getInventory().offHand.get(0).isOf(Items.FLINT_AND_STEEL)) {
                return;
            }
        }

        if (stack.isOf(Items.WATER_BUCKET)) {
            // only give it if they don't have it already
            for (var item : player.getInventory().main) {
                if (item.isOf(Items.WATER_BUCKET)) {
                    return;
                }
            }
        }
        if (giveToHand) {
            var slot = player.getInventory().selectedSlot;
            var oldStack = player.getInventory().getStack(slot);
            if (oldStack.isEmpty()) {
                player.getInventory().setStack(slot, stack.copy());
            } else {
                player.giveItemStack(stack.copy());
            }
        } else {
            player.giveItemStack(stack.copy());
        }
    }

    public void clearInventory() {
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data == null || data.eliminated) {
                continue;
            }
            player.getInventory().clear();
        }
    }

    /*=============*/
    /*  LISTENERS  */
    /*=============*/

    private ActionResult onWorldInteraction(@Nullable ServerPlayerEntity player, BlockPos pos) {
        return canInteractWithWorldAt(player, pos) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    private boolean canInteractWithWorldAt(@Nullable PlayerEntity player, BlockPos pos) {
        if (!this.canInteractWithWorld) {
            BuildRush.debug("interactWithWorld: cannot build");
            return false;
        }
        if (player == null) {
            BuildRush.debug("interactWithWorld: player is null");
            return false;
        }
        if (this.isClosing()) {
            BuildRush.debug("interactWithWorld: game is closing");
            return false;
        }
        var data = this.playerDataMap.get(player.getUuid());
        if (data == null) {
            BuildRush.debug("interactWithWorld: player has no data");
            return false;
        }
        if (data.eliminated) {
            BuildRush.debug("interactWithWorld: player is eliminated");
            return false;
        }
        if (!data.plot.buildBounds().contains(pos)) {
            BuildRush.debug("interactWithWorld: block outside player's plot");
            return false;
        }
        if (data.score == this.maxScore) {
            BuildRush.debug("interactWithWorld: player has finished building");
            return false;
        }
        return true;
    }

    private ActionResult onBlockUsed(BlockState state, World world, BlockPos pos, PlayerEntity playerEntity, Hand hand, BlockHitResult blockHitResult) {
        var blockEntity = world.getBlockEntity(pos);
        var block = state.getBlock();

        if (!this.canInteractWithWorldAt(playerEntity, pos))
            return ActionResult.FAIL;
        if (block instanceof ButtonBlock ||
                blockEntity instanceof LockableContainerBlockEntity ||
                block instanceof ComposterBlock ||
                block instanceof AnvilBlock ||
                block instanceof EnchantingTableBlock ||
                block instanceof GrindstoneBlock) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private ActionResult punchBlock(ServerPlayerEntity player, Direction direction, BlockPos pos) {
        if (canInteractWithWorldAt(player, pos)) {
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

            var data = this.playerDataMap.get(player.getUuid());
            int score = this.calcPlayerScore(data);
            data.breakingCooldown = PlayerData.BREAKING_COOLDOWN;
            data.setNameHologramColor(TextUtil.lerpScoreColor((float) score / this.maxScore));
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }

    private ActionResult onBlockBroken(BlockPos pos, boolean drops, @Nullable Entity breakingEntity, int ignored) {
        PlayerData data = null;
        UUID uuid = null;
        for (var entry : this.playerDataMap.entrySet()) {
            if (entry.getValue().plot.buildBounds().contains(pos)) {
                data = entry.getValue();
                uuid = entry.getKey();
                break;
            }
        }
        if (data == null || data.eliminated || this.isClosing()) {
            return ActionResult.FAIL;
        }
        if (this.canInteractWithWorld) {
            var state = this.world.getBlockState(pos);
            var center = pos.toCenterPos();
            var player = this.space.getPlayers().getEntity(uuid);

            if (player != null) {
                this.giveBlock(player, pos);
            }
            this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
            this.world.spawnParticles(ParticleTypes.CRIT, center.getX(), center.getY(), center.getZ(), 5, 0.1D, 0.1D, 0.1D, 0.03D);
            var soundGroup = state.getSoundGroup();
            this.world.playSound(null, pos, soundGroup.getBreakSound(), SoundCategory.BLOCKS, 1.0f, soundGroup.getPitch() - 0.2f);
            data.breakingCooldown = PlayerData.BREAKING_COOLDOWN;
            return ActionResult.FAIL;
        }
        return ActionResult.FAIL;
    }

    private void addPlayer(ServerPlayerEntity player) {
        this.sidebar.addPlayer(player);
        this.resetPlayer(player, true);

        this.songManager.addPlayer(player);
    }

    private void removePlayer(ServerPlayerEntity player) {
        var data = this.playerDataMap.get(player.getUuid());
        if (data != null) {
            if (!data.eliminated && !this.isClosing()) {
                this.eliminate(data);
            }
            data.leave(player);
            this.playerDataMap.remove(player.getUuid());
        }
        this.sidebar.removePlayer(player);
        this.refreshSidebar();
        this.songManager.removePlayer(player);
    }

    /*===========*/
    /*  UTILITY  */
    /*===========*/

    public void refreshSidebar() {
        this.sidebar.setTitle(Text.translatable("game.build_rush").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)));

        this.sidebar.set(b -> {
            b.add(Text.translatable("sidebar.build_rush.round", this.roundManager.getNumber()).setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true)));
            b.add(Text.empty());

            if (this.currentBuild != null) {
                b.add(Text.translatable("sidebar.build_rush.build").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true)));
                b.add(this.currentBuild.name().copy().setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                this.currentBuild.author().ifPresent(author -> b.add((Text.literal("- ").append(Text.translatable("sidebar.build_rush.author", author.name()))).setStyle(Style.EMPTY.withColor(Formatting.GRAY))));
                b.add(Text.empty());
            }

            b.add(Text.translatable("sidebar.build_rush.players_left", this.getAliveDatas().size()).setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true)));
        });
    }

    public List<PlayerData> getAliveDatas() {
        return this.playerDataMap.values().stream().filter(p -> !p.eliminated).toList();
    }

    public void resetPlayer(ServerPlayerEntity player, boolean teleport) {
        var data = playerDataMap.get(player.getUuid());
        boolean cannotPlay = data == null || data.eliminated || this.isClosing();
        boolean hasFinished = (this.roundManager.getState() == RoundManager.BUILD && data != null && data.score == this.maxScore) || this.roundManager.getState() >= RoundManager.BUILD_END;

        if (teleport) {
            Vec3d pos;
            if (cannotPlay) {
                pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(centerPlot.groundBounds().center())).toCenterPos();
            } else {
                data.join(player);
                pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(data.plot.groundBounds().center()).add(0, 0, this.size / 2)).toCenterPos();
                //TODO: add config for this
                for (int i = 5; i > 0; i--) {
                    var newPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(data.plot.groundBounds().center().add(0, 0, i)));
                    if (newPos.getY() <= this.world.getBottomY()) {
                        continue;
                    }
                    if (world.getBlockState(newPos.down()).hasSolidTopSurface(world, newPos.down(), player)) {
                        pos = newPos.toCenterPos();
                        break;
                    }
                }
            }
            player.teleport(pos.getX(), pos.getY(), pos.getZ());
        }

        player.setHealth(20.0f);
        player.changeGameMode(!this.isClosing() && (hasFinished || cannotPlay) ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        if (!player.isSpectator()) {
            player.getAbilities().allowFlying = true;
            if (this.isClosing()) {
                player.getAbilities().flying = true;
            }
            player.sendAbilitiesUpdate();
        }
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
    }

    public void onBlockPlaced(ServerPlayerEntity player) {
        var data = this.playerDataMap.get(player.getUuid());
        if (data == null || data.eliminated) {
            return;
        }

        int score = this.calcPlayerScore(data);
        data.setNameHologramColor(TextUtil.lerpScoreColor((float) score / this.maxScore));
        if (score == this.maxScore) {
            data.score = this.maxScore;
            //TODO: store and send time
            player.sendMessage(TextUtil.translatable(TextUtil.CHECKMARK, TextUtil.SUCCESS, "text.build_rush.finished"), false);
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
            resetPlayer(player, false);
            TextUtil.clearTitle(player);
        }

        // If all players have finished, skip the round
        for (var otherData : this.playerDataMap.values()) {
            if (otherData.eliminated) {
                continue;
            }
            if (otherData.score != this.maxScore) {
                return;
            }
        }
        this.roundManager.skip();
    }


    /*================*/
    /*  Calculations  */
    /*================*/

    /**
     * This method requires the center plot to be placed. We need it to execute the pickBlock method correctly.
     */
    public void calcInventory() {
        this.buildItems.clear();
        for (var pos : this.centerPlot.buildBounds()) {
            var stacks = BuildUtil.stacksForBlock(world, pos);
            this.buildItems.addAll(stacks);
        }
    }

    public int calcPlayerScore(PlayerData playerData) {
        int score = 0;
        for (var pos : this.cachedBuild.positions()) {
            var sourceState = this.cachedBuild.state(pos);
            if (sourceState.isIn(BRTags.IGNORED_IN_COMPARISON)) {
                continue;
            }
            var targetPos = playerData.plot.buildBounds().min().add(pos);
            var targetState = this.world.getBlockState(targetPos);
            var targetEntity = this.world.getBlockEntity(targetPos);
            var sourceNbt = this.cachedBuild.nbt(pos);
            var targetNbt = targetEntity != null ? targetEntity.createNbt() : null;
            if (BuildUtil.areEqual(sourceState, sourceNbt, targetState, targetNbt)) {
                score++;
            }
        }
        return score;
    }

    public void calcLastPlayer() {
        int fewestScore = Integer.MAX_VALUE;
        UUID uuid = null;
        for (var u : this.playerDataMap.keySet()) {
            var d = this.playerDataMap.get(u);
            if (d != null && !d.eliminated && d.score <= fewestScore) {
                fewestScore = d.score;
                uuid = u;
            }
        }
        if (fewestScore == this.maxScore) {
            this.loserUuid = null;
            return;
        }
        if (uuid == null) {
            BuildRush.LOGGER.error("Tried to eliminate last player but no players were found!");
            this.loserUuid = null;
            return;
        }
        this.loserUuid = uuid;
    }

    /* ================= */
    /*   Plot Placement  */
    /* ================= */

    public void placePlayerBuilds() {
        var structure = this.world.getStructureTemplateManager().getTemplate(this.currentBuild.structure()).orElseThrow();

        for (var playerData : playerDataMap.values()) {
            if (playerData.eliminated) {
                continue;
            }
            playerData.plot.placeBuild(this.world, structure);
        }

        // if the player is inside a block, teleport them
        for (var player : this.space.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (!this.world.getBlockState(player.getBlockPos()).isAir() || !this.world.getBlockState(player.getBlockPos().up()).isAir()) {
                this.resetPlayer(player, true);
            }
        }
    }

    public void removePlayerBuilds() {
        for (var playerData : this.playerDataMap.values()) {
            if (playerData.eliminated) {
                continue;
            }
            playerData.plot.removeBuild(this.world);
        }
    }

    /*=====================*/
    /*  ROUND CONTROLLERS  */
    /*=====================*/

    public void newRound() {
        this.removePlayerBuilds();

        for (var playerData : this.playerDataMap.values()) {
            if (playerData.eliminated) {
                continue;
            }
            playerData.plot.placeGround(this.world);
        }
        this.centerPlot.placeGround(this.world);

        // pick a new build
        if (this.builds.isEmpty()) {
            this.builds.addAll(this.usedBuilds);
            this.usedBuilds.clear();
        }
        if (this.builds.isEmpty()) {
            throw new GameOpenException(Text.translatable("error.build_rush.build.none.weird"));
        }
        this.currentBuild = this.builds.get(this.world.random.nextInt(this.builds.size()));
        this.builds.remove(this.currentBuild);
        this.usedBuilds.add(this.currentBuild);

        var structure = this.world.getStructureTemplateManager().getTemplate(this.currentBuild.structure()).orElseThrow();
        this.centerPlot.placeBuild(this.world, structure);
        this.cachedBuild = this.centerPlot.cacheBuild(this.world);
        this.calcInventory();

        // reset scores
        this.loserUuid = null;
        this.maxScore = 0;
        for (var pos : this.cachedBuild.positions()) {
            if (!this.cachedBuild.state(pos).isIn(BRTags.IGNORED_IN_COMPARISON)) {
                this.maxScore++;
            }
        }
        for (var data : this.playerDataMap.values()) {
            if (data.eliminated) {
                continue;
            }
            data.score = 0;
            data.setNameHologramColor(0xFFFFFF);
        }

        // reset timers
        this.roundManager.setTimes(BuildUtil.getBuildComplexity(this.cachedBuild), this.perfectRoundsInARow);

        // reset players
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data != null && !data.eliminated) {
                this.resetPlayer(player, true);
            }
        }

        // reset HUD
        this.refreshSidebar();
    }

    public void startMemorizing() {
        this.placePlayerBuilds();
        this.centerPlot.removeBuild(this.world);
        this.centerPlot.placeGround(this.world);
    }

    public void startBuilding() {
        this.removePlayerBuilds();
        this.canInteract(true);
        this.giveInventory();
    }

    public void endBuilding() {
        this.canInteract(false);
        this.clearInventory();
        this.judge.spawn();
        for (var player : this.space.getPlayers()) {
            this.resetPlayer(player, false);
        }
    }

    public void startElimination() {
        var structure = this.world.getStructureTemplateManager().getTemplate(this.currentBuild.structure()).orElseThrow();
        this.centerPlot.placeBuild(this.world, structure);

        // calculate scores again
        for (var data : this.playerDataMap.values()) {
            // don't recalculate if the player already finished, just in case (fairness)
            if (!data.eliminated && data.score != this.maxScore) {
                data.score = calcPlayerScore(data);
                data.setNameHologramColor(TextUtil.lerpScoreColor((float) data.score / this.maxScore));
            }
        }
        this.calcLastPlayer();

        // calculate stats
        for (var entry : this.playerDataMap.entrySet()) {
            var uuid = entry.getKey();
            var data = entry.getValue();

            if (!data.eliminated && uuid != this.loserUuid) {
                this.statistics.forPlayer(uuid).increment(BRStatistics.SURVIVED_ROUNDS, 1);
                if (data.score == this.maxScore) {
                    this.statistics.forPlayer(uuid).increment(BRStatistics.PERFECT_ROUNDS, 1);
                }
            }
        }
        this.statistics.global().increment(BRStatistics.TOTAL_ROUNDS, 1);
        if (this.loserUuid == null) {
            this.perfectRoundsInARow++;
        }

        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data == null || data.eliminated) {
                continue;
            }

            if (data.score == this.maxScore) {
                TextUtil.sendSubtitle(player, Text.translatable("title.build_rush.perfect").setStyle(Style.EMPTY.withColor(TextUtil.LEGENDARY).withBold(true)), 0, 3 * 20, 10);
                player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
            } else {
                float scorePercentage = data.score / (float) this.maxScore;
                String scoreAsPercent = String.format("%.2f", scorePercentage * 100).replaceAll("0*$", "").replaceAll("[,.]$", "");
                var scoreText = Text.translatable("generic.build_rush.score", scoreAsPercent)
                        .setStyle(Style.EMPTY.withColor(TextUtil.lerpScoreColor(scorePercentage)).withBold(true));

                player.sendMessage(TextUtil.translatable(TextUtil.DASH, TextUtil.NEUTRAL, "text.build_rush.score", scoreText), false);
                TextUtil.sendSubtitle(player, scoreText, 0, 2 * 20, 5);
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }

        if (this.loserUuid == null) {
            this.judge.remove();
        } else {
            var loserData = this.playerDataMap.get(this.loserUuid);
            this.judge.setPlot(loserData.plot);
            this.judge.setAbovePlot();
        }
    }

    public void eliminateLoser() {
        if (this.loserUuid == null) {
            this.space.getPlayers().sendMessage(TextUtil.translatable(TextUtil.HEALTH, TextUtil.SUCCESS, "text.build_rush.no_elimination"));
            this.space.getPlayers().playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.MASTER, 1.0f, 1.5f);
        } else {
            var loserData = this.playerDataMap.get(this.loserUuid);
            if (loserData == null) {
                BuildRush.LOGGER.error("Tried to eliminate last player but the player's data was not found!");
                return;
            }
            this.eliminate(loserData);
            perfectRoundsInARow = 0;

            // play an explosion sound and particles
            this.world.playSound(null, BlockPos.ofFloored(loserData.plot.buildBounds().center()), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0f * this.size, 1.0f);
            this.world.spawnParticles(ParticleTypes.EXPLOSION,
                    loserData.plot.buildBounds().center().getX(), loserData.plot.buildBounds().center().getY(), loserData.plot.buildBounds().center().getZ(), 10,
                    this.size / 1.5f, this.size / 1.5f, this.size / 1.5f, 0.0);
            loserData.plot.placeGround(this.world);
            loserData.plot.removeBuild(this.world);
        }
    }

    public void endRound() {
        // TODO: send round results?
        this.judge.remove();
        if (this.shouldClose) {
            this.removePlayerBuilds();
            for (var playerData : this.playerDataMap.values()) {
                if (playerData.eliminated) {
                    continue;
                }
                playerData.plot.placeGround(this.world);
            }
            this.startClosing();
        }
    }
}
