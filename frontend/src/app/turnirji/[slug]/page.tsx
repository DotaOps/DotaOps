import { notFound } from "next/navigation";
import Link from "next/link";
import {
  BarChart3,
  CalendarDays,
  DatabaseZap,
  Trophy,
  UsersRound
} from "lucide-react";

import { AnalyticsOverview } from "@/components/analytics-overview";
import { GroupsStandingsPanel } from "@/components/groups-standings-panel";
import { PublicTournamentManageLink } from "@/components/public-tournament-manage-link";
import { SectionHeader } from "@/components/section-header";
import { TournamentBracketPanel } from "@/components/tournament-bracket-panel";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { TournamentMetaGrid } from "@/components/tournament-meta-grid";
import { TournamentRegistrationPanel } from "@/components/tournament-registration-panel";
import { TournamentScheduleResultsPanel } from "@/components/tournament-schedule-results-panel";
import {
  getAnalytics,
  getTournamentBySlug,
  getTournaments
} from "@/lib/data";
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
import { formatDateTime } from "@/lib/utils";

interface TournamentDetailPageProps {
  params: Promise<{
    slug: string;
  }>;
}

export const dynamic = "force-dynamic";

export async function generateStaticParams() {
  const tournaments = await getTournaments();

  return tournaments.map((tournament) => ({ slug: tournament.slug }));
}

export default async function TournamentDetailPage({
  params
}: TournamentDetailPageProps) {
  const { slug } = await params;
  const [analytics, tournament] = await Promise.all([
    getAnalytics(),
    getTournamentBySlug(slug)
  ]);

  if (!tournament) {
    notFound();
  }

  let groupsStandings: PublicGroupsStandingsData = {
    groups: [],
    standings: []
  };
  let groupsStandingsError: string | null = null;
  let bracket: TournamentBracket | null = null;
  let bracketError: string | null = null;
  let publicMatches: TournamentMatch[] = [];
  let publicMatchesError: string | null = null;

  try {
    groupsStandings = await getPublicGroupsStandingsData(tournament.id);
  } catch (error) {
    groupsStandingsError = error instanceof Error
      ? error.message
      : "Group standings are unavailable.";
  }

  try {
    bracket = await getPublicTournamentBracket(tournament.id);
  } catch (error) {
    bracketError = error instanceof Error
      ? error.message
      : "Bracket is unavailable.";
  }

  try {
    publicMatches = await getPublicTournamentMatches(tournament.id);
  } catch (error) {
    publicMatchesError = error instanceof Error
      ? error.message
      : "Schedule is unavailable.";
  }

  return (
    <div className="tournament-control-room">
      <TournamentCommandHeader
        eyebrow="Tournament control room"
        title={tournament.title}
        description={tournament.description}
        status={tournament.status}
        actions={
          <>
            <Link className="button ops-button-secondary" href="/turnirji">
              All Tournaments
            </Link>
            <PublicTournamentManageLink
              label="Manage Groups"
              slug={tournament.slug}
              tournamentId={tournament.id}
            />
          </>
        }
      >
        <TournamentMetaGrid
          items={[
            {
              detail: "format",
              icon: Trophy,
              label: "System",
              tone: "red",
              value: tournament.format
            },
            {
              detail: "start",
              icon: CalendarDays,
              label: "Schedule",
              tone: "gold",
              value: formatDateTime(tournament.startsAt)
            },
            {
              detail: "registrations",
              icon: UsersRound,
              label: "Teams",
              tone: "cyan",
              value: `${tournament.registrationsCount}/${tournament.teamsCount}`
            },
            {
              detail: "official records",
              icon: DatabaseZap,
              label: "Matches",
              tone: "green",
              value: String(publicMatches.length)
            }
          ]}
        />
      </TournamentCommandHeader>

      <TournamentRegistrationPanel tournament={tournament} />

      <GroupsStandingsPanel
        error={groupsStandingsError}
        groups={groupsStandings.groups}
        managementAction={
          <PublicTournamentManageLink
            className="button ops-button-secondary"
            label="Manage Groups"
            note
            slug={tournament.slug}
            tournamentId={tournament.id}
          />
        }
        standings={groupsStandings.standings}
      />

      <TournamentBracketPanel
        bracket={bracket}
        error={bracketError}
      />

      <TournamentScheduleResultsPanel
        error={publicMatchesError}
        matches={publicMatches}
      />

      <section className="tournament-command-panel tournament-analytics-panel ops-panel">
        <SectionHeader
          eyebrow="Tournament Analytics"
          title="Metrics from Imported Matches"
          description="Ready for win rate, KDA, match duration, and hero performance."
          action={
            <Link className="text-link ops-mono" href="/analitika">
              <BarChart3 size={16} />
              <span>Open Analytics</span>
            </Link>
          }
        />
        <AnalyticsOverview heroes={analytics.heroMetrics} teams={analytics.teams} />
      </section>
    </div>
  );
}
