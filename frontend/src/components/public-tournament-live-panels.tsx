"use client";

import { useCallback, useState } from "react";

import { GroupsStandingsPanel } from "@/components/groups-standings-panel";
import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { PublicTournamentManageLink } from "@/components/public-tournament-manage-link";
import { TournamentBracketPanel } from "@/components/tournament-bracket-panel";
import { TournamentScheduleResultsPanel } from "@/components/tournament-schedule-results-panel";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import {
  getPublicGroupsStandingsData,
  type PublicGroupsStandingsData
} from "@/lib/tournament-group-data";
import {
  getPublicTournamentBracket,
  type TournamentBracket
} from "@/lib/tournament-bracket-data";
import {
  getPublicTournamentMatches,
  type TournamentMatch
} from "@/lib/tournament-match-data";

interface PublicTournamentLivePanelsProps {
  initialBracket: TournamentBracket | null;
  initialBracketError: string | null;
  initialGroupsStandings: PublicGroupsStandingsData;
  initialGroupsStandingsError: string | null;
  initialMatches: TournamentMatch[];
  initialMatchesError: string | null;
  slug: string;
  tournamentId: string;
}

function settledError(result: PromiseSettledResult<unknown>, fallback: string) {
  if (result.status === "fulfilled") {
    return null;
  }

  return result.reason instanceof Error ? result.reason.message : fallback;
}

export function PublicTournamentLivePanels({
  initialBracket,
  initialBracketError,
  initialGroupsStandings,
  initialGroupsStandingsError,
  initialMatches,
  initialMatchesError,
  slug,
  tournamentId
}: PublicTournamentLivePanelsProps) {
  const [groupsStandings, setGroupsStandings] = useState(initialGroupsStandings);
  const [groupsStandingsError, setGroupsStandingsError] = useState(initialGroupsStandingsError);
  const [bracket, setBracket] = useState(initialBracket);
  const [bracketError, setBracketError] = useState(initialBracketError);
  const [matches, setMatches] = useState(initialMatches);
  const [matchesError, setMatchesError] = useState(initialMatchesError);

  const refreshPublicPanels = useCallback(async () => {
    const [groupsResult, bracketResult, matchesResult] = await Promise.allSettled([
      getPublicGroupsStandingsData(tournamentId),
      getPublicTournamentBracket(tournamentId),
      getPublicTournamentMatches(tournamentId)
    ]);

    if (groupsResult.status === "fulfilled") {
      setGroupsStandings(groupsResult.value);
      setGroupsStandingsError(null);
    } else {
      setGroupsStandingsError(settledError(groupsResult, "Group standings are unavailable."));
    }

    if (bracketResult.status === "fulfilled") {
      setBracket(bracketResult.value);
      setBracketError(null);
    } else {
      setBracketError(settledError(bracketResult, "Bracket is unavailable."));
    }

    if (matchesResult.status === "fulfilled") {
      setMatches(matchesResult.value);
      setMatchesError(null);
    } else {
      setMatchesError(settledError(matchesResult, "Schedule is unavailable."));
    }

    const failed = [groupsResult, bracketResult, matchesResult].find(
      (result) => result.status === "rejected"
    );

    if (failed?.status === "rejected") {
      throw failed.reason;
    }
  }, [tournamentId]);

  const liveSync = useTournamentLiveRefresh({
    enabled: true,
    hiddenIntervalMs: 60_000,
    intervalMs: 30_000,
    label: "public tournament",
    onRefresh: refreshPublicPanels
  });

  return (
    <div className="public-tournament-live-panels">
      <LiveSyncIndicator
        errorCount={liveSync.errorCount}
        lastError={liveSync.lastError}
        lastUpdated={liveSync.lastUpdated}
        onRefresh={liveSync.refreshNow}
        status={liveSync.status}
      />

      <GroupsStandingsPanel
        error={groupsStandingsError}
        groups={groupsStandings.groups}
        managementAction={
          <PublicTournamentManageLink
            className="button ops-button-secondary"
            label="Manage Groups"
            note
            slug={slug}
            tournamentId={tournamentId}
          />
        }
        standings={groupsStandings.standings}
      />

      <TournamentBracketPanel
        bracket={bracket}
        error={bracketError}
      />

      <TournamentScheduleResultsPanel
        error={matchesError}
        matches={matches}
      />
    </div>
  );
}
