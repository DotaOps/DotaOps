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
import { HeroMasteryTrends } from "@/components/analytics/personal/hero-mastery-trends";
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
  const filterContext = masteryFilterContext(filters);
  const emptyStateCopy = heroMasteryEmptyCopy(filterContext.hasDataScopeFilters);

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
        description="Showing mastery for current analytics filters."
      />
      <div className="analytics-mastery-filter-context">
        <span className="ops-label">{filterContext.hasAnyFilter ? "Filtered view" : "Current scope"}</span>
        <p>{filterContext.hasAnyFilter ? "These hero mastery numbers follow the active analytics filters." : "Using the default player analytics scope."}</p>
        <div>
          {filterContext.labels.map((label) => (
            <span className="ops-badge" key={label}>{label}</span>
          ))}
        </div>
      </div>
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
              title={emptyStateCopy.title}
              detail={emptyStateCopy.detail}
            />
          ) : null}
          {isInsufficientData && !isEmptyHero ? (
            <div className="analytics-mastery-info-block">
              <span className="ops-label">Sample size</span>
              <strong>Not enough data for a reliable mastery verdict.</strong>
              <p>
                Raw hero stats are still shown when available. The reliable mastery verdict needs more imported
                matches on this hero in the current analytics scope.
              </p>
            </div>
          ) : null}
          <HeroMasteryTrends mastery={mastery} />
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

const DEFAULT_ANALYTICS_LIMIT = 50;

function masteryFilterContext(filters: AnalyticsFilters) {
  const labels = [
    filters.teamId ? "Team filter" : null,
    filters.tournamentId ? "Tournament filter" : null,
    filters.profileId ? "Profile filter" : null,
    filters.from ? `From ${formatFilterDate(filters.from)}` : null,
    filters.to ? `To ${formatFilterDate(filters.to)}` : null,
    filters.limit ? `Limit ${filters.limit}` : null
  ].filter((label): label is string => Boolean(label));
  const hasDataScopeFilters = Boolean(
    filters.teamId ||
    filters.tournamentId ||
    filters.profileId ||
    filters.from ||
    filters.to ||
    (filters.limit && filters.limit !== DEFAULT_ANALYTICS_LIMIT)
  );

  return {
    hasAnyFilter: labels.length > 0,
    hasDataScopeFilters,
    labels: labels.length > 0 ? labels : ["Default scope"]
  };
}

function heroMasteryEmptyCopy(hasDataScopeFilters: boolean) {
  if (hasDataScopeFilters) {
    return {
      detail: "Try clearing date, team, tournament, profile, or limit filters, or select another hero.",
      title: "No mastery data for this hero in the current filter range."
    };
  }

  return {
    detail: "Imported matches for this player do not include this hero yet.",
    title: "No matches on this hero yet."
  };
}

function formatFilterDate(value: string) {
  const parsed = new Date(value);

  if (Number.isNaN(parsed.getTime())) {
    return "selected date";
  }

  return parsed.toLocaleDateString("en-US", { dateStyle: "medium" });
}
