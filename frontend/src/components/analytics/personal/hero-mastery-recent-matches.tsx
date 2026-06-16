import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  countMetricValue,
  formatAnalyticsDateTime,
  formatAnalyticsDuration,
  formatAnalyticsEnum,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type { HeroMasteryRecentMatch } from "@/lib/analytics-data";
import { classNames } from "@/lib/utils";

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
              <th>Impact</th>
              <th>Context</th>
            </tr>
          </thead>
          <tbody>
            {matches.map((match, index) => (
              <tr key={`${match.matchId ?? "match"}-${match.matchGameId ?? index}`}>
                <td>
                  <strong>{formatAnalyticsDateTime(match.playedAt)}</strong>
                  <span>{match.dotaMatchId ? `Dota ${match.dotaMatchId}` : match.matchId ?? "Match unavailable"}</span>
                  <span>{match.matchGameId ?? "Game ID unavailable"}</span>
                </td>
                <td>
                  <strong className={classNames("analytics-match-result-text", resultClass(match.won))}>
                    {resultLabel(match.won)}
                  </strong>
                  <span>{sideLabel(match.teamSide, match.winnerSide)}</span>
                  <span>{scoreLabel(match.radiantScore, match.direScore)}</span>
                  <span>Duration {formatAnalyticsDuration(match.durationSeconds)}</span>
                </td>
                <td>
                  <strong>{match.kills}-{match.deaths}-{match.assists}</strong>
                  <span>KDA {safeMetricNumber(match.kda, 2)}</span>
                </td>
                <td>
                  <strong>{countMetricValue(match.goldPerMin)} / {countMetricValue(match.xpPerMin)}</strong>
                  <span>GPM / XPM</span>
                  <span>LH/DN {countMetricValue(match.lastHits)} / {countMetricValue(match.denies)}</span>
                  <span>NW {countMetricValue(match.netWorth)} / Lvl {countMetricValue(match.level)}</span>
                </td>
                <td>
                  <strong>{countMetricValue(match.heroDamage)} hero</strong>
                  <span>{countMetricValue(match.towerDamage)} tower damage</span>
                  <span>{countMetricValue(match.heroHealing)} healing</span>
                </td>
                <td>
                  <div className="analytics-mastery-match-context">
                    <strong>Weight {safeMetricNumber(match.contextWeight, 2)}</strong>
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

function resultClass(won: boolean | null) {
  if (won === true) {
    return "is-win";
  }

  if (won === false) {
    return "is-loss";
  }

  return "is-unknown";
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
