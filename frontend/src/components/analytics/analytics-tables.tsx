import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  formatAnalyticsDateTime,
  matchTeamsLabel,
  matchWinnerLabel,
  playerHeroReferenceLabel,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import {
  type AnalyticsMatchHistory,
  type HeroAnalyticsMetric,
  type PlayerHeroPerformance
} from "@/lib/analytics-data";
import { formatPercent } from "@/lib/utils";

export function MatchHistoryList({
  emptyText,
  matches
}: Readonly<{
  emptyText: string;
  matches: AnalyticsMatchHistory[];
}>) {
  if (matches.length === 0) {
    return <AnalyticsEmptyBlock title={emptyText} detail="Match history rows will appear after imported match records are processed." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Dota Match ID</th>
            <th>Tournament</th>
            <th>Teams</th>
            <th>Game</th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match, index) => (
            <tr key={`${match.matchId ?? "match"}-${match.matchGameId ?? index}`}>
              <td>
                <strong>{match.dotaMatchId ?? "No data"}</strong>
                <span>Official match history</span>
              </td>
              <td>
                <strong>{match.tournamentName ?? "Tournament unavailable"}</strong>
                <span>{formatAnalyticsDateTime(match.playedAt)}</span>
              </td>
              <td>
                <strong>{matchTeamsLabel(match)}</strong>
                <span>{matchWinnerLabel(match)}</span>
              </td>
              <td>
                <strong>{match.matchGameId ?? "No game ID"}</strong>
                <span>{match.matchId ?? "No match ID"}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function PlayerHeroPerformanceTable({ heroes }: Readonly<{ heroes: PlayerHeroPerformance[] }>) {
  if (heroes.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No hero-specific analytics yet."
        detail="Hero breakdown rows will appear after imported matches link heroes to your player profile."
      />
    );
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Hero</th>
            <th>Matches</th>
            <th>W-L</th>
            <th>Win Rate</th>
            <th>Avg KDA</th>
            <th>Economy</th>
            <th>Impact</th>
            <th>Recent / Best</th>
          </tr>
        </thead>
        <tbody>
          {heroes.slice(0, 12).map((hero) => (
            <tr key={`${hero.heroId ?? hero.heroName ?? "hero"}-${hero.recentMatchId ?? "recent"}`}>
              <td>
                <strong>{hero.heroName ?? "Hero unavailable"}</strong>
                <span>{hero.heroId ?? "Hero ID unavailable"}</span>
              </td>
              <td>{hero.matches}</td>
              <td>{hero.wins}-{hero.losses}</td>
              <td>{formatPercent(hero.winRate)}</td>
              <td>
                <strong>{safeMetricNumber(hero.avgKda, 2)}</strong>
                <span>{safeMetricNumber(hero.avgKills)} / {safeMetricNumber(hero.avgDeaths)} / {safeMetricNumber(hero.avgAssists)}</span>
              </td>
              <td>
                <strong>{safeMetricNumber(hero.avgGpm)} / {safeMetricNumber(hero.avgXpm)}</strong>
                <span>LH/DN {safeMetricNumber(hero.avgLastHits)} / {safeMetricNumber(hero.avgDenies)}</span>
              </td>
              <td>
                <strong>{Math.round(hero.avgHeroDamage).toLocaleString("en-US")} hero / {Math.round(hero.avgTowerDamage).toLocaleString("en-US")} tower</strong>
                <span>Healing {Math.round(hero.avgHeroHealing).toLocaleString("en-US")}</span>
              </td>
              <td>
                <strong>{formatAnalyticsDateTime(hero.recentPlayedAt)}</strong>
                <span>Best KDA {safeMetricNumber(hero.bestKda, 2)} / {hero.bestDotaMatchId ?? playerHeroReferenceLabel(hero)}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function HeroMatrix({ heroes }: Readonly<{ heroes: HeroAnalyticsMetric[] }>) {
  if (heroes.length === 0) {
    return <AnalyticsEmptyBlock title="No hero metrics available." detail="Hero performance rows will appear after imported matches include hero data." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Hero</th>
            <th>Games</th>
            <th>W-L</th>
            <th>Win Rate</th>
            <th>KDA</th>
            <th>Avg Damage</th>
          </tr>
        </thead>
        <tbody>
          {heroes.slice(0, 12).map((hero) => (
            <tr key={`${hero.heroId}-${hero.tournamentId ?? "global"}`}>
              <td>
                <strong>{hero.localizedName}</strong>
                <span>{hero.tournamentName ?? "Tournament aggregate"}</span>
              </td>
              <td>{hero.gamesPlayed}</td>
              <td>{hero.wins}-{hero.losses}</td>
              <td>{formatPercent(hero.winRate)}</td>
              <td>{hero.kda.toFixed(2)}</td>
              <td>{Math.round(hero.avgHeroDamage).toLocaleString("en-US")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
