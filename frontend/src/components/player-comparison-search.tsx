"use client";

import { Search, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { UserAvatar } from "@/components/user-avatar";
import { ApiRequestError } from "@/lib/api";
import {
  lookupPlayerComparisonCandidates,
  type AnalyticsFilters,
  type PlayerComparisonCandidate
} from "@/lib/analytics-data";
import { classNames } from "@/lib/utils";

type PlayerComparisonSearchProps = Readonly<{
  disabled?: boolean;
  excludedProfileId?: string;
  filters: AnalyticsFilters;
  label: string;
  onClear: () => void;
  onSelect: (candidate: PlayerComparisonCandidate) => void;
  placeholder?: string;
  selectedCandidate: PlayerComparisonCandidate | null;
}>;

function useDebouncedValue(value: string, delayMs: number) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = globalThis.setTimeout(() => setDebouncedValue(value), delayMs);

    return () => globalThis.clearTimeout(timer);
  }, [delayMs, value]);

  return debouncedValue;
}

function playerSearchErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return "Login session expired. Please log in again to search players.";
    }

    if (error.status === 403) {
      return "You do not have permission to search player comparison candidates.";
    }

    return error.message;
  }

  return error instanceof Error ? error.message : "Player search unavailable.";
}

function candidateMeta(candidate: PlayerComparisonCandidate) {
  const team = candidate.teamName ?? "No team";
  const analytics = candidate.hasAnalyticsData
    ? `${candidate.analyticsGamesCount.toLocaleString("en-US")} analytics games`
    : "No analytics data";

  return `${team} / ${analytics}`;
}

export function PlayerComparisonSearch({
  disabled = false,
  excludedProfileId,
  filters,
  label,
  onClear,
  onSelect,
  placeholder = "Search display name, nickname, or OpenDota ID",
  selectedCandidate
}: PlayerComparisonSearchProps) {
  const [candidates, setCandidates] = useState<PlayerComparisonCandidate[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [query, setQuery] = useState("");
  const trimmedQuery = query.trim();
  const debouncedQuery = useDebouncedValue(trimmedQuery, 320);

  const visibleCandidates = useMemo(
    () => candidates.filter((candidate) => candidate.profileId !== excludedProfileId),
    [candidates, excludedProfileId]
  );
  const canSearch = debouncedQuery.length >= 2;
  const isSearchPending = trimmedQuery.length >= 2 && trimmedQuery !== debouncedQuery;

  useEffect(() => {
    let cancelled = false;

    async function runSearch() {
      if (!canSearch || disabled) {
        setCandidates([]);
        setError(null);
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setError(null);

      try {
        const response = await lookupPlayerComparisonCandidates({
          filters: {
            ...filters,
            limit: 10
          },
          q: debouncedQuery
        });

        if (!cancelled) {
          setCandidates(response.candidates);
        }
      } catch (error) {
        if (!cancelled) {
          setCandidates([]);
          setError(playerSearchErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    void runSearch();

    return () => {
      cancelled = true;
    };
  }, [canSearch, debouncedQuery, disabled, filters]);

  function selectCandidate(candidate: PlayerComparisonCandidate) {
    onSelect(candidate);
    setQuery("");
    setCandidates([]);
    setError(null);
  }

  return (
    <div className="analytics-player-search">
      <label>
        <span>{label}</span>
        <div className="analytics-player-search-input">
          <Search size={16} />
          <input
            disabled={disabled}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={selectedCandidate ? "Search to replace selected player" : placeholder}
            type="search"
            value={query}
          />
        </div>
      </label>

      {selectedCandidate ? (
        <div className="analytics-player-selected">
          <UserAvatar avatarUrl={selectedCandidate.avatarUrl} size={16} />
          <div>
            <strong>{selectedCandidate.displayName}</strong>
            <span>{selectedCandidate.nickname ? `@${selectedCandidate.nickname}` : candidateMeta(selectedCandidate)}</span>
            <em>{selectedCandidate.label ?? candidateMeta(selectedCandidate)}</em>
          </div>
          <button aria-label={`Clear ${label}`} onClick={onClear} type="button">
            <X size={15} />
          </button>
        </div>
      ) : null}

      {!selectedCandidate && trimmedQuery.length > 0 && trimmedQuery.length < 2 ? (
        <p className="analytics-player-search-state">Enter at least two characters.</p>
      ) : null}
      {isSearchPending ? <p className="analytics-player-search-state">Preparing search...</p> : null}
      {isLoading ? <p className="analytics-player-search-state">Searching players...</p> : null}
      {error ? <p className="analytics-player-search-state analytics-player-search-error">{error}</p> : null}
      {!isLoading && !error && canSearch && !isSearchPending && visibleCandidates.length === 0 ? (
        <p className="analytics-player-search-state">
          {candidates.length > 0 ? "Only the already selected player matched." : "No matching players found."}
        </p>
      ) : null}
      {!isLoading && visibleCandidates.length > 0 ? (
        <div className="analytics-player-search-results">
          {visibleCandidates.map((candidate) => (
            <button
              className={classNames(
                "analytics-player-search-result",
                candidate.hasAnalyticsData && "analytics-player-search-result-ready",
                !candidate.hasAnalyticsData && "analytics-player-search-result-muted"
              )}
              key={candidate.profileId}
              onClick={() => selectCandidate(candidate)}
              type="button"
            >
              <UserAvatar avatarUrl={candidate.avatarUrl} size={16} />
              <span>
                <strong>{candidate.displayName}</strong>
                <em>{candidate.nickname ? `@${candidate.nickname}` : candidate.teamName ?? "Player candidate"}</em>
              </span>
              <span>
                <strong>{candidate.analyticsGamesCount.toLocaleString("en-US")}</strong>
                <em>{candidate.hasAnalyticsData ? "analytics games" : candidate.label ?? "No analytics data"}</em>
              </span>
              <span className="analytics-player-search-status">
                {candidate.hasAnalyticsData ? "Ready" : "No data"}
              </span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
