package fr.hugman.build_rush.statistics;

import fr.hugman.build_rush.BuildRush;
import xyz.nucleoid.plasmid.game.stats.StatisticKey;

public class BRStatistics {
    public static final StatisticKey<Integer> SURVIVED_ROUNDS = StatisticKey.intKey(BuildRush.id("survived_rounds"));
    public static final StatisticKey<Integer> PERFECT_ROUNDS = StatisticKey.intKey(BuildRush.id("perfect_rounds"));

    public static final StatisticKey<Integer> TOTAL_ROUNDS = StatisticKey.intKey(BuildRush.id("total_rounds"));
}