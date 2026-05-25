import { notFound } from "next/navigation";
import Link from "next/link";
import {
  CalendarDays,
  DatabaseZap,
  Trophy,
  UsersRound
} from "lucide-react";

import { PublicTournamentManageLink } from "@/components/public-tournament-manage-link";
import { PublicTournamentLivePanels } from "@/components/public-tournament-live-panels";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { TournamentAnalyticsPanel } from "@/components/tournament-analytics-panel";
import { TournamentMetaGrid } from "@/components/tournament-meta-grid";
import { TournamentRegistrationPanel } from "@/components/tournament-registration-panel";
import {
  getTournamentBySlug,
  getTournaments
} from "@/lib/data";
import {
  getPublicTournamentAnalytics,
  type TournamentAnalyticsMetric
} from "@/lib/analytics-data";
import { ApiRequestError } from "@/lib/api";
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
  const tournament = await getTournamentBySlug(slug);

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
  let tournamentAnalytics: TournamentAnalyticsMetric | null = null;
  let tournamentAnalyticsError: string | null = null;

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

  try {
    tournamentAnalytics = await getPublicTournamentAnalytics(tournament.id);
  } catch (error) {
    tournamentAnalyticsError = error instanceof ApiRequestError && error.status === 404
      ? null
      : error instanceof Error
      ? error.message
      : "Analytics unavailable.";
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

      <PublicTournamentLivePanels
        initialBracket={bracket}
        initialBracketError={bracketError}
        initialGroupsStandings={groupsStandings}
        initialGroupsStandingsError={groupsStandingsError}
        initialMatches={publicMatches}
        initialMatchesError={publicMatchesError}
        slug={tournament.slug}
        tournamentId={tournament.id}
      />

      <TournamentAnalyticsPanel
        error={tournamentAnalyticsError}
        metrics={tournamentAnalytics}
      />
    </div>
  );
}
