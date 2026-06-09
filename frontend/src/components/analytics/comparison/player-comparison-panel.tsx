"use client";

import { useEffect, useMemo, useState } from "react";

import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import { analyticsErrorMessage } from "@/components/analytics/analytics-errors";
import { EnrichedMatchHistoryPanel } from "@/components/analytics/comparison/enriched-match-history-panel";
import { HeadlineComparisonCards } from "@/components/analytics/comparison/headline-comparison-cards";
import {
  PlayerCandidateDataWarnings,
  PlayerComparisonWarnings
} from "@/components/analytics/comparison/comparison-warnings";
import { PlayerHeroDetailsPanel } from "@/components/analytics/comparison/player-hero-details-panel";
import { SharedHeroComparisonTable } from "@/components/analytics/comparison/shared-hero-comparison-table";
import { PlayerComparisonSearch } from "@/components/player-comparison-search";
import { SectionHeader } from "@/components/section-header";
import {
  compareAnalyticsPlayers,
  getMyTeamLookups,
  getTeamPlayerLookups,
  type AnalyticsFilters,
  type PlayerAnalyticsMetric,
  type PlayerComparisonCandidate,
  type PlayerComparisonResponse,
  type TeamLookup,
  type TeamPlayerLookup
} from "@/lib/analytics-data";
import { type CurrentUserProfile } from "@/lib/auth";

export function PlayerComparisonPanel({
  appliedFilters,
  currentProfile,
  currentProfileId,
  fallbackPlayers
}: Readonly<{
  appliedFilters: AnalyticsFilters;
  currentProfile: CurrentUserProfile | null;
  currentProfileId: string;
  fallbackPlayers: PlayerAnalyticsMetric[];
}>) {
  const [comparison, setComparison] = useState<PlayerComparisonResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isComparing, setIsComparing] = useState(false);
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [leftId, setLeftId] = useState("");
  const [leftSearchSelection, setLeftSearchSelection] = useState<PlayerComparisonCandidate | null>(null);
  const [playerLookups, setPlayerLookups] = useState<TeamPlayerLookup[]>([]);
  const [rightId, setRightId] = useState("");
  const [rightSearchSelection, setRightSearchSelection] = useState<PlayerComparisonCandidate | null>(null);
  const [selectedTeamId, setSelectedTeamId] = useState(appliedFilters.teamId ?? "");
  const [teamLookups, setTeamLookups] = useState<TeamLookup[]>([]);
  const [useCurrentProfileDefault, setUseCurrentProfileDefault] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function loadTeams() {
      setIsLoadingLookups(true);
      setError(null);

      try {
        const teams = await getMyTeamLookups(100);

        if (!cancelled) {
          setTeamLookups(teams);
          setSelectedTeamId((current) => current || teams[0]?.teamId || "");
        }
      } catch (error) {
        if (!cancelled) {
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsLoadingLookups(false);
        }
      }
    }

    void loadTeams();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function loadPlayers() {
      if (!selectedTeamId) {
        setPlayerLookups([]);
        return;
      }

      setIsLoadingLookups(true);
      setError(null);

      try {
        const players = await getTeamPlayerLookups(selectedTeamId, 100);

        if (!cancelled) {
          setPlayerLookups(players);
        }
      } catch (error) {
        if (!cancelled) {
          setPlayerLookups([]);
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsLoadingLookups(false);
        }
      }
    }

    void loadPlayers();

    return () => {
      cancelled = true;
    };
  }, [selectedTeamId]);

  const players = useMemo(
    () => playerLookups.length > 0
      ? playerLookups.map((player) => ({
        displayName: player.displayName,
        profileId: player.profileId
      }))
      : fallbackPlayers.map((player) => ({
        displayName: player.displayName,
        profileId: player.profileId
      })),
    [fallbackPlayers, playerLookups]
  );
  const candidateTeamFilter = selectedTeamId || appliedFilters.teamId;
  const currentPlayerMetric = useMemo(
    () => fallbackPlayers.find((player) => player.profileId === currentProfileId) ?? null,
    [currentProfileId, fallbackPlayers]
  );
  const currentProfileCandidate = useMemo(() => {
    if (!currentProfileId || !currentProfile) {
      return null;
    }

    const analyticsGamesCount = currentPlayerMetric?.gamesPlayed ?? 0;

    return {
      analyticsGamesCount,
      avatarUrl: currentProfile.avatarUrl,
      displayName: currentProfile.displayName ?? currentProfile.nickname,
      hasAnalyticsData: analyticsGamesCount > 0,
      label: analyticsGamesCount > 0
        ? `${analyticsGamesCount.toLocaleString("en-US")} analytics games`
        : "Current profile",
      nickname: currentProfile.nickname,
      opendotaAccountId: currentProfile.opendotaAccountId,
      profileId: currentProfileId,
      teamId: currentPlayerMetric?.teamId ?? candidateTeamFilter ?? null,
      teamName: currentPlayerMetric?.teamName ?? null
    };
  }, [candidateTeamFilter, currentPlayerMetric, currentProfile, currentProfileId]);
  const searchFilters = useMemo(
    () => ({
      from: appliedFilters.from,
      heroId: appliedFilters.heroId,
      limit: 10,
      teamId: candidateTeamFilter,
      to: appliedFilters.to,
      tournamentId: appliedFilters.tournamentId
    }),
    [
      appliedFilters.from,
      appliedFilters.heroId,
      appliedFilters.to,
      appliedFilters.tournamentId,
      candidateTeamFilter
    ]
  );

  const activeLeftSearchSelection =
    useCurrentProfileDefault ? leftSearchSelection ?? currentProfileCandidate : leftSearchSelection;
  const selectedLeftId = activeLeftSearchSelection?.profileId || leftId;
  const selectedRightId = rightSearchSelection?.profileId || rightId;
  const samePlayerSelected = Boolean(selectedLeftId && selectedRightId && selectedLeftId === selectedRightId);
  const effectiveLeftId = selectedLeftId || players[0]?.profileId || "";
  const effectiveRightId =
    samePlayerSelected
      ? ""
      : selectedRightId ||
        players.find((player) => player.profileId !== effectiveLeftId)?.profileId ||
        "";
  const searchComparisonActive = Boolean(activeLeftSearchSelection || rightSearchSelection);
  const selectedCandidateWarnings = [activeLeftSearchSelection, rightSearchSelection]
    .filter((candidate): candidate is PlayerComparisonCandidate => Boolean(candidate))
    .filter((candidate) => !candidate.hasAnalyticsData);

  useEffect(() => {
    let cancelled = false;

    async function runComparison() {
      if (
        !effectiveLeftId ||
        !effectiveRightId ||
        samePlayerSelected ||
        (!searchComparisonActive && !selectedTeamId)
      ) {
        setComparison(null);
        return;
      }

      setIsComparing(true);
      setError(null);

      try {
        const response = await compareAnalyticsPlayers({
          filters: {
            from: appliedFilters.from,
            heroId: appliedFilters.heroId,
            limit: appliedFilters.limit,
            teamId: searchComparisonActive ? candidateTeamFilter : selectedTeamId,
            to: appliedFilters.to,
            tournamentId: appliedFilters.tournamentId
          },
          profileAId: effectiveLeftId,
          profileBId: effectiveRightId
        });

        if (!cancelled) {
          setComparison(response);
        }
      } catch (error) {
        if (!cancelled) {
          setComparison(null);
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsComparing(false);
        }
      }
    }

    void runComparison();

    return () => {
      cancelled = true;
    };
  }, [appliedFilters.from, appliedFilters.heroId, appliedFilters.limit, appliedFilters.to, appliedFilters.tournamentId, candidateTeamFilter, effectiveLeftId, effectiveRightId, samePlayerSelected, searchComparisonActive, selectedTeamId]);

  if (!currentProfileId && teamLookups.length === 0 && fallbackPlayers.length < 2 && !isLoadingLookups) {
    return (
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Roster comparison"
          title="Player vs Player"
          description="Compare players from your team roster."
        />
        <AnalyticsEmptyBlock
          title="Player comparison requires a team roster."
          detail="Join or create a team with at least two players to compare player analytics."
        />
      </section>
    );
  }

  const leftPlayer = comparison?.playerA ?? null;
  const rightPlayer = comparison?.playerB ?? null;

  return (
    <section className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Roster comparison"
        title="Player vs Player"
        description="Compare players from your team roster."
      />
      <div className="analytics-comparison-controls">
        <label>
          <span>Team</span>
          <select value={selectedTeamId} onChange={(event) => {
            setSelectedTeamId(event.target.value);
            setLeftId("");
            setRightId("");
            setUseCurrentProfileDefault(true);
            setLeftSearchSelection(null);
            setRightSearchSelection(null);
            setComparison(null);
          }}>
            <option value="">Select team</option>
            {teamLookups.map((team) => (
              <option key={team.teamId} value={team.teamId}>
                {team.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Roster Player A</span>
          <select value={activeLeftSearchSelection ? "" : effectiveLeftId} onChange={(event) => {
            setUseCurrentProfileDefault(false);
            setLeftSearchSelection(null);
            setLeftId(event.target.value);
          }}>
            <option value="">Select player</option>
            {players.map((player) => (
              <option key={`left-${player.profileId}`} value={player.profileId}>
                {player.displayName}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Roster Player B</span>
          <select value={rightSearchSelection ? "" : effectiveRightId} onChange={(event) => {
            setRightSearchSelection(null);
            setRightId(event.target.value);
          }}>
            <option value="">Select player</option>
            {players
              .filter((player) => player.profileId !== effectiveLeftId)
              .map((player) => (
                <option key={`right-${player.profileId}`} value={player.profileId}>
                  {player.displayName}
                </option>
              ))}
          </select>
        </label>
      </div>
      <div className="analytics-player-search-grid">
        <PlayerComparisonSearch
          excludedProfileId={effectiveRightId}
          filters={searchFilters}
          label="Search Player A"
          onClear={() => {
            setUseCurrentProfileDefault(false);
            setLeftSearchSelection(null);
            setLeftId("");
            setComparison(null);
          }}
          onSelect={(candidate) => {
            setUseCurrentProfileDefault(false);
            setLeftSearchSelection(candidate);
            setLeftId("");
            setComparison(null);
          }}
          selectedCandidate={activeLeftSearchSelection}
        />
        <PlayerComparisonSearch
          excludedProfileId={effectiveLeftId}
          filters={searchFilters}
          label="Search Player B"
          onClear={() => {
            setRightSearchSelection(null);
            setRightId("");
            setComparison(null);
          }}
          onSelect={(candidate) => {
            setRightSearchSelection(candidate);
            setRightId("");
            setComparison(null);
          }}
          selectedCandidate={rightSearchSelection}
        />
      </div>
      {isLoadingLookups ? <p className="analytics-slow-query">Loading roster options...</p> : null}
      {isComparing ? <p className="analytics-slow-query">Comparing players...</p> : null}
      {error ? <AnalyticsEmptyBlock title="Player comparison unavailable." detail={error} /> : null}
      {samePlayerSelected ? (
        <AnalyticsEmptyBlock
          title="Select two different players."
          detail="Player comparison needs distinct Player A and Player B profiles."
        />
      ) : null}
      {selectedCandidateWarnings.length > 0 ? (
        <PlayerCandidateDataWarnings candidates={selectedCandidateWarnings} />
      ) : null}
      {!error && !searchComparisonActive && players.length < 2 ? (
        <AnalyticsEmptyBlock
          title="Player comparison requires at least two players."
          detail="The selected team roster lookup returned fewer than two players."
        />
      ) : null}
      {!error && comparison && (!leftPlayer || !rightPlayer) ? (
        <AnalyticsEmptyBlock
          title="Not enough analytics data for this comparison."
          detail="At least one selected player has no aggregate analytics row in the current filter scope."
        />
      ) : null}
      {!error && comparison && leftPlayer && rightPlayer ? (
        <>
          <span className="ops-badge">Access scope: {comparison.filters.accessScope}</span>
          <PlayerComparisonWarnings warnings={comparison.warnings} />
          <HeadlineComparisonCards
            comparison={comparison}
            leftPlayer={leftPlayer}
            rightPlayer={rightPlayer}
          />
          {comparison.profileAHeroDetails.length > 0 ||
          comparison.profileBHeroDetails.length > 0 ||
          comparison.profileAHeroPerformance.length > 0 ||
          comparison.profileBHeroPerformance.length > 0 ? (
            <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
              <PlayerHeroDetailsPanel
                fallbackHeroes={comparison.profileAHeroPerformance}
                heroDetails={comparison.profileAHeroDetails}
                playerName={leftPlayer.displayName}
              />
              <PlayerHeroDetailsPanel
                fallbackHeroes={comparison.profileBHeroPerformance}
                heroDetails={comparison.profileBHeroDetails}
                playerName={rightPlayer.displayName}
              />
            </section>
          ) : null}
          <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
            <div className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Shared hero pool"
                title="Shared Heroes"
                description="Hero overlap returned by the player comparison endpoint."
              />
              <SharedHeroComparisonTable
                heroes={comparison.sharedHeroComparisons}
                fallbackHeroes={comparison.sharedHeroes}
              />
            </div>
            <EnrichedMatchHistoryPanel
              fallbackMatches={comparison.recentMatches}
              leftName={leftPlayer.displayName}
              matches={comparison.enrichedMatchHistory}
              rightName={rightPlayer.displayName}
            />
          </section>
        </>
      ) : null}
    </section>
  );
}
