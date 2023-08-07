package fr.hugman.build_rush.game.state;

import fr.hugman.build_rush.BRConfig;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import fr.hugman.build_rush.map.BRMap;
import fr.hugman.build_rush.registry.BRRegistries;
import fr.hugman.build_rush.registry.tag.BRTags;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BRWaiting {
    public static GameOpenProcedure open(GameOpenContext<BRConfig> context) {
        BRConfig config = context.config();
        BRMap map;

        try {
            map = BRMap.from(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return context.openWithWorld(map.worldConfig(), (activity, world) -> {
            GameWaitingLobby.addTo(activity, config.playerConfig());
            map.cachePlotGrounds(world);

            var builds = getBuilds(map.centerPlot().buildBounds().size().getX()+1, config, world);
            var spawnPos = map.centerPlot().groundBounds().center().add(0, 1, 0);

            activity.listen(PlayerDamageEvent.EVENT, (player, source, amount) -> {
                if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
                    resetPlayer(player, world, spawnPos);
                }
                return ActionResult.FAIL;
            });
            activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
                resetPlayer(player, world, spawnPos);
                return ActionResult.FAIL;
            });

            activity.listen(GamePlayerEvents.OFFER, offer -> offer.accept(world, spawnPos).and(() -> resetPlayer(offer.player(), world, spawnPos)));

            activity.listen(GameActivityEvents.REQUEST_START, () -> {
                BRActive.create(config, activity.getGameSpace(), world, map, builds);
                return GameResult.ok();
            });
        });
    }

    public static void resetPlayer(ServerPlayerEntity player, World world, Vec3d pos) {
        player.teleport(pos.getX(), pos.getY(), pos.getZ());

        player.setHealth(20.0f);
        player.changeGameMode(GameMode.ADVENTURE);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
    }

    public static List<Build> getBuilds(int buildSize, BRConfig config, ServerWorld world) {
        var structureManager = world.getStructureTemplateManager();
        var registryManager = world.getRegistryManager();

        List<Build> builds = new ArrayList<>();
        var buildEntries = config.builds()
                .orElse(registryManager.get(BRRegistries.BUILD).getEntryList(BRTags.GENERIC)
                        .orElseThrow(() -> new GameOpenException(Text.translatable("error.build_rush.tag.generic.not_found"))));

        for (RegistryEntry<Build> buildEntry : buildEntries) {
            // Get the plot structure
            var build = buildEntry.value();
            if (buildEntry.isIn(BRTags.BLACKLIST)) {
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
            throw new GameOpenException(Text.translatable("error.build_rush.build.none"));
        }
        return builds;
    }

    @Nullable
    public static StructureTemplate getAndAssertStructure(Identifier id, StructureTemplateManager manager) {
        var template = manager.getTemplate(id).orElseThrow(() -> new GameOpenException(Text.translatable("structure_block.load_not_found", id.toString())));

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
