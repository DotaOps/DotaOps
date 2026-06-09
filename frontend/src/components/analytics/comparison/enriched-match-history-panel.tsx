import { MatchHistoryList } from "@/components/analytics/analytics-tables";
import {
  countMetricValue,
  formatAnalyticsDateTime,
  matchTeamsLabel,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import { SectionHeader } from "@/components/section-header";
import {
  type AnalyticsMatchHistory,
  type PlayerComparisonMatch,
  type PlayerComparisonMatchPlayer
} from "@/lib/analytics-data";

function comparisonMatchResult(player: PlayerComparisonMatchPlayer | null) {
  if (!player) {
    return "No player row";
  }

  if (player.won === true) {
    return "Win";
  }

  if (player.won === false) {
    return "Loss";
  }

  return "Result unavailable";
}

function comparisonMatchPlayerCell(player: PlayerComparisonMatchPlayer | null, fallbackName: string) {
  if (!player) {
    return (
      <>
        <strong>{fallbackName}</strong>
        <span>No player match row</span>
      </>
    );
  }

  return (
    <>
      <strong>{player.heroName ?? "Hero unavailable"} / {comparisonMatchResult(player)}</strong>
      <span>K/D/A {player.kills}-{player.deaths}-{player.assists} / KDA {safeMetricNumber(player.kda, 2)}</span>
      <span>GPM/XPM {countMetricValue(player.goldPerMin)} / {countMetricValue(player.xpPerMin)}</span>
      <span>{countMetricValue(player.heroDamage)} hero / {countMetricValue(player.towerDamage)} tower / heal {countMetricValue(player.heroHealing)}</span>
      <span>Net worth {countMetricValue(player.netWorth)} / side {player.teamSide ?? "unknown"}</span>
    </>
  );
}

function EnrichedMatchHistoryTable({
  fallbackMatches,
  leftName,
  matches,
  rightName
}: Readonly<{
  fallbackMatches: AnalyticsMatchHistory[];
  leftName: string;
  matches: PlayerComparisonMatch[];
  rightName: string;
}>) {
  if (matches.length === 0) {
    return <MatchHistoryList emptyText="No shared matches returned." matches={fallbackMatches} />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Match</th>
            <th>{leftName}</th>
            <th>{rightName}</th>
            <th>Context</th>
          </tr>
        </thead>
        <tbody>
          {matches.slice(0, 12).map((match, index) => (
            <tr key={`${match.matchId ?? match.dotaMatchId ?? "match"}-${match.matchGameId ?? index}`}>
              <td>
                <strong>{match.dotaMatchId ? `Dota ${match.dotaMatchId}` : match.matchId ?? "Match unavailable"}</strong>
                <span>{formatAnalyticsDateTime(match.playedAt)}</span>
              </td>
              <td>{comparisonMatchPlayerCell(match.profileA, leftName)}</td>
              <td>{comparisonMatchPlayerCell(match.profileB, rightName)}</td>
              <td>
                <strong>{match.tournamentName ?? "Tournament unavailable"}</strong>
                <span>{matchTeamsLabel({
                  teamAId: match.teamAId,
                  teamAName: match.teamAName,
                  teamBId: match.teamBId,
                  teamBName: match.teamBName,
                  winnerTeamId: match.winnerTeamId
                })}</span>
                <span>Winner side {match.winnerSide ?? "unknown"}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function EnrichedMatchHistoryPanel({
  fallbackMatches,
  leftName,
  matches,
  rightName
}: Readonly<{
  fallbackMatches: AnalyticsMatchHistory[];
  leftName: string;
  matches: PlayerComparisonMatch[];
  rightName: string;
}>) {
  return (
    <div className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Recent matches"
        title="Shared Match History"
        description="Matches where both compared players appear in normalized analytics data."
      />
      <EnrichedMatchHistoryTable
        fallbackMatches={fallbackMatches}
        leftName={leftName}
        matches={matches}
        rightName={rightName}
      />
    </div>
  );
}
