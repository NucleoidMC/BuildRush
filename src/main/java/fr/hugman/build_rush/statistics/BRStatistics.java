package fr.hugman.build_rush.statistics;

import fr.hugman.build_rush.BuildRush;
import xyz.nucleoid.plasmid.game.stats.StatisticKey;

public class BRStatistics {
    public static final StatisticKey<Integer> TOTAL_PERFECT_BUILDS = StatisticKey.intKey(BuildRush.id("total_perfect_builds"));
}