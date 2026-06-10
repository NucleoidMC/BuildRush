package fr.hugman.build_rush.game.state;

import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.map.BRMap;
import fr.hugman.build_rush.registry.BRRegistries;
import fr.hugman.build_rush.registry.tag.BRTags;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenException;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;

public class BRWaiting {
    public static GameOpenProcedure open(GameOpenContext<BRConfig> context) {
        BRConfig config = context.config();
        BRMap map;

        try {
            map = BRMap.from(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return context.openWithLevel(map.worldConfig(), (activity, world) -> {
            GameWaitingLobby.addTo(activity, config.playerConfig());
            map.cachePlotGrounds(world);

            var builds = getBuilds(map.centerPlot().buildBounds().size().getX()+1, config, world);
            var spawnPos = map.centerPlot().groundBounds().center().add(0, 1, 0);

            activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
                if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
                    resetPlayer(player, world, spawnPos);
                }
                return EventResult.DENY;
            });
            activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
                resetPlayer(player, world, spawnPos);
                return EventResult.DENY;
            });

            activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
            activity.listen(GamePlayerEvents.ACCEPT, offer -> offer.teleport(world, spawnPos).thenRunForEach((player) -> resetPlayer(player, world, spawnPos)));

            activity.listen(GameActivityEvents.REQUEST_START, () -> {
                BRActive.create(config, activity.getGameSpace(), world, map, builds);
                return GameResult.ok();
            });
        });
    }

    public static void resetPlayer(ServerPlayer player, Level world, Vec3 pos) {
        player.randomTeleport(pos.x(), pos.y(), pos.z(), false);

        player.setHealth(20.0f);
        player.setGameMode(GameType.ADVENTURE);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
    }

    public static List<Build> getBuilds(int buildSize, BRConfig config, ServerLevel world) {
        var structureManager = world.getStructureManager();
        var registryManager = world.registryAccess();

        List<Build> builds = new ArrayList<>();
        var buildEntries = config.builds()
                .orElse(registryManager.lookupOrThrow(BRRegistries.BUILD).get(BRTags.GENERIC)
                        .orElseThrow(() -> new GameOpenException(Component.translatable("error.build_rush.tag.generic.not_found"))));

        for (Holder<Build> buildEntry : buildEntries) {
            // Get the plot structure
            var build = buildEntry.value();
            if (buildEntry.is(BRTags.BLACKLIST)) {
                // TODO: fix #29
                BuildRush.LOGGER.warn("Build is in the blacklist! Skipping: " + buildEntry);
                continue;
            }

            // Verify that the structure is here and is of the correct size
            var structure = getAndAssertStructure(build.structure(), structureManager);

            if (structure == null || structure.getSize().getX() != buildSize) {
                continue;
            }
            builds.add(build);
        }
        if (builds.isEmpty()) {
            throw new GameOpenException(Component.translatable("error.build_rush.build.none"));
        }
        return builds;
    }

    @Nullable
    public static StructureTemplate getAndAssertStructure(Identifier id, StructureTemplateManager manager) {
        var template = manager.get(id).orElseThrow(() -> new GameOpenException(Component.translatable("structure_block.load_not_found", id.toString())));

        int x = template.getSize().getX();
        int y = template.getSize().getY();
        int z = template.getSize().getZ();

        if (x != z) {
            BuildRush.LOGGER.warn("Build structure " + id.toString() + " has an invalid width and length (x=" + x + ", z=" + z + ") and cannot be loaded. It should be square (x = y).");
            return null;
        }
        if (y != x && y != x + 1) {
            BuildRush.LOGGER.warn("Build structure " + id.toString() + " has an invalid height (" + y + ", should be " + x + " or " + (x + 1) + ") and cannot be loaded.");
            return null;
        }

        return template;
    }
}
