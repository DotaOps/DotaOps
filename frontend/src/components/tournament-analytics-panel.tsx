import { BarChart3, Clock3, DatabaseZap, ShieldCheck, Sparkles, UsersRound } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { SectionHeader } from "@/components/section-header";
import type { TournamentAnalyticsMetric } from "@/lib/analytics-data";

function secondsToDuration(value: number | null) {
  if (!value || value <= 0) {
    return "No data";
  }

  const minutes = Math.floor(value / 60);
  const seconds = Math.round(value % 60);
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

interface TournamentAnalyticsPanelProps {
  error?: string | null;
  metrics: TournamentAnalyticsMetric | null;
}

export function TournamentAnalyticsPanel({
  error,
  metrics
}: TournamentAnalyticsPanelProps) {
  const hasData = Boolean(metrics && metrics.gamesPlayed > 0);

  return (
    <section className="tournament-command-panel tournament-analytics-panel tournament-real-analytics-panel ops-panel">
      <SectionHeader
        eyebrow="Tournament Analytics"
        title="Backend Calculated Metrics"
        description="Read-only public analytics from imported OpenDota match data."
        action={
          <span className="ops-badge">
            <ShieldCheck size={14} />
            Backend calculated
          </span>
        }
      />

      {error ? (
        <div className="tournament-analytics-state">
          <strong>Analytics unavailable.</strong>
          <p>{error}</p>
        </div>
      ) : null}

      {!error && !hasData ? (
        <div className="tournament-analytics-state">
          <strong>No imported match analytics yet.</strong>
          <p>Import OpenDota matches first. Analytics will appear after backend processing.</p>
        </div>
      ) : null}

      {metrics ? (
        <>
          <div className="tournament-analytics-summary">
            <AnalyticsFact icon={DatabaseZap} label="Analyzed Matches" value={String(metrics.gamesPlayed || "No data")} />
            <AnalyticsFact icon={Clock3} label="Avg Duration" value={secondsToDuration(metrics.avgDurationSeconds)} />
            <AnalyticsFact icon={BarChart3} label="Avg KDA" value={metrics.avgKda > 0 ? metrics.avgKda.toFixed(2) : "No data"} />
            <AnalyticsFact icon={UsersRound} label="Teams" value={String(metrics.teamsCount || "No data")} />
            <AnalyticsFact icon={Sparkles} label="Heroes Picked" value={String(metrics.heroesPickedCount || "No data")} />
          </div>

          <div className="tournament-picked-heroes">
            <span className="ops-label">Most Picked Heroes</span>
            {metrics.mostPickedHeroes.length > 0 ? (
              <div>
                {metrics.mostPickedHeroes.map((hero) => (
                  <article key={hero.heroId}>
                    <strong>{hero.localizedName}</strong>
                    <span className="ops-mono">{hero.picks} picks</span>
                  </article>
                ))}
              </div>
            ) : (
              <p>No hero pick metrics available.</p>
            )}
          </div>
        </>
      ) : null}
    </section>
  );
}

function AnalyticsFact({
  icon: Icon,
  label,
  value
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <article>
      <Icon size={17} />
      <span className="ops-label">{label}</span>
      <strong className="ops-data">{value}</strong>
    </article>
  );
}
