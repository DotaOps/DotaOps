import {
  HeroMatrix,
  PlayerHeroPerformanceTable
} from "@/components/analytics/analytics-tables";
import { SectionHeader } from "@/components/section-header";
import type {
  HeroAnalyticsMetric,
  PlayerHeroPerformance
} from "@/lib/analytics-data";

export function PersonalHeroesPanel({
  heroDetails,
  heroPerformance
}: Readonly<{
  heroDetails: PlayerHeroPerformance[];
  heroPerformance: HeroAnalyticsMetric[];
}>) {
  return (
    <>
      <div className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Personal hero pool"
          title="Hero Performance"
          description="Hero analytics scoped to your profile."
        />
        <HeroMatrix heroes={heroPerformance} />
      </div>
      <div className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Hero detail"
          title="Top Hero Breakdown"
          description="Per-hero averages and match references for your current player profile."
        />
        <PlayerHeroPerformanceTable heroes={heroDetails} />
      </div>
    </>
  );
}
