import { HeroMatrix } from "@/components/analytics/analytics-tables";
import { safeMetricNumber } from "@/components/analytics/analytics-formatters";
import {
  type HeroAnalyticsMetric,
  type PlayerComparisonSharedHero
} from "@/lib/analytics-data";
import { formatPercent } from "@/lib/utils";

function heroStatsSummary(stats: PlayerComparisonSharedHero["profileA"]) {
  if (!stats) {
    return (
      <>
        <strong>No data</strong>
        <span>Hero row unavailable</span>
      </>
    );
  }

  return (
    <>
      <strong>{stats.gamesPlayed} games / {formatPercent(stats.winRate)} WR</strong>
      <span>KDA {safeMetricNumber(stats.kda, 2)} / deaths {safeMetricNumber(stats.avgDeaths)}</span>
      <span>GPM/XPM {safeMetricNumber(stats.avgGpm)} / {safeMetricNumber(stats.avgXpm)}</span>
      <span>{Math.round(stats.avgHeroDamage).toLocaleString("en-US")} hero / {Math.round(stats.avgTowerDamage).toLocaleString("en-US")} tower</span>
    </>
  );
}

function signedMetric(value: number | null | undefined, digits = 1, suffix = "") {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return "No data";
  }

  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(digits)}${suffix}`;
}

export function SharedHeroComparisonTable({
  fallbackHeroes,
  heroes
}: Readonly<{
  fallbackHeroes: HeroAnalyticsMetric[];
  heroes: PlayerComparisonSharedHero[];
}>) {
  if (heroes.length === 0) {
    return <HeroMatrix heroes={fallbackHeroes} />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Hero</th>
            <th>Profile A</th>
            <th>Profile B</th>
            <th>Delta</th>
          </tr>
        </thead>
        <tbody>
          {heroes.slice(0, 12).map((hero) => {
            const smallestSample = Math.min(
              hero.profileA?.gamesPlayed ?? 0,
              hero.profileB?.gamesPlayed ?? 0
            );

            return (
              <tr key={`${hero.heroId ?? hero.heroName ?? "hero"}-${hero.profileA?.profileId ?? "a"}-${hero.profileB?.profileId ?? "b"}`}>
                <td>
                  <strong>{hero.heroName ?? "Hero unavailable"}</strong>
                  <span>{smallestSample < 3 ? "Small sample" : hero.heroId ?? "Shared hero"}</span>
                </td>
                <td>{heroStatsSummary(hero.profileA)}</td>
                <td>{heroStatsSummary(hero.profileB)}</td>
                <td>
                  <strong>WR {signedMetric(hero.delta?.winRate, 1, "%")}</strong>
                  <span>KDA {signedMetric(hero.delta?.kda, 2)}</span>
                  <span>GPM/XPM {signedMetric(hero.delta?.avgGpm)} / {signedMetric(hero.delta?.avgXpm)}</span>
                  <span>Damage {signedMetric(hero.delta?.avgHeroDamage)}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
