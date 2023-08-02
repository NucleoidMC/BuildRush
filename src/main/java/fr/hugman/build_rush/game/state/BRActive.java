package fr.hugman.build_rush.game.state;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.sidebars.api.Sidebar;
import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.build.BuildUtil;
import fr.hugman.build_rush.build.CachedBuild;
import fr.hugman.build_rush.event.UseEvents;
import fr.hugman.build_rush.event.WorldBlockBreakEvent;
import fr.hugman.build_rush.game.BRRoundManager;
import fr.hugman.build_rush.game.Judge;
import fr.hugman.build_rush.game.PlayerData;
import fr.hugman.build_rush.registry.tag.BRTags;
import fr.hugman.build_rush.text.TextUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
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
    private final List<Build> builds;
    private final List<Build> usedBuilds;

    private final HashMap<UUID, PlayerData> playerDataMap;
    private Build currentBuild;
    private CachedBuild cachedBuild;
    private final List<ItemStack> cachedBuildItems;
    private int maxScore;
    private UUID loserUuid;

    private long tick;
    private long closeTick;
    private final BRRoundManager round;
    public final Sidebar globalSidebar = new Sidebar(Sidebar.Priority.MEDIUM);
    private boolean canInteractWithWorld;

    public final Judge judge;

    public BRActive(BRConfig config, GameSpace space, ServerWorld world, BlockBounds center, BlockBounds centerPlot, StructureTemplate platform, StructureTemplate plotGround, List<Build> builds) {
        this.config = config;
        this.space = space;
        this.world = world;
        this.center = center;
        this.centerPlot = centerPlot;
        this.platform = platform;
        this.plotGround = plotGround;
        this.builds = builds;
        this.usedBuilds = new ArrayList<>();

        this.playerDataMap = new HashMap<>();
        this.currentBuild = null;
        this.cachedBuildItems = new ArrayList<>();

        this.round = new BRRoundManager(this, 10, 40);
        this.tick = 0;
        this.closeTick = Long.MAX_VALUE;
        this.canInteractWithWorld = false;

        this.judge = Judge.of(round, world, this.centerPlot.center());
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
            activity.deny(GameRuleType.CORAL_DEATH);
            activity.deny(GameRuleType.ICE_MELT);

            activity.listen(GameActivityEvents.ENABLE, this::enable);
            activity.listen(GameActivityEvents.TICK, this::tick);
            activity.listen(GameActivityEvents.DESTROY, this::onClose);

            activity.listen(GamePlayerEvents.OFFER, offer -> offer.accept(this.world, this.centerPlot.center()));
            activity.listen(GamePlayerEvents.ADD, this::addPlayer);
            activity.listen(GamePlayerEvents.REMOVE, this::removePlayer);

            activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
                if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
                    this.resetPlayer(player, true);
                }
                return ActionResult.FAIL;
            });
            activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
                this.resetPlayer(player, true);
                return ActionResult.FAIL;
            });
            activity.listen(BlockPlaceEvent.BEFORE, (player, world1, pos, state, context) -> this.onWorldInteraction(player, pos));
            activity.listen(BlockPlaceEvent.AFTER, (player, world1, pos, state) -> this.onBlockPlaced(player));
            activity.listen(FluidPlaceEvent.EVENT, (world1, pos, player, hitResult) -> this.onWorldInteraction(player, pos));
            activity.listen(BlockPunchEvent.EVENT, this::punchBlock);
            activity.listen(WorldBlockBreakEvent.EVENT, this::onBlockBroken);
            activity.listen(UseEvents.BLOCK, this::onBlockUsed);
            activity.listen(UseEvents.ITEM_ON_BLOCK, (stack, context) ->
                    this.onWorldInteraction((ServerPlayerEntity) context.getPlayer(), context.getBlockPos().add(context.getSide().getVector()))
            );
        });

        return GameResult.ok();
    }

    /*=========*/
    /*  LOGIC  */
    /*=========*/

    public void enable() {
        var players = this.space.getPlayers();
        var playerCount = BuildRush.DEBUG ? players.size() + 2 : players.size();
        int i = 0;
        for (var player : players) {
            var data = new PlayerData();
            this.playerDataMap.put(player.getUuid(), data);
            data.join(player);
            data.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);
            BuildRush.debug("Player n°" + i + ", name tick: " + data.playerNameTick);
        }
        if (BuildRush.DEBUG) {
            var data1 = new PlayerData();
            var data2 = new PlayerData();
            data1.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);
            BuildRush.debug("Player n°" + i + ", name tick: " + data1.playerNameTick);
            data2.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);
            BuildRush.debug("Player n°" + i + ", name tick: " + data2.playerNameTick);
            this.playerDataMap.put(UUID.randomUUID(), data1);
            this.playerDataMap.put(UUID.randomUUID(), data2);
        }
        this.refreshSidebar();
        this.globalSidebar.show();
        this.calcPlatformsAndPlots();
        this.placeAlivePlayerPlatforms();

        for (var player : this.space.getPlayers()) {
            this.resetPlayer(player, true);
            this.globalSidebar.addPlayer(player);
        }
    }

    public void tick() {
        this.tick++;
        if (this.isClosing()) {
            if (this.tick >= this.closeTick) {
                this.space.close(GameCloseReason.FINISHED);
            }
            return;
        }
        this.round.tick();
        this.judge.tick();

        for (var data : this.playerDataMap.values()) {
            data.tick();
        }

        var showCountdown = this.round.getState() == BRRoundManager.BUILD || this.round.getState() == BRRoundManager.MEMORIZE;
        var stateTick = this.round.getStateTick();
        var stateTotalTicks = this.round.getLength(this.round.getState());
        var statePercent = (float) stateTick / stateTotalTicks;

        var stateTicksLeft = stateTotalTicks - stateTick;
        var stateMinutes = stateTicksLeft / 20 / 60;
        var stateSeconds = stateTicksLeft / 20 % 60;

        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());

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
        this.closeTick = this.tick + 20 * 10;
        for (var player : this.space.getPlayers()) {
            player.getInventory().clear();
            this.resetPlayer(player, false);
        }
    }

    private boolean isClosing() {
        return this.closeTick != Long.MAX_VALUE;
    }

    private void onClose(GameCloseReason gameCloseReason) {
        this.globalSidebar.hide();
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data != null) {
                data.leave(player);
            }
            this.globalSidebar.removePlayer(player);
        }
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
        //TODO: play breaking sound
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
                            p.sendMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.LEGENDARY, "text.build_rush.win.self", this.round.getNumber()));
                        } else {
                            p.sendMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.EPIC, "text.build_rush.win", winner.getName().getString(), this.round.getNumber()));
                        }
                    }
                    this.startClosing();
                    return;
                }
            }
            this.space.getPlayers().sendMessage(TextUtil.translatable(TextUtil.FLAG, TextUtil.EPIC, "text.build_rush.win.unknown", this.round.getNumber()));
            this.startClosing();
        }
    }

    public void giveInventory() {
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUuid());
            if (data == null || data.eliminated) {
                continue;
            }
            for (var stack : cachedBuildItems) {
                this.give(player, stack, false);
            }
        }
    }

    public void giveBlock(PlayerEntity player, BlockPos pos) {
        var stacks = BuildUtil.stacksForBlock(world, pos);
        if(stacks.isEmpty()) {
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
        return canInteractWithWorld(player, pos) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    private boolean canInteractWithWorld(@Nullable ServerPlayerEntity player, BlockPos pos) {
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
        if (!data.plot.contains(pos)) {
            BuildRush.debug("interactWithWorld: block outside player's plot");
            return false;
        }
        if (data.score == this.maxScore) {
            BuildRush.debug("interactWithWorld: player has finished building");
            return false;
        }
        return true;
    }

    private ActionResult onBlockUsed(BlockState state, World world, BlockPos blockPos, PlayerEntity playerEntity, Hand hand, BlockHitResult blockHitResult) {
        return ActionResult.FAIL;
    }

    private ActionResult punchBlock(ServerPlayerEntity player, Direction direction, BlockPos pos) {
        if (canInteractWithWorld(player, pos)) {
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
            if (entry.getValue().plot.contains(pos)) {
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
        this.globalSidebar.addPlayer(player);
        this.resetPlayer(player, true);
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
        this.globalSidebar.removePlayer(player);
        this.refreshSidebar();
    }

    /*===========*/
    /*  UTILITY  */
    /*===========*/

    public void refreshSidebar() {
        this.globalSidebar.setTitle(Text.translatable("game.build_rush").setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)));

        this.globalSidebar.set(b -> {
            b.add(Text.translatable("sidebar.build_rush.round", this.round.getNumber()).setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true)));
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
        boolean spectator = data == null || data.eliminated || this.isClosing();

        if (teleport) {
            Vec3d pos;
            if (spectator) {
                pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(centerPlot.center())).toCenterPos();
            } else {
                pos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(data.plot.center()).add(0, 0, data.plot.size().getZ())).toCenterPos();
                for (int i = 5; i > 0; i--) {
                    var newPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, BlockPos.ofFloored(data.plot.center().add(0, 0, i)));
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
        player.changeGameMode(spectator && !this.isClosing() ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        if (!spectator) {
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
        }
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
    }

    public void removeBlock(BlockPos pos) {
        if (!world.getBlockState(pos).isAir()) {
            var particlePos = pos.toCenterPos();
            this.world.spawnParticles(ParticleTypes.CLOUD, particlePos.getX(), particlePos.getY(), particlePos.getZ(), 2, 0.5, 0.5, 0.5, 0.1D);
            this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
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
            TextUtil.clearTitle(player);
        }
        this.calcLastPlayer();

        // If all players have finished, skip the round
        for (var otherData : this.playerDataMap.values()) {
            if (otherData.eliminated) {
                continue;
            }
            if (otherData.score != this.maxScore) {
                return;
            }
        }
        this.round.skip();
    }


    /*================*/
    /*  Calculations  */
    /*================*/

    public void calcPlatformsAndPlots() {
        var aliveDatas = getAliveDatas();
        int n = aliveDatas.size();

        var platformSize = this.platform.getSize();
        var platformPlotOffset = this.config.map().platformPlotOffset();
        var plotSize = this.plotGround.getSize().getX();

        int centerSizeX = this.center.max().getX() - this.center.min().getX();
        int centerSizeY = this.center.max().getZ() - this.center.min().getZ();

        // C = (sqrt((A/2)^2 + (B/2)^2) + X/2) / sin(pi/N)
        double r = (Math.sqrt(Math.pow(centerSizeX / 2.0, 2) + Math.pow(centerSizeY / 2.0, 2)) + (Math.max(platformSize.getX(), platformSize.getZ()) / 2.0) + this.config.map().platformSpacing()) / Math.sin(Math.PI / n);
        // I owe chat gpt a beer for this one ^
        double thetaStep = 2 * Math.PI / n;

        int index = 0;
        for (var aliveData : aliveDatas) {
            double theta = index++ * thetaStep;

            int x = MathHelper.floor(Math.cos(theta) * r);
            int y = this.centerPlot.min().getY() - platformPlotOffset.getY(); // TODO: add an offset to the config
            int z = MathHelper.floor(Math.sin(theta) * r);

            int x1 = MathHelper.floor(platformSize.getX() / 2.0);
            int z1 = MathHelper.floor(platformSize.getZ() / 2.0);
            int x2 = platformSize.getX() - x1 - 1;
            int z2 = platformSize.getZ() - z1 - 1;

            BlockPos minPos = BlockPos.ofFloored(x - x1, y, z - z1);
            BlockPos maxPos = BlockPos.ofFloored(x + x2, y + platformSize.getY(), z + z2);

            aliveData.platform = BlockBounds.of(minPos, maxPos);

            int xPlot = aliveData.platform.min().getX() + platformPlotOffset.getX();
            int yPlot = aliveData.platform.min().getY() + platformPlotOffset.getY();
            int zPlot = aliveData.platform.min().getZ() + platformPlotOffset.getZ();
            int size = plotSize - 1;

            aliveData.plot = BlockBounds.of(xPlot, yPlot, zPlot, xPlot + size, yPlot + size, zPlot + size);
        }
    }

    public void cacheBuild() {
        int size = this.centerPlot.size().getX();
        HashMap<Vec3i, BlockState> states = new HashMap<>();
        HashMap<Vec3i, NbtCompound> nbt = new HashMap<>();
        for (int x = 0; x <= size; x++) {
            for (int y = 0; y <= size; y++) {
                for (int z = 0; z <= size; z++) {
                    var targetPos = new BlockPos(x, y, z);
                    var sourcePos = this.centerPlot.min().add(x, y, z);

                    // state
                    states.put(targetPos, this.world.getBlockState(sourcePos));

                    // nbt
                    var sourceEntity = this.world.getBlockEntity(sourcePos);
                    if (sourceEntity != null) {
                        var sourceNbt = sourceEntity.createNbt();
                        nbt.put(targetPos, sourceNbt);
                    }
                }
            }
        }
        this.cachedBuild = new CachedBuild(states, nbt);
    }

    /**
     * This method requires the center plot to be placed. We need it to execute the pickBlock method correctly.
     */
    public void calcInventory() {
        this.cachedBuildItems.clear();
        for (var pos : this.centerPlot) {
            var stacks = BuildUtil.stacksForBlock(world, pos);
            this.cachedBuildItems.addAll(stacks);
        }
    }

    public int calcPlayerScore(PlayerData playerData) {
        int score = 0;
        for (var pos : this.cachedBuild.positions()) {
            var sourceState = this.cachedBuild.state(pos);
            if (sourceState.isIn(BRTags.IGNORED_IN_COMPARISON)) {
                continue;
            }
            var targetPos = playerData.plot.min().add(pos);
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

    /**
     * @return 0 if there is an error, 1 if there is a last player, 2 if there is no last player (everyone got perfect)
     */
    public int calcLastPlayer() {
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
            //TODO: check for time
            loserUuid = null;
            return 2;
        }
        if (uuid == null) {
            BuildRush.LOGGER.error("Tried to eliminate last player but no players were found!");
            loserUuid = null;
            return 0;
        }
        loserUuid = uuid;
        return 1;
    }

    /* ================= */
    /*   Plot Placement  */
    /* ================= */

    public void placeAlivePlayerPlatforms() {
        for (var entry : this.playerDataMap.entrySet()) {
            var data = entry.getValue();
            if (!data.eliminated) {
                var platformPos = data.platform.min();
                this.platform.place(world, platformPos, platformPos, new StructurePlacementData(), this.world.getRandom(), 2);
                BlockBounds barrier = BlockBounds.of(data.plot.min().add(0, -2, 0), data.plot.max().add(0, -2, 0));
                barrier.forEach(pos -> {
                    if (world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
                    }
                });
                if (data.playerNameHolder == null) {
                    var player = this.space.getPlayers().getEntity(entry.getKey());

                    Vec3d center = this.centerPlot.center();
                    //TODO: make the offset configurable
                    Vec3d pos = data.plot.centerTop().add(0, 10, 0);

                    Quaternionf rotation = new Quaternionf();
                    // we are at `pos`, we must rotate to face `center`
                    rotation.lookAlong(pos.subtract(center).toVector3f(), new Vector3f(0, 1, 0));

                    data.playerNameHolder = new ElementHolder();
                    ChunkAttachment.of(data.playerNameHolder, world, pos);
                    data.playerNameElement = new TextDisplayElement(player == null ? Text.of("???") : player.getDisplayName());
                    data.playerNameElement.setBillboardMode(DisplayEntity.BillboardMode.VERTICAL);
                    //data.playerNameElement.setRightRotation(rotation);
                    data.playerNameElement.setScale(new Vector3f(5, 5, 5));

                    data.playerNameHolder.addElement(data.playerNameElement);
                }
            }
        }
    }

    public void placePlayerBuilds() {
        var aliveDatas = getAliveDatas();

        var structure = this.world.getStructureTemplateManager().getTemplate(this.currentBuild.structure()).orElseThrow();
        boolean shouldPlacePlotGround = structure.getSize().getY() > this.plotGround.getSize().getX();
        for (var aliveData : aliveDatas) {
            this.world.playSound(null, BlockPos.ofFloored(aliveData.plot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 0.9f);
            var plotPos = aliveData.plot.min();
            if (shouldPlacePlotGround) {
                plotPos = plotPos.down();
            }
            structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
        }

        // if the player is inside a block, teleport them on top
        for (var uuid : this.playerDataMap.keySet()) {
            if (!this.space.getPlayers().contains(uuid)) {
                continue;
            }
            var player = this.world.getPlayerByUuid(uuid);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!this.world.getBlockState(player.getBlockPos()).isAir() || !this.world.getBlockState(player.getBlockPos().up()).isAir()) {
                    this.resetPlayer(serverPlayer, true);
                }
            }
        }
    }

    public void placePlayerPlotGrounds() {
        var aliveDatas = getAliveDatas();

        for (var aliveData : aliveDatas) {
            placePlayerPlotGround(aliveData);
        }
    }

    public void placePlayerPlotGround(PlayerData data) {
        var plotPos = data.plot.min().down();
        this.plotGround.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
    }

    public void removePlayerBuilds() {
        var aliveDatas = getAliveDatas();
        for (var aliveData : aliveDatas) {
            this.removePlayerPlot(aliveData);
        }
    }

    public void removePlayerPlot(PlayerData data) {
        this.world.playSound(null, BlockPos.ofFloored(data.plot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 1.1f);
        for (var pos : data.plot) {
            this.removeBlock(pos);
        }
    }

    public void placeCenterBuild() {
        var plotPos = this.centerPlot.min();
        var structure = this.world.getStructureTemplateManager().getTemplate(this.currentBuild.structure()).orElseThrow();
        boolean shouldPlacePlotGround = structure.getSize().getY() > this.plotGround.getSize().getX();
        if (shouldPlacePlotGround) {
            plotPos = plotPos.down();
        }
        this.world.playSound(null, BlockPos.ofFloored(this.centerPlot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 0.9f);
        structure.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
        this.calcInventory();
    }

    public void placeCenterBuildGround() {
        var plotPos = this.centerPlot.min().down();
        this.plotGround.place(world, plotPos, plotPos, new StructurePlacementData(), this.world.getRandom(), 2);
    }

    public void removeCenterBuild() {
        this.world.playSound(null, BlockPos.ofFloored(this.centerPlot.center()), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 2.0f, 1.1f);
        for (var pos : this.centerPlot) {
            this.removeBlock(pos);
        }
    }

    /*=====================*/
    /*  ROUND CONTROLLERS  */
    /*=====================*/

    public void newRound() {
        this.removePlayerBuilds();
        this.placePlayerPlotGrounds();
        this.placeCenterBuildGround();

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

        this.placeCenterBuild();
        this.cacheBuild();

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
        this.round.setTimes(BuildUtil.getBuildComplexity(this.cachedBuild));

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
        this.removeCenterBuild();
        this.placeCenterBuildGround();
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
    }

    public void startElimination() {
        // calculate scores again
        for (var data : this.playerDataMap.values()) {
            // don't recalculate if the player already finished, just in case (fairness)
            if (!data.eliminated && data.score != this.maxScore) {
                data.score = calcPlayerScore(data);
                data.setNameHologramColor(TextUtil.lerpScoreColor((float) data.score / this.maxScore));
            }
        }
        this.calcLastPlayer();

        // send score in the chat
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

        if (this.calcLastPlayer() == 1) {
            var loserData = this.playerDataMap.get(this.loserUuid);
            this.judge.setAbovePlot(loserData.plot);
        }
        else {
            this.judge.remove();
        }
    }

    public void eliminateLoser() {
        int result = this.calcLastPlayer();
        if (result == 2) {
            this.space.getPlayers().sendMessage(TextUtil.translatable(TextUtil.HEALTH, TextUtil.SUCCESS, "text.build_rush.no_elimination"));
            this.space.getPlayers().playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.MASTER, 1.0f, 1.5f);
        } else if (result == 1) {
            var loserData = this.playerDataMap.get(this.loserUuid);
            if (loserData == null) {
                BuildRush.LOGGER.error("Tried to eliminate last player but the player's data was not found!");
                return;
            }
            this.eliminate(loserData);
            this.removePlayerPlot(loserData);
            this.placePlayerPlotGround(loserData);
        }
    }

    public void endRound() {
        // TODO: send round results?
        this.judge.remove();
    }
}
