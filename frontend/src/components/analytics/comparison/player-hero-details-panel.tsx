import { HeroMatrix, PlayerHeroPerformanceTable } from "@/components/analytics/analytics-tables";
import { SectionHeader } from "@/components/section-header";
import {
  type HeroAnalyticsMetric,
  type PlayerHeroPerformance
} from "@/lib/analytics-data";

export function PlayerHeroDetailsPanel({
  fallbackHeroes,
  heroDetails,
  playerName
}: Readonly<{
  fallbackHeroes: HeroAnalyticsMetric[];
  heroDetails: PlayerHeroPerformance[];
  playerName: string;
}>) {
  return (
    <div className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Hero performance"
        title={`${playerName} Hero Pool`}
        description="Most played and best available hero rows from the player comparison response."
      />
      {heroDetails.length > 0 ? (
        <PlayerHeroPerformanceTable heroes={heroDetails} />
      ) : (
        <HeroMatrix heroes={fallbackHeroes} />
      )}
    </div>
  );
}
