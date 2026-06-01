import { notFound } from "next/navigation";
import Link from "next/link";

import { PublicTournamentLivePanels } from "@/components/public-tournament-live-panels";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { getTournamentBySlug } from "@/lib/data";
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

interface TournamentDetailPageProps {
  params: Promise<{
    slug: string;
  }>;
}

export const dynamic = "force-dynamic";

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
          <Link className="button ops-button-secondary" href="/turnirji">
            All Tournaments
          </Link>
        }
      />

      <PublicTournamentLivePanels
        analyticsError={tournamentAnalyticsError}
        analyticsMetrics={tournamentAnalytics}
        initialBracket={bracket}
        initialBracketError={bracketError}
        initialGroupsStandings={groupsStandings}
        initialGroupsStandingsError={groupsStandingsError}
        initialMatches={publicMatches}
        initialMatchesError={publicMatchesError}
        slug={tournament.slug}
        tournament={tournament}
        tournamentId={tournament.id}
      />
    </div>
  );
}
