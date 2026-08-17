package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record HeroMasteryResponse(
        UUID profileId,
        UUID heroId,
        String heroName,
        int games,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal kda,
        BigDecimal avgGoldPerMin,
        BigDecimal avgXpPerMin,
        BigDecimal avgLastHits,
        BigDecimal avgDenies,
        BigDecimal avgNetWorth,
        BigDecimal avgHeroDamage,
        BigDecimal avgTowerDamage,
        BigDecimal avgHeroHealing,
        BigDecimal avgLevel,
        List<HeroMasteryRecentMatchResponse> recentMatches,
        HeroMasteryRecentTrendResponse recentTrend,
        List<HeroMasteryMetricComparisonResponse> comparisonToPlayerOverallBaseline,
        HeroMasteryContextSummaryResponse contextSummary,
        HeroMasteryVerdict masteryVerdict,
        List<HeroMasteryNoteResponse> deterministicNotes
) {
}
