"use client";

import {
  useEffect,
  useRef,
  useState
} from "react";

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
  const [retryNonce, setRetryNonce] = useState(0);
  const requestSerial = useRef(0);

  useEffect(() => {
    let cancelled = false;
    let slowTimer: number | null = null;
    const requestId = requestSerial.current + 1;
    requestSerial.current = requestId;

    async function loadMastery() {
      await Promise.resolve();

      if (cancelled || requestSerial.current !== requestId) {
        return;
      }

      setError(null);
      setIsLoading(true);
      setIsSlowLoading(false);
      setMastery(null);

      slowTimer = window.setTimeout(() => {
        if (!cancelled && requestSerial.current === requestId) {
          setIsSlowLoading(true);
        }
      }, 800);

      try {
        const nextMastery = await getMyHeroMastery(heroId, filters);

        if (!cancelled && requestSerial.current === requestId) {
          setMastery(nextMastery);
        }
      } catch (error) {
        if (!cancelled && requestSerial.current === requestId) {
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (slowTimer !== null) {
          window.clearTimeout(slowTimer);
        }

        if (!cancelled && requestSerial.current === requestId) {
          setIsLoading(false);
          setIsSlowLoading(false);
        }
      }
    }

    void loadMastery();

    return () => {
      cancelled = true;
      requestSerial.current += 1;
      if (slowTimer !== null) {
        window.clearTimeout(slowTimer);
      }
    };
  }, [filters, heroId, retryNonce]);

  function retry() {
    setRetryNonce((current) => current + 1);
  }

  const title = mastery?.heroName ?? heroName ?? "Selected Hero";
  const isEmptyHero = Boolean(mastery && mastery.games === 0 && mastery.recentMatches.length === 0);
  const isInsufficientData = mastery?.masteryVerdict === "INSUFFICIENT_DATA";

  return (
    <section
      aria-busy={isLoading}
      aria-live="polite"
      className="analytics-terminal-panel analytics-data-panel ops-panel analytics-mastery-panel"
    >
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
      <p className="analytics-context-note">
        Raw hero stats are shown as stored. Context weighting is displayed separately and only affects long-term
        interpretation and the mastery verdict.
      </p>
      {isLoading ? (
        <AnalyticsEmptyBlock
          title={`Loading ${title} mastery.`}
          detail={isSlowLoading ? "Still loading protected hero mastery details." : "Fetching hero mastery details for the selected hero."}
        />
      ) : null}
      {!isLoading && error ? (
        <div className="analytics-mastery-state">
          <AnalyticsEmptyBlock title="Hero mastery unavailable." detail={error} />
          <button className="button ops-button-secondary" onClick={retry} type="button">
            Retry hero mastery
          </button>
        </div>
      ) : null}
      {!isLoading && !error && mastery ? (
        <div className="analytics-mastery-stack">
          <HeroMasterySummary mastery={mastery} />
          {isEmptyHero ? (
            <AnalyticsEmptyBlock
              title="No analyzed matches for this hero."
              detail="This hero is selected, but the current filter scope has no imported matches connected to your profile."
            />
          ) : null}
          {isInsufficientData && !isEmptyHero ? (
            <AnalyticsEmptyBlock
              title="Insufficient hero sample."
              detail="The selected hero has some data, but not enough matches for a reliable mastery verdict. Backend notes and raw rows are still shown when available."
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
