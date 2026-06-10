import {
  Activity,
  BarChart3,
  DatabaseZap,
  Swords,
  Trophy
} from "lucide-react";

import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import { SectionHeader } from "@/components/section-header";
import { TelemetryCard } from "@/components/telemetry-card";
import type { PlayerAnalyticsMetric } from "@/lib/analytics-data";
import { formatPercent } from "@/lib/utils";

export function PersonalSummaryPanel({ metric }: Readonly<{ metric: PlayerAnalyticsMetric | null }>) {
  return (
    <div className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Personal summary"
        title="Headline Metrics"
        description="Compact personal performance summary from the protected player analytics endpoint."
      />
      {metric ? (
        <section className="analytics-telemetry-grid personal-summary-grid">
          <TelemetryCard
            icon={DatabaseZap}
            label="Games played"
            value={countMetricValue(metric.gamesPlayed)}
            delta={`${metric.wins}-${metric.losses} W-L`}
            tone="cyan"
          />
          <TelemetryCard
            icon={Trophy}
            label="Win rate"
            value={formatPercent(metric.winRate)}
            delta="personal matches"
            tone="green"
          />
          <TelemetryCard
            icon={Swords}
            label="KDA"
            value={metric.kda.toFixed(2)}
            delta={`${safeMetricNumber(metric.avgKills)} / ${safeMetricNumber(metric.avgDeaths)} / ${safeMetricNumber(metric.avgAssists)}`}
            tone="gold"
          />
          <TelemetryCard
            icon={BarChart3}
            label="GPM / XPM"
            value={`${safeMetricNumber(metric.avgGpm)} / ${safeMetricNumber(metric.avgXpm)}`}
            delta="average economy"
            tone="red"
          />
          <TelemetryCard
            icon={Activity}
            label="Hero damage"
            value={countMetricValue(Math.round(metric.avgHeroDamage))}
            delta="average per match"
            tone="cyan"
          />
        </section>
      ) : (
        <AnalyticsEmptyBlock
          title="No personal summary yet."
          detail="Import OpenDota matches connected to your profile to populate headline analytics."
        />
      )}
    </div>
  );
}
