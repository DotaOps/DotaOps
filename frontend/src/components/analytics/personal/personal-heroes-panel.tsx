"use client";

import { useMemo, useState } from "react";

import {
  HeroMatrix,
  PlayerHeroPerformanceTable
} from "@/components/analytics/analytics-tables";
import { HeroMasteryPanel } from "@/components/analytics/personal/hero-mastery-panel";
import { SectionHeader } from "@/components/section-header";
import type {
  AnalyticsFilters,
  HeroAnalyticsMetric,
  PlayerHeroPerformance
} from "@/lib/analytics-data";

export function PersonalHeroesPanel({
  appliedFilters,
  heroDetails,
  heroPerformance
}: Readonly<{
  appliedFilters: AnalyticsFilters;
  heroDetails: PlayerHeroPerformance[];
  heroPerformance: HeroAnalyticsMetric[];
}>) {
  const [selectedHeroId, setSelectedHeroId] = useState<string | null>(null);
  const [selectedHeroName, setSelectedHeroName] = useState<string | null>(null);
  const availableHeroes = useMemo(
    () => [
      ...heroDetails
        .filter((hero) => hero.heroId)
        .map((hero) => ({
          heroId: hero.heroId as string,
          heroName: hero.heroName
        })),
      ...heroPerformance.map((hero) => ({
        heroId: hero.heroId,
        heroName: hero.localizedName
      }))
    ],
    [heroDetails, heroPerformance]
  );
  const activeSelectedHero = useMemo(
    () => selectedHeroId
      ? availableHeroes.find((hero) => hero.heroId === selectedHeroId) ?? null
      : null,
    [availableHeroes, selectedHeroId]
  );
  const activeSelectedHeroId = activeSelectedHero?.heroId ?? null;
  const activeSelectedHeroName = selectedHeroName ?? activeSelectedHero?.heroName ?? null;

  function selectHero(heroId: string, heroName: string | null) {
    setSelectedHeroId(heroId);
    setSelectedHeroName(heroName);
  }

  function clearSelectedHero() {
    setSelectedHeroId(null);
    setSelectedHeroName(null);
  }

  return (
    <>
      <div className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Personal hero pool"
          title="Hero Performance"
          description="Hero analytics scoped to your profile."
        />
        <HeroMatrix
          heroes={heroPerformance}
          onSelectHero={selectHero}
          selectedHeroId={activeSelectedHeroId}
        />
      </div>
      <div className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          action={activeSelectedHeroId ? (
            <button className="button ops-button-secondary" onClick={clearSelectedHero} type="button">
              Clear hero
            </button>
          ) : null}
          eyebrow="Hero detail"
          title="Top Hero Breakdown"
          description="Per-hero averages and match references for your current player profile."
        />
        <PlayerHeroPerformanceTable
          heroes={heroDetails}
          onSelectHero={selectHero}
          selectedHeroId={activeSelectedHeroId}
        />
      </div>
      {activeSelectedHeroId ? (
        <HeroMasteryPanel
          filters={appliedFilters}
          heroId={activeSelectedHeroId}
          heroName={activeSelectedHeroName}
          onClose={clearSelectedHero}
        />
      ) : null}
    </>
  );
}
