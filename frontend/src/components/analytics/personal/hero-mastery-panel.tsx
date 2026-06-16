"use client";

import { useEffect, useState } from "react";

import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import { analyticsErrorMessage } from "@/components/analytics/analytics-errors";
import { HeroMasteryBaselineComparison } from "@/components/analytics/personal/hero-mastery-baseline-comparison";
import { HeroMasteryContextSummary } from "@/components/analytics/personal/hero-mastery-context-summary";
import { HeroMasteryNotes } from "@/components/analytics/personal/hero-mastery-notes";
import { HeroMasteryRecentMatches } from "@/components/analytics/personal/hero-mastery-recent-matches";
import { HeroMasterySummary } from "@/components/analytics/personal/hero-mastery-summary";
import { SectionHeader } from "@/components/section-header";
import {
  getMyHeroMastery,
  type AnalyticsFilters,
  type HeroMasteryResponse
} from "@/lib/analytics-data";

export function HeroMasteryPanel({
  filters,
  heroId,
  heroName,
  onClose
}: Readonly<{
  filters: AnalyticsFilters;
  heroId: string;
  heroName: string | null;
  onClose: () => void;
}>) {
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSlowLoading, setIsSlowLoading] = useState(false);
  const [mastery, setMastery] = useState<HeroMasteryResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    const slowTimer = window.setTimeout(() => {
      if (!cancelled) {
        setIsSlowLoading(true);
      }
    }, 800);

    async function loadMastery() {
      setError(null);
      setIsLoading(true);
      setIsSlowLoading(false);
      setMastery(null);

      try {
        const nextMastery = await getMyHeroMastery(heroId, filters);

        if (!cancelled) {
          setMastery(nextMastery);
        }
      } catch (error) {
        if (!cancelled) {
          setError(analyticsErrorMessage(error));
        }
      } finally {
        window.clearTimeout(slowTimer);
        if (!cancelled) {
          setIsLoading(false);
          setIsSlowLoading(false);
        }
      }
    }

    void loadMastery();

    return () => {
      cancelled = true;
      window.clearTimeout(slowTimer);
    };
  }, [
    filters.from,
    filters.limit,
    filters.teamId,
    filters.to,
    filters.tournamentId,
    filters,
    heroId
  ]);

  const title = mastery?.heroName ?? heroName ?? "Selected Hero";

  return (
    <section className="analytics-terminal-panel analytics-data-panel ops-panel analytics-mastery-panel" aria-live="polite">
      <SectionHeader
        action={(
          <button className="button ops-button-secondary" onClick={onClose} type="button">
            Clear hero
          </button>
        )}
        eyebrow="Hero mastery"
        title={title}
        description="Hero-specific mastery report for your current player profile."
      />
      {isLoading ? (
        <AnalyticsEmptyBlock
          title="Loading hero mastery."
          detail={isSlowLoading ? "Still loading hero mastery details from protected analytics." : "Fetching hero mastery details."}
        />
      ) : null}
      {!isLoading && error ? (
        <AnalyticsEmptyBlock title="Hero mastery unavailable." detail={error} />
      ) : null}
      {!isLoading && !error && mastery ? (
        <div className="analytics-mastery-stack">
          <HeroMasterySummary mastery={mastery} />
          {mastery.masteryVerdict === "INSUFFICIENT_DATA" ? (
            <AnalyticsEmptyBlock
              title="Insufficient hero sample."
              detail="The backend returned an insufficient-data verdict. Existing notes and raw rows are still shown when available."
            />
          ) : null}
          <HeroMasteryBaselineComparison comparisons={mastery.comparisonToPlayerOverallBaseline} />
          <section className="analytics-mastery-two-column">
            <HeroMasteryContextSummary contextSummary={mastery.contextSummary} />
            <HeroMasteryNotes notes={mastery.deterministicNotes} />
          </section>
          <HeroMasteryRecentMatches matches={mastery.recentMatches} />
        </div>
      ) : null}
    </section>
  );
}
