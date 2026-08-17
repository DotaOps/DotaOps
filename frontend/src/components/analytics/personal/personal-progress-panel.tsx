import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  countMetricValue,
  formatAnalyticsDateTime
} from "@/components/analytics/analytics-formatters";
import { SectionHeader } from "@/components/section-header";
import type { PlayerProgressPoint } from "@/lib/analytics-data";

export function PersonalProgressPanel({ progress }: Readonly<{ progress: PlayerProgressPoint[] }>) {
  return (
    <div className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Progress trend"
        title="Recent Progress"
        description="Chronological match-by-match progress for your current player profile."
      />
      <PlayerProgressTable progress={progress} />
    </div>
  );
}

function secondsToDuration(value: number | null) {
  if (!value || value <= 0) {
    return "No data";
  }

  const minutes = Math.floor(value / 60);
  const seconds = Math.round(value % 60);
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function progressResultLabel(point: PlayerProgressPoint) {
  if (point.won === true) {
    return "Win";
  }

  if (point.won === false) {
    return "Loss";
  }

  return "Result unavailable";
}

function progressScoreLabel(point: PlayerProgressPoint) {
  if (point.radiantScore === null || point.direScore === null) {
    return null;
  }

  return `Score R-D ${point.radiantScore}-${point.direScore}`;
}

function progressSideContextLabel(point: PlayerProgressPoint) {
  const parts = [
    point.teamSide ? `Side ${point.teamSide}` : null,
    point.winnerSide ? `Winner ${point.winnerSide}` : null,
    progressScoreLabel(point)
  ].filter(Boolean);

  return parts.length > 0 ? parts.join(" / ") : null;
}

function progressResourceContextLabel(point: PlayerProgressPoint) {
  const parts = [
    point.netWorth !== null ? `NW ${countMetricValue(point.netWorth)}` : null,
    point.level !== null ? `Lvl ${point.level}` : null,
    point.durationSeconds !== null ? `Duration ${secondsToDuration(point.durationSeconds)}` : null
  ].filter(Boolean);

  return parts.length > 0 ? parts.join(" / ") : null;
}

function PlayerProgressTable({ progress }: Readonly<{ progress: PlayerProgressPoint[] }>) {
  if (progress.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No progress data yet."
        detail="Progress rows will appear after imported matches link to your player profile."
      />
    );
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Played</th>
            <th>Hero</th>
            <th>Result</th>
            <th>KDA</th>
            <th>Economy</th>
            <th>Impact</th>
          </tr>
        </thead>
        <tbody>
          {progress.map((point, index) => {
            const sideContext = progressSideContextLabel(point);
            const resourceContext = progressResourceContextLabel(point);

            return (
              <tr key={`${point.matchId ?? "match"}-${point.matchGameId ?? index}`}>
                <td>
                  <strong>{formatAnalyticsDateTime(point.playedAt)}</strong>
                  <span>{point.dotaMatchId ? `Dota ${point.dotaMatchId}` : point.matchId ?? "Match ID unavailable"}</span>
                  {sideContext ? <span className="analytics-progress-context">{sideContext}</span> : null}
                </td>
                <td>
                  <strong>{point.heroName ?? "Hero unavailable"}</strong>
                  <span>{point.heroId ?? "Hero ID unavailable"}</span>
                </td>
                <td>
                  <strong>{progressResultLabel(point)}</strong>
                  <span>{point.kills}-{point.deaths}-{point.assists}</span>
                </td>
                <td>{point.kda.toFixed(2)}</td>
                <td>
                  <strong>{countMetricValue(point.goldPerMin)} / {countMetricValue(point.xpPerMin)}</strong>
                  <span>LH/DN {countMetricValue(point.lastHits)} / {countMetricValue(point.denies)}</span>
                  {resourceContext ? <span className="analytics-progress-context">{resourceContext}</span> : null}
                </td>
                <td>
                  <strong>{countMetricValue(point.heroDamage)} hero / {countMetricValue(point.towerDamage)} tower</strong>
                  <span>Healing {countMetricValue(point.heroHealing)}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
