package fr.hugman.build_rush.game.state;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.sidebars.api.Sidebar;
import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.build.BuildItemCollector;
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
import fr.hugman.build_rush.statistics.BRStatistics;
import fr.hugman.build_rush.text.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameOpenException;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.game.stats.GameStatisticBundle;
import xyz.nucleoid.plasmid.api.util.PlayerUtil;
import xyz.nucleoid.stimuli.event.EventResult;
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
    private final ServerLevel level;
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

    public final GameStatisticBundle statistics;

    public BRActive(ServerLevel level, GameSpace space, BRConfig config, int size, Plot centerPlot, List<Build> builds) {
        this.level = level;
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

        this.judge = Judge.of(roundManager, level, this.centerPlot.groundBounds().center().add(0, size + 3, 0));

        this.statistics = space.getStatistics().bundle(BuildRush.ID);
    }

    public static BRActive create(BRConfig config, GameSpace space, ServerLevel world, BRMap map, List<Build> builds) {
        var centerPlot = map.centerPlot();
        var size = centerPlot.buildBounds().size().getX() + 1;

        BRActive active = new BRActive(world, space, config, size, centerPlot, builds);

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

            activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            activity.listen(GamePlayerEvents.ACCEPT, offer -> offer.teleport(world, centerPlot.groundBounds().center()));
            activity.listen(GamePlayerEvents.ADD, active::addPlayer);
            activity.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
                if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
                    active.resetPlayer(player, true);
                }
                return EventResult.DENY;
            });
            activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
                active.resetPlayer(player, true);
                return EventResult.DENY;
            });
            activity.listen(BlockPlaceEvent.BEFORE, (player, world1, pos, state, context) -> active.onWorldInteraction(player, pos));
            activity.listen(BlockPlaceEvent.AFTER, (player, world1, pos, state) -> active.onBlockPlaced(player));
            activity.listen(FluidPlaceEvent.EVENT, (world1, pos, player, hitResult) -> active.onWorldInteraction(player, pos));
            activity.listen(BlockPunchEvent.EVENT, active::punchBlock);
            activity.listen(WorldBlockBreakEvent.EVENT, active::onBlockBroken);
            activity.listen(UseEvents.BLOCK, active::onBlockUsed);
            activity.listen(UseEvents.ITEM_ON_BLOCK, (stack, context) ->
                    active.onWorldInteraction((ServerPlayer) context.getPlayer(), context.getClickedPos().offset(context.getClickedFace().getUnitVec3i())).asActionResult()
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
            var data = new PlayerData(Mth.createInsecureUUID(this.level.getRandom()));

            var plot = plots.get(this.level.getRandom().nextInt(plots.size()));
            data.plot = plot;
            plots.remove(plot);

            data.join(player);
            Vec3 pos = plot.buildBounds().centerTop().add(0, this.config.mapConfig().nametagOffset(), 0);

            data.playerNameHolder = new ElementHolder();
            ChunkAttachment.of(data.playerNameHolder, level, pos);
            data.playerNameElement = new TextDisplayElement(player == null ? Component.nullToEmpty("???") : player.getDisplayName());
            data.playerNameElement.setBillboardMode(Display.BillboardConstraints.VERTICAL);
            data.playerNameElement.setScale(new Vector3f(5, 5, 5));

            data.playerNameHolder.addElement(data.playerNameElement);
            data.playerNameTick += (int) (((float) i++ / playerCount) * PlayerData.PLAYER_NAME_TICKS);

            this.playerDataMap.put(player.getUUID(), data);
        }
        if (BuildRush.debug()) {
            var data1 = new PlayerData(Mth.createInsecureUUID(this.level.getRandom()));
            var data2 = new PlayerData(Mth.createInsecureUUID(this.level.getRandom()));

            var plot1 = plots.get(this.level.getRandom().nextInt(plots.size()));
            plots.remove(plot1);
            var plot2 = plots.get(this.level.getRandom().nextInt(plots.size()));
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
    }

    public void tick() {
        this.tick++;
        if (this.isClosing()) {
            var progress = (float) (this.closeTick - this.tick) / this.closeTicks;
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
            var data = this.playerDataMap.get(player.getUUID());
            if (!player.isSpectator() && this.canInteractWithWorld) {
                // if the player is in another's safe zone, teleport them back to their own plot
                for (var otherData : this.playerDataMap.values()) {
                    if (otherData != data && otherData.plot != null && otherData.plot.safeZone().contains(player.blockPosition())) {
                        resetPlayer(player, true);
                        player.sendSystemMessage(TextUtil.translatable(TextUtil.WARNING, TextUtil.DANGER, "text.build_rush.do_not_disturb"));
                        PlayerUtil.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 1, 1);
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
                        data.bar.setName(Component.translatable("bar.build_rush.perfect_build"));
                        data.bar.setColor(BossEvent.BossBarColor.GREEN);
                        data.bar.setProgress(1);
                    } else {
                        data.bar.setName(Component.translatable("bar.build_rush.time_left", String.format("%d", stateMinutes), String.format("%02d", stateSeconds)));

                        if (stateTicksLeft % 20 == 0) {
                            if (stateMinutes == 0) {
                                if (stateSeconds >= 30) {
                                    data.bar.setColor(BossEvent.BossBarColor.GREEN);
                                } else if (stateSeconds >= 15) {
                                    data.bar.setColor(BossEvent.BossBarColor.YELLOW);
                                } else if (stateSeconds <= 10) {
                                    data.bar.setColor(BossEvent.BossBarColor.RED);
                                }
                                if (stateSeconds == 30 || stateSeconds == 15 || stateSeconds == 10) {
                                    TextUtil.sendSubtitle(player, Component.literal(String.valueOf(stateSeconds)).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)), 0, 30, 10);
                                    PlayerUtil.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1.3f);
                                }
                                if (stateSeconds <= 5) {
                                    TextUtil.sendSubtitle(player, Component.literal(String.valueOf(stateSeconds)).setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), 0, 20, 0);
                                    PlayerUtil.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1.6f);
                                }
                            } else {
                                data.bar.setColor(BossEvent.BossBarColor.GREEN);
                            }
                            if (stateSeconds == 0 && (stateMinutes == 1 || stateMinutes == 2)) {
                                TextUtil.sendSubtitle(player, Component.literal(String.valueOf(60)).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)), 0, 40, 20);
                                PlayerUtil.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1, 1);
                            }
                        }
                        data.bar.setProgress(statePercent);
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
            player.getInventory().clearContent();
            this.resetPlayer(player, false);

            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    private boolean isClosing() {
        return this.closeTick != Long.MAX_VALUE;
    }

    private void onClose(GameCloseReason gameCloseReason) {
        this.sidebar.hide();
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUUID());
            if (data != null) {
                data.leave(player);
            }
            this.sidebar.removePlayer(player);
        }
    }

    public void canInteract(boolean canBuild) {
        this.canInteractWithWorld = canBuild;
    }

    public void eliminate(PlayerData data) {
        ServerPlayer player = null;
        for (var uuid : this.playerDataMap.keySet()) {
            if (this.playerDataMap.get(uuid) == data) {
                for (var p : this.space.getPlayers()) {
                    if (p.getUUID().equals(uuid)) {
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
                p.sendSystemMessage(TextUtil.translatable(TextUtil.SKULL, TextUtil.DANGER, "text.build_rush.eliminated", player.getName().getString(), scoreAsPercent));
            }
            player.sendSystemMessage(TextUtil.translatable(TextUtil.SKULL, TextUtil.DANGER, "text.build_rush.eliminated.self", player.getName().getString()));
            TextUtil.clearSubtitle(player);
            TextUtil.sendTitle(player, TextUtil.translatable(TextUtil.DANGER, "title.build_rush.eliminated"), 0, 5 * 20, 20);
            PlayerUtil.playSoundToPlayer(player, SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1, 2f);
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
                            p.sendSystemMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.LEGENDARY, "text.build_rush.win.self", this.roundManager.getNumber()));
                        } else {
                            p.sendSystemMessage(TextUtil.translatable(TextUtil.STAR, TextUtil.EPIC, "text.build_rush.win", winner.getName().getString(), this.roundManager.getNumber()));
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
            var data = this.playerDataMap.get(player.getUUID());
            if (data == null || data.eliminated) {
                continue;
            }
            for (var stack : buildItems) {
                this.give(player, stack, null, false);
            }
        }
    }

    public void giveBlock(Player player, BlockPos pos) {
        var collector = new BuildItemCollector();
        collector.accept(this.level, pos);

        var stacks = collector.getStacks();
        if (stacks.isEmpty()) {
            return;
        }
        var firstStack = stacks.get(0);

        this.give(player, firstStack, collector, true);
        for (int i = 1; i < stacks.size(); i++) {
            this.give(player, stacks.get(i), collector, false);
        }
    }

    public void give(Player player, ItemStack stack, @Nullable BuildItemCollector collector, boolean giveToHand) {
        if (collector != null && collector.isSingletonStack(stack) && player.getInventory().contains(stack)) {
            return;
        }

        if (giveToHand) {
            var slot = player.getInventory().getSelectedSlot();
            var oldStack = player.getInventory().getItem(slot);
            if (oldStack.isEmpty()) {
                player.getInventory().setItem(slot, stack.copy());
            } else {
                player.addItem(stack.copy());
            }
        } else {
            player.addItem(stack.copy());
        }
    }

    public void clearInventory() {
        for (var player : this.space.getPlayers()) {
            var data = this.playerDataMap.get(player.getUUID());
            if (data == null || data.eliminated) {
                continue;
            }
            player.getInventory().clearContent();
        }
    }

    /*=============*/
    /*  LISTENERS  */
    /*=============*/

    private EventResult onWorldInteraction(@Nullable ServerPlayer player, BlockPos pos) {
        return canInteractWithWorldAt(player, pos) ? EventResult.ALLOW : EventResult.DENY;
    }

    private boolean canInteractWithWorldAt(@Nullable Player player, BlockPos pos) {
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
        var data = this.playerDataMap.get(player.getUUID());
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

    private InteractionResult onBlockUsed(BlockState state, Level world, BlockPos pos, Player playerEntity, BlockHitResult blockHitResult) {
        var blockEntity = world.getBlockEntity(pos);
        var block = state.getBlock();

        if (!this.canInteractWithWorldAt(playerEntity, pos))
            return InteractionResult.FAIL;
        if (block instanceof ButtonBlock ||
                blockEntity instanceof BaseContainerBlockEntity ||
                block instanceof ComposterBlock ||
                block instanceof AnvilBlock ||
                block instanceof EnchantingTableBlock ||
                block instanceof GrindstoneBlock) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private EventResult punchBlock(ServerPlayer player, Direction direction, BlockPos pos) {
        if (!canInteractWithWorldAt(player, pos)) {
            return EventResult.DENY;
        }
        /*
          This currently doesn't work very well, so I'm disabling it for now
          If you hold the click it won't break the second block if you're still holding the click
        if(data.breakingCooldown > 0) {
            return EventResult.DENY;
        }
         */
        var state = this.level.getBlockState(pos);
        var center = Vec3.atCenterOf(pos);

        this.giveBlock(player, pos);
        this.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        this.level.sendParticles(ParticleTypes.CRIT, center.x(), center.y(), center.z(), 5, 0.1D, 0.1D, 0.1D, 0.03D);
        this.level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0f, 0.8f);

        var data = this.playerDataMap.get(player.getUUID());
        int score = this.calcPlayerScore(data);
        data.breakingCooldown = PlayerData.BREAKING_COOLDOWN;
        data.setNameHologramColor(TextUtil.lerpScoreColor((float) score / this.maxScore));
        return EventResult.ALLOW;
    }

    private InteractionResult onBlockBroken(BlockPos pos, boolean drops, @Nullable Entity breakingEntity, int ignored) {
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
            return InteractionResult.FAIL;
        }
        if (this.canInteractWithWorld) {
            var state = this.level.getBlockState(pos);
            var center = Vec3.atCenterOf(pos);
            var player = this.space.getPlayers().getEntity(uuid);

            if (player != null) {
                this.giveBlock(player, pos);
            }
            this.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            this.level.sendParticles(ParticleTypes.CRIT, center.x(), center.y(), center.z(), 5, 0.1D, 0.1D, 0.1D, 0.03D);
            var soundGroup = state.getSoundType();
            this.level.playSound(null, pos, soundGroup.getBreakSound(), SoundSource.BLOCKS, 1.0f, soundGroup.getPitch() - 0.2f);
            data.breakingCooldown = PlayerData.BREAKING_COOLDOWN;
            return InteractionResult.FAIL;
        }
        return InteractionResult.FAIL;
    }

    private void addPlayer(ServerPlayer player) {
        this.sidebar.addPlayer(player);
        this.resetPlayer(player, true);
    }

    private void removePlayer(ServerPlayer player) {
        var data = this.playerDataMap.get(player.getUUID());
        if (data != null) {
            if (!data.eliminated && !this.isClosing()) {
                this.eliminate(data);
            }
            data.leave(player);
            this.playerDataMap.remove(player.getUUID());
        }
        this.sidebar.removePlayer(player);
        this.refreshSidebar();
    }

    /*===========*/
    /*  UTILITY  */
    /*===========*/

    public void refreshSidebar() {
        this.sidebar.setTitle(Component.translatable("game.build_rush").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)));

        this.sidebar.set(b -> {
            b.add(Component.translatable("sidebar.build_rush.round", this.roundManager.getNumber()).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)));
            b.add(Component.empty());

            if (this.currentBuild != null) {
                b.add(Component.translatable("sidebar.build_rush.build").setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)));
                b.add(this.currentBuild.name().copy().setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)));
                this.currentBuild.author().ifPresent(author -> b.add((Component.literal("- ").append(Component.translatable("sidebar.build_rush.author", author.name()))).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))));
                b.add(Component.empty());
            }

            b.add(Component.translatable("sidebar.build_rush.players_left", this.getAliveDatas().size()).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true)));
        });
    }

    public List<PlayerData> getAliveDatas() {
        return this.playerDataMap.values().stream().filter(p -> !p.eliminated).toList();
    }

    public void resetPlayer(ServerPlayer player, boolean teleport) {
        var data = playerDataMap.get(player.getUUID());
        boolean cannotPlay = data == null || data.eliminated || this.isClosing();
        boolean hasFinished = (this.roundManager.getState() == RoundManager.BUILD && data != null && data.score == this.maxScore) || this.roundManager.getState() >= RoundManager.BUILD_END;

        if (teleport) {
            Vec3 pos;
            if (cannotPlay) {
                pos = Vec3.atCenterOf(level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.containing(centerPlot.groundBounds().center())));
            } else {
                data.join(player);
                pos = Vec3.atCenterOf(level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.containing(data.plot.groundBounds().center()).offset(0, 0, this.size / 2)));
                //TODO: add config for this
                for (int i = 5; i > 0; i--) {
                    var newPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.containing(data.plot.groundBounds().center().add(0, 0, i)));
                    if (newPos.getY() <= this.level.getMinY()) {
                        continue;
                    }
                    if (level.getBlockState(newPos.below()).entityCanStandOn(level, newPos.below(), player)) {
                        pos = Vec3.atCenterOf(newPos);
                        break;
                    }
                }
            }
            player.randomTeleport(pos.x(), pos.y(), pos.z(), false);
        }

        player.setHealth(20.0f);
        player.setGameMode(!this.isClosing() && (hasFinished || cannotPlay) ? GameType.SPECTATOR : GameType.SURVIVAL);
        if (!player.isSpectator()) {
            player.getAbilities().mayfly = true;
            if (this.isClosing()) {
                player.getAbilities().flying = true;
            }
            player.onUpdateAbilities();
        }
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
    }

    public void onBlockPlaced(ServerPlayer player) {
        var data = this.playerDataMap.get(player.getUUID());
        if (data == null || data.eliminated) {
            return;
        }

        int score = this.calcPlayerScore(data);
        data.setNameHologramColor(TextUtil.lerpScoreColor((float) score / this.maxScore));
        if (score == this.maxScore) {
            data.score = this.maxScore;
            //TODO: store and send time
            player.sendSystemMessage(TextUtil.translatable(TextUtil.CHECKMARK, TextUtil.SUCCESS, "text.build_rush.finished"), false);
            PlayerUtil.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
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

        var collector = new BuildItemCollector();

        for (var pos : this.centerPlot.buildBounds()) {
            collector.accept(this.level, pos);
        }

        this.buildItems.addAll(collector.getStacks());
    }

    public int calcPlayerScore(PlayerData playerData) {
        int score = 0;
        for (var pos : this.cachedBuild.positions()) {
            var sourceState = this.cachedBuild.state(pos);
            if (sourceState.is(BRTags.IGNORED_IN_COMPARISON)) {
                continue;
            }
            var targetPos = playerData.plot.buildBounds().min().offset(pos);
            var targetState = this.level.getBlockState(targetPos);
            var targetEntity = this.level.getBlockEntity(targetPos);
            var sourceNbt = this.cachedBuild.nbt(pos);
            var targetNbt = targetEntity != null ? targetEntity.saveWithoutMetadata(this.level.registryAccess()) : null;
            if (BuildUtil.areEqual(sourceState, sourceNbt, targetState, targetNbt)) {
                score++;
            }
        }
        return score;
    }

    public void calcLastPlayer() {
        int fewestScore = Integer.MAX_VALUE;
        int scoreCount = 0;
        UUID uuid = null;
        for (var u : this.playerDataMap.keySet()) {
            var d = this.playerDataMap.get(u);
            if (d != null && !d.eliminated && d.score <= fewestScore) {
                if (d.score < fewestScore) {
                    fewestScore = d.score;
                    uuid = u;
                    scoreCount = 0;
                }
                scoreCount += 1;
            }
        }
        if (fewestScore == this.maxScore || (scoreCount > 1 && fewestScore > 0)) {
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
        var structure = this.level.getStructureManager().get(this.currentBuild.structure()).orElseThrow();

        for (var playerData : playerDataMap.values()) {
            if (playerData.eliminated) {
                continue;
            }
            playerData.plot.placeBuild(this.level, structure);
        }

        // if the player is inside a block, teleport them
        for (var player : this.space.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (!this.level.getBlockState(player.blockPosition()).isAir() || !this.level.getBlockState(player.blockPosition().above()).isAir()) {
                this.resetPlayer(player, true);
            }
        }
    }

    public void removePlayerBuilds() {
        for (var playerData : this.playerDataMap.values()) {
            if (playerData.eliminated) {
                continue;
            }
            playerData.plot.removeBuild(this.level);
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
            playerData.plot.placeGround(this.level);
        }
        this.centerPlot.placeGround(this.level);

        // pick a new build
        if (this.builds.isEmpty()) {
            this.builds.addAll(this.usedBuilds);
            this.usedBuilds.clear();
        }
        if (this.builds.isEmpty()) {
            throw new GameOpenException(Component.translatable("error.build_rush.build.none.weird"));
        }
        this.currentBuild = this.builds.get(this.level.getRandom().nextInt(this.builds.size()));
        this.builds.remove(this.currentBuild);
        this.usedBuilds.add(this.currentBuild);

        var structure = this.level.getStructureManager().get(this.currentBuild.structure()).orElseThrow();
        this.centerPlot.placeBuild(this.level, structure);
        this.cachedBuild = this.centerPlot.cacheBuild(this.level);
        this.calcInventory();

        // reset scores
        this.loserUuid = null;
        this.maxScore = 0;
        for (var pos : this.cachedBuild.positions()) {
            if (!this.cachedBuild.state(pos).is(BRTags.IGNORED_IN_COMPARISON)) {
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
            var data = this.playerDataMap.get(player.getUUID());
            if (data != null && !data.eliminated) {
                this.resetPlayer(player, true);
            }
        }

        // reset HUD
        this.refreshSidebar();
    }

    public void startMemorizing() {
        this.placePlayerBuilds();
        this.centerPlot.removeBuild(this.level);
        this.centerPlot.placeGround(this.level);
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
        var structure = this.level.getStructureManager().get(this.currentBuild.structure()).orElseThrow();
        this.centerPlot.placeBuild(this.level, structure);

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
            var data = this.playerDataMap.get(player.getUUID());
            if (data == null || data.eliminated) {
                continue;
            }

            if (data.score == this.maxScore) {
                TextUtil.sendSubtitle(player, Component.translatable("title.build_rush.perfect").setStyle(Style.EMPTY.withColor(TextUtil.LEGENDARY).withBold(true)), 0, 3 * 20, 10);
                PlayerUtil.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                float scorePercentage = data.score / (float) this.maxScore;
                String scoreAsPercent = String.format("%.2f", scorePercentage * 100).replaceAll("0*$", "").replaceAll("[,.]$", "");
                var scoreText = Component.translatable("generic.build_rush.score", scoreAsPercent)
                        .setStyle(Style.EMPTY.withColor(TextUtil.lerpScoreColor(scorePercentage)).withBold(true));

                player.sendSystemMessage(TextUtil.translatable(TextUtil.DASH, TextUtil.NEUTRAL, "text.build_rush.score", scoreText), false);
                TextUtil.sendSubtitle(player, scoreText, 0, 2 * 20, 5);
                PlayerUtil.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
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
            this.space.getPlayers().playSound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.MASTER, 1.0f, 1.5f);
        } else {
            var loserData = this.playerDataMap.get(this.loserUuid);
            if (loserData == null) {
                BuildRush.LOGGER.error("Tried to eliminate last player but the player's data was not found!");
                return;
            }
            this.eliminate(loserData);
            perfectRoundsInARow = 0;

            // play an explosion sound and particles
            var center = loserData.plot.buildBounds().center();
            this.level.playSound(null, center.x(), center.y(), center.z(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0f * this.size, 1.0f);
            this.level.sendParticles(ParticleTypes.EXPLOSION,
                    loserData.plot.buildBounds().center().x(), loserData.plot.buildBounds().center().y(), loserData.plot.buildBounds().center().z(), 10,
                    this.size / 1.5f, this.size / 1.5f, this.size / 1.5f, 0.0);
            loserData.plot.placeGround(this.level);
            loserData.plot.removeBuild(this.level);
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
                playerData.plot.placeGround(this.level);
            }
            this.startClosing();
        }
    }
}
