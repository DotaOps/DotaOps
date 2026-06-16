import { MatchHistoryList } from "@/components/analytics/analytics-tables";
import { PersonalHeroesPanel } from "@/components/analytics/personal/personal-heroes-panel";
import { PersonalInsightsPanel } from "@/components/analytics/personal/personal-insights-panel";
import { PersonalProgressPanel } from "@/components/analytics/personal/personal-progress-panel";
import { PersonalSummaryPanel } from "@/components/analytics/personal/personal-summary-panel";
import { SectionHeader } from "@/components/section-header";
import type {
  AnalyticsFilters,
  PlayerAnalyticsResponse
} from "@/lib/analytics-data";

export function PersonalAnalyticsPanel({
  appliedFilters,
  personal
}: Readonly<{
  appliedFilters: AnalyticsFilters;
  personal: PlayerAnalyticsResponse;
}>) {
  const primaryMetric = personal.metrics[0] ?? null;

  return (
    <section className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Personal analytics"
        title="Personal Performance"
        description="Protected analytics scoped to the current player profile."
      />
      <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
        <PersonalSummaryPanel metric={primaryMetric} />
        <PersonalInsightsPanel insights={personal.insights} />
        <PersonalHeroesPanel
          appliedFilters={appliedFilters}
          heroDetails={personal.heroDetails}
          heroPerformance={personal.heroPerformance}
        />
        <div className="analytics-terminal-panel analytics-data-panel ops-panel">
          <SectionHeader
            eyebrow="Personal match history"
            title="Analyzed Matches"
            description="Imported OpenDota matches connected to your player profile."
          />
          <MatchHistoryList emptyText="No analyzed personal matches yet." matches={personal.matchHistory} />
        </div>
        <PersonalProgressPanel progress={personal.progress} />
      </section>
    </section>
  );
}
