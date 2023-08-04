package fr.hugman.build_rush.map;

import fr.hugman.build_rush.BRConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplateSerializer;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record BRMap(Plot centerPlot, List<Plot> plots, RuntimeWorldConfig worldConfig) {
    public static BRMap from(GameOpenContext<BRConfig> context) throws IOException {
        var server = context.server();
        var config = context.config();

        var template = MapTemplateSerializer.loadFromResource(server, config.mapConfig().template());
        var metadata = template.getMetadata();

        var worldConfig = new RuntimeWorldConfig().setGenerator(new TemplateChunkGenerator(server, template))
                .setGameRule(GameRules.DO_FIRE_TICK, false)
                .setGameRule(GameRules.FIRE_DAMAGE, false)
                .setGameRule(GameRules.FREEZE_DAMAGE, false)
                .setGameRule(GameRules.DO_MOB_GRIEFING, false)
                .setGameRule(GameRules.DO_MOB_SPAWNING, false)
                .setGameRule(GameRules.RANDOM_TICK_SPEED, 0)
                .setGameRule(GameRules.WATER_SOURCE_CONVERSION, false)
                .setGameRule(GameRules.LAVA_SOURCE_CONVERSION, false);

        var centerPlotBounds = metadata.getFirstRegionBounds("center_plot");
        if (centerPlotBounds == null) {
            throw new GameOpenException(Text.translatable("error.build_rush.mapConfig.center_plot.not_found"));
        }
        var centerPlot = Plot.of(centerPlotBounds);

        final int size = centerPlotBounds.size().getX()+1;
        validateBounds(centerPlotBounds, size);

        var plotBoundss = metadata.getRegionBounds("plot").toList();
        if (plotBoundss.size() < config.playerConfig().maxPlayers()) {
            throw new GameOpenException(Text.translatable("error.build_rush.mapConfig.plots.not_enough", plotBoundss.size(), config.playerConfig().maxPlayers()));
        }

        List<Plot> plots = new ArrayList<>();
        for(var plotBounds : plotBoundss) {
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
            throw new GameOpenException(Text.translatable("error.build_rush.mapConfig.plot.wrong_size", x, z));
        }
        if(y != 1) {
            throw new GameOpenException(Text.translatable("error.build_rush.mapConfig.plot.wrong_height", y));
        }
        if(x != size) {
            throw new GameOpenException(Text.translatable("error.build_rush.mapConfig.plot.wrong_size_as_center", x, size));
        }
    }

    public void cachePlotGrounds(ServerWorld world) {
        centerPlot.cacheGround(world);
        for(var plot : plots) {
            plot.cacheGround(world);
        }
    }
}
