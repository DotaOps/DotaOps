import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  countMetricValue,
  formatAnalyticsDateTime,
  formatAnalyticsDuration,
  formatAnalyticsEnum,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type { HeroMasteryRecentMatch } from "@/lib/analytics-data";

export function HeroMasteryRecentMatches({
  matches
}: Readonly<{
  matches: HeroMasteryRecentMatch[];
}>) {
  if (matches.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No recent hero matches returned."
        detail="Recent hero match rows appear after imported OpenDota matches are linked to this hero."
      />
    );
  }

  return (
    <section className="analytics-mastery-card">
      <div className="analytics-mastery-card-header">
        <div>
          <span className="ops-label">Recent hero matches</span>
          <strong>Raw Match History</strong>
        </div>
        <span className="ops-badge">{matches.length} rows</span>
      </div>
      <div className="analytics-real-table-wrap">
        <table className="analytics-real-table analytics-mastery-matches-table">
          <thead>
            <tr>
              <th>Match</th>
              <th>Result</th>
              <th>K/D/A</th>
              <th>Economy</th>
              <th>Resources</th>
              <th>Impact</th>
              <th>Map context</th>
              <th>Context weight</th>
            </tr>
          </thead>
          <tbody>
            {matches.map((match, index) => (
              <tr key={`${match.matchId ?? "match"}-${match.matchGameId ?? index}`}>
                <td>
                  <strong>{match.dotaMatchId ? `Dota ${match.dotaMatchId}` : match.matchId ?? "Match unavailable"}</strong>
                  <span>{formatAnalyticsDateTime(match.playedAt)}</span>
                  <span>{match.matchGameId ?? "Game ID unavailable"}</span>
                </td>
                <td>
                  <strong>{resultLabel(match.won)}</strong>
                  <span>KDA {safeMetricNumber(match.kda, 2)}</span>
                </td>
                <td>{match.kills}-{match.deaths}-{match.assists}</td>
                <td>
                  <strong>{countMetricValue(match.goldPerMin)} / {countMetricValue(match.xpPerMin)}</strong>
                  <span>GPM / XPM</span>
                </td>
                <td>
                  <strong>LH/DN {countMetricValue(match.lastHits)} / {countMetricValue(match.denies)}</strong>
                  <span>NW {countMetricValue(match.netWorth)} / Lvl {countMetricValue(match.level)}</span>
                  <span>Duration {formatAnalyticsDuration(match.durationSeconds)}</span>
                </td>
                <td>
                  <strong>{countMetricValue(match.heroDamage)} hero / {countMetricValue(match.towerDamage)} tower</strong>
                  <span>Healing {countMetricValue(match.heroHealing)}</span>
                </td>
                <td>
                  <strong>{sideLabel(match.teamSide, match.winnerSide)}</strong>
                  <span>{scoreLabel(match.radiantScore, match.direScore)}</span>
                </td>
                <td>
                  <div className="analytics-mastery-match-context">
                    <strong>{safeMetricNumber(match.contextWeight, 2)}</strong>
                    <span>{formatAnalyticsEnum(match.contextClassification)}</span>
                    {match.contextMessage ? <em>{match.contextMessage}</em> : null}
                    {match.contextReasons.length > 0 ? (
                      <div className="analytics-mastery-match-reasons">
                        {match.contextReasons.map((reason) => (
                          <span className="ops-badge" key={reason}>{formatAnalyticsEnum(reason)}</span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function resultLabel(won: boolean | null) {
  if (won === true) {
    return "Win";
  }

  if (won === false) {
    return "Loss";
  }

  return "Result unavailable";
}

function sideLabel(teamSide: string | null, winnerSide: string | null) {
  const parts = [
    teamSide ? `Side ${formatAnalyticsEnum(teamSide)}` : null,
    winnerSide ? `Winner ${formatAnalyticsEnum(winnerSide)}` : null
  ].filter(Boolean);

  return parts.length > 0 ? parts.join(" / ") : "Side unavailable";
}

function scoreLabel(radiantScore: number | null, direScore: number | null) {
  if (radiantScore === null || direScore === null) {
    return "Score unavailable";
  }

  return `R-D ${radiantScore}-${direScore}`;
}
