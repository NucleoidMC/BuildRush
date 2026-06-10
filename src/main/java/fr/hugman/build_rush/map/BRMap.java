package fr.hugman.build_rush.map;

import fr.hugman.build_rush.BRConfig;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import xyz.nucleoid.plasmid.api.game.level.generator.TemplateChunkGenerator;

public record BRMap(Plot centerPlot, List<Plot> plots, RuntimeLevelConfig worldConfig) {
    public static BRMap from(GameOpenContext<BRConfig> context) throws IOException {
        var server = context.server();
        var config = context.config();

        var template = MapTemplateSerializer.loadFromResource(server, config.mapConfig().template());
        var metadata = template.getMetadata();

        var worldConfig = new RuntimeLevelConfig().setGenerator(new TemplateChunkGenerator(server, template))
                .setGameRule(GameRules.FIRE_DAMAGE, false)
                .setGameRule(GameRules.FREEZE_DAMAGE, false)
                .setGameRule(GameRules.MOB_GRIEFING, false)
                .setGameRule(GameRules.SPAWN_MOBS, false)
                .setGameRule(GameRules.SPAWN_MONSTERS, false)
                .setGameRule(GameRules.RANDOM_TICK_SPEED, 0)
                .setGameRule(GameRules.WATER_SOURCE_CONVERSION, false)
                .setGameRule(GameRules.LAVA_SOURCE_CONVERSION, false);

        var centerPlotBounds = metadata.getFirstRegionBounds("center_plot");
        if (centerPlotBounds == null) {
            throw new GameOpenException(Component.translatable("error.build_rush.mapConfig.center_plot.not_found"));
        }
        var centerPlot = Plot.of(centerPlotBounds);

        final int size = centerPlotBounds.size().getX()+1;
        validateBounds(centerPlotBounds, size);

        var plotBoundsList = metadata.getRegionBounds("plot").toList();
        config.playerConfig().playerConfig().maxPlayers().ifPresent(value -> {
            if (plotBoundsList.size() > value) {
                throw new GameOpenException(Component.translatable("error.build_rush.mapConfig.plots.too_much", plotBoundsList.size(), value));
            }
        });

        List<Plot> plots = new ArrayList<>();
        for(var plotBounds : plotBoundsList) {
            validateBounds(plotBounds, size);
            plots.add(Plot.of(plotBounds));
        }

        return new BRMap(centerPlot, plots, worldConfig);
    }

    private static void validateBounds(BlockBounds plot, int size) {
        int x = plot.size().getX()+1;
        int y = plot.size().getY()+1;
        int z = plot.size().getZ()+1;

        if(x != z) {
            throw new GameOpenException(Component.translatable("error.build_rush.mapConfig.plot.wrong_size", x, z));
        }
        if(y != 1) {
            throw new GameOpenException(Component.translatable("error.build_rush.mapConfig.plot.wrong_height", y));
        }
        if(x != size) {
            throw new GameOpenException(Component.translatable("error.build_rush.mapConfig.plot.wrong_size_as_center", x, size));
        }
    }

    public void cachePlotGrounds(ServerLevel world) {
        centerPlot.cacheGround(world);
        for(var plot : plots) {
            plot.cacheGround(world);
        }
    }
}
