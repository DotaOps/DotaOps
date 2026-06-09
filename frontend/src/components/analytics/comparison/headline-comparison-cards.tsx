import { ComparisonCards } from "@/components/analytics/comparison/comparison-cards";
import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import {
  type PlayerAnalyticsMetric,
  type PlayerComparisonMetric,
  type PlayerComparisonResponse
} from "@/lib/analytics-data";
import { formatPercent } from "@/lib/utils";

function playerHeadlineMetrics(
  metric: PlayerComparisonMetric | null,
  fallback: PlayerAnalyticsMetric | null
) {
  const gamesPlayed = metric?.gamesPlayed ?? fallback?.gamesPlayed;
  const wins = metric?.wins ?? fallback?.wins;
  const losses = metric?.losses ?? fallback?.losses;
  const winRate = metric?.winRate ?? fallback?.winRate;
  const kda = metric?.kda ?? fallback?.kda;
  const avgKills = metric?.avgKills ?? fallback?.avgKills;
  const avgDeaths = metric?.avgDeaths ?? fallback?.avgDeaths;
  const avgAssists = metric?.avgAssists ?? fallback?.avgAssists;
  const avgGpm = metric?.avgGpm ?? fallback?.avgGpm;
  const avgXpm = metric?.avgXpm ?? fallback?.avgXpm;
  const avgHeroDamage = metric?.avgHeroDamage ?? fallback?.avgHeroDamage;

  return [
    { label: "Games", value: countMetricValue(gamesPlayed) },
    { label: "Wins", value: countMetricValue(wins) },
    { label: "Losses", value: countMetricValue(losses) },
    { label: "Win rate", value: typeof winRate === "number" ? formatPercent(winRate) : "No data" },
    { label: "KDA", value: safeMetricNumber(kda, 2) },
    { label: "Avg kills", value: safeMetricNumber(avgKills) },
    { label: "Avg deaths", value: safeMetricNumber(avgDeaths) },
    { label: "Avg assists", value: safeMetricNumber(avgAssists) },
    { label: "GPM", value: safeMetricNumber(avgGpm) },
    { label: "XPM", value: safeMetricNumber(avgXpm) },
    { label: "Last hits", value: safeMetricNumber(metric?.avgLastHits) },
    { label: "Denies", value: safeMetricNumber(metric?.avgDenies) },
    { label: "Net worth", value: safeMetricNumber(metric?.avgNetWorth) },
    { label: "Hero damage", value: safeMetricNumber(avgHeroDamage) },
    { label: "Tower damage", value: safeMetricNumber(metric?.avgTowerDamage) },
    { label: "Hero healing", value: safeMetricNumber(metric?.avgHeroHealing) }
  ];
}

export function HeadlineComparisonCards({
  comparison,
  leftPlayer,
  rightPlayer
}: Readonly<{
  comparison: PlayerComparisonResponse;
  leftPlayer: PlayerAnalyticsMetric;
  rightPlayer: PlayerAnalyticsMetric;
}>) {
  const headline = comparison.headlineComparison;
  const profileA = headline?.profileA ?? null;
  const profileB = headline?.profileB ?? null;

  return (
    <ComparisonCards
      left={{
        metrics: playerHeadlineMetrics(profileA, leftPlayer),
        name: profileA?.displayName ?? leftPlayer.displayName,
        subtitle: leftPlayer.teamName ?? "Selected player"
      }}
      right={{
        metrics: playerHeadlineMetrics(profileB, rightPlayer),
        name: profileB?.displayName ?? rightPlayer.displayName,
        subtitle: rightPlayer.teamName ?? "Selected player"
      }}
    />
  );
}
