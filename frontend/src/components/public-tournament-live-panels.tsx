"use client";

import {
  BarChart3,
  CalendarDays,
  ClipboardCheck,
  DatabaseZap,
  GitBranch,
  Trophy,
  UsersRound
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";

import { GroupsStandingsPanel } from "@/components/groups-standings-panel";
import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { PublicTournamentManageLink } from "@/components/public-tournament-manage-link";
import { SectionHeader } from "@/components/section-header";
import { TournamentAnalyticsPanel } from "@/components/tournament-analytics-panel";
import { TournamentBracketPanel } from "@/components/tournament-bracket-panel";
import { TournamentMetaGrid } from "@/components/tournament-meta-grid";
import { TournamentRegistrationPanel } from "@/components/tournament-registration-panel";
import { TournamentScheduleResultsPanel } from "@/components/tournament-schedule-results-panel";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import type { TournamentAnalyticsMetric } from "@/lib/analytics-data";
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
import type { Tournament } from "@/lib/types";
import { classNames, formatDateTime } from "@/lib/utils";

type PublicTournamentView =
  | "overview"
  | "submission"
  | "groups"
  | "bracket"
  | "schedule";

interface ShortcutItem {
  icon: LucideIcon;
  label: string;
  view: PublicTournamentView;
}

const shortcuts: ShortcutItem[] = [
  { icon: BarChart3, label: "Overview", view: "overview" },
  { icon: ClipboardCheck, label: "Team Submission", view: "submission" },
  { icon: UsersRound, label: "Groups & Standings", view: "groups" },
  { icon: GitBranch, label: "Bracket & Advancement", view: "bracket" },
  { icon: CalendarDays, label: "Schedule & Results", view: "schedule" }
];

interface PublicTournamentLivePanelsProps {
  analyticsError: string | null;
  analyticsMetrics: TournamentAnalyticsMetric | null;
  initialBracket: TournamentBracket | null;
  initialBracketError: string | null;
  initialGroupsStandings: PublicGroupsStandingsData;
  initialGroupsStandingsError: string | null;
  initialMatches: TournamentMatch[];
  initialMatchesError: string | null;
  slug: string;
  tournament: Tournament;
  tournamentId: string;
}

function settledError(result: PromiseSettledResult<unknown>, fallback: string) {
  if (result.status === "fulfilled") {
    return null;
  }

  return result.reason instanceof Error ? result.reason.message : fallback;
}

export function PublicTournamentLivePanels({
  analyticsError,
  analyticsMetrics,
  initialBracket,
  initialBracketError,
  initialGroupsStandings,
  initialGroupsStandingsError,
  initialMatches,
  initialMatchesError,
  slug,
  tournament,
  tournamentId
}: PublicTournamentLivePanelsProps) {
  const [activeView, setActiveView] = useState<PublicTournamentView>("overview");
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

  const showLiveSync = activeView !== "submission";

  const liveSync = useTournamentLiveRefresh({
    enabled: showLiveSync,
    hiddenIntervalMs: 60_000,
    intervalMs: 30_000,
    label: "public tournament",
    onRefresh: refreshPublicPanels
  });

  const overviewMetrics = useMemo(
    () => [
      {
        detail: "format",
        icon: Trophy,
        label: "System",
        tone: "red" as const,
        value: tournament.format
      },
      {
        detail: "start",
        icon: CalendarDays,
        label: "Schedule",
        tone: "gold" as const,
        value: formatDateTime(tournament.startsAt)
      },
      {
        detail: "registrations",
        icon: UsersRound,
        label: "Teams",
        tone: "cyan" as const,
        value: `${tournament.registrationsCount}/${tournament.teamsCount}`
      },
      {
        detail: "official records",
        icon: DatabaseZap,
        label: "Matches",
        tone: "green" as const,
        value: String(matches.length)
      }
    ],
    [matches.length, tournament]
  );

  return (
    <div className="public-tournament-live-panels">
      <nav className="public-tournament-shortcuts org-tournament-section-shortcuts ops-panel" aria-label="Tournament shortcuts">
        {shortcuts.map((shortcut) => (
          <button
            aria-pressed={activeView === shortcut.view}
            className={classNames(activeView === shortcut.view && "is-active")}
            key={shortcut.view}
            onClick={() => setActiveView(shortcut.view)}
            type="button"
          >
            <shortcut.icon size={15} />
            {shortcut.label}
          </button>
        ))}
      </nav>

      {showLiveSync ? (
        <LiveSyncIndicator
          errorCount={liveSync.errorCount}
          lastError={liveSync.lastError}
          lastUpdated={liveSync.lastUpdated}
          onRefresh={liveSync.refreshNow}
          status={liveSync.status}
        />
      ) : null}

      <div className="public-tournament-tab-body">
        {activeView === "overview" ? (
          <div className="public-tournament-overview">
            <section className="public-tournament-overview-panel ops-panel">
              <SectionHeader
                eyebrow="Tournament Snapshot"
                title="Overview"
                description="Tournament setup, start time, team capacity, and official match records."
              />
              <TournamentMetaGrid items={overviewMetrics} />
            </section>

            <TournamentAnalyticsPanel
              error={analyticsError}
              metrics={analyticsMetrics}
            />
          </div>
        ) : null}

        <div
          aria-hidden={activeView !== "submission"}
          className="public-tournament-tab-panel"
          hidden={activeView !== "submission"}
        >
          <TournamentRegistrationPanel tournament={tournament} />
        </div>

        {activeView === "groups" ? (
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
        ) : null}

        {activeView === "bracket" ? (
          <TournamentBracketPanel
            bracket={bracket}
            error={bracketError}
          />
        ) : null}

        {activeView === "schedule" ? (
          <TournamentScheduleResultsPanel
            error={matchesError}
            matches={matches}
          />
        ) : null}
      </div>
    </div>
  );
}
