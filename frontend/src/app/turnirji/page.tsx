import {
  AlertTriangle,
  RefreshCcw,
  Trophy,
} from "lucide-react";
import Link from "next/link";

import { OrganizerCreateTournamentLink } from "@/components/organizer-create-tournament-link";
import { SectionHeader } from "@/components/section-header";
import { TournamentCatalogue } from "@/components/tournament-catalogue";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { getTournaments } from "@/lib/data";
import { tournamentReferenceTime } from "@/lib/tournament-phase";

export const dynamic = "force-dynamic";

export default async function TournamentsPage() {
  let tournaments: Awaited<ReturnType<typeof getTournaments>> = [];
  let hasLoadError = false;

  try {
    tournaments = await getTournaments();
  } catch {
    hasLoadError = true;
  }

  const referenceTime = tournamentReferenceTime();

  return (
    <div className="tournament-command">
      <TournamentCommandHeader
        eyebrow="Tournament catalogue"
        title="Browse Dota 2 tournaments"
        description="Published circuits, registration windows, live events, and completed tournament records in one public catalogue."
        actions={<OrganizerCreateTournamentLink />}
      />

      <section className="tournament-command-panel ops-panel">
        <SectionHeader
          eyebrow="Tournament registry"
          title="All Tournaments"
          description="Statuses, registrations, teams, and formats for every public Dota 2 tournament."
        />
        {hasLoadError ? (
          <div className="tournament-registry-state is-error">
            <AlertTriangle size={22} />
            <div>
              <h3>Tournaments are currently unavailable.</h3>
              <p>The tournament registry could not be loaded. Please try again.</p>
              <Link className="button ops-button-secondary" href="/turnirji">
                <RefreshCcw size={16} />
                <span>Retry</span>
              </Link>
            </div>
          </div>
        ) : tournaments.length === 0 ? (
          <div className="tournament-registry-state">
            <Trophy size={22} />
            <div>
              <h3>No tournaments published yet.</h3>
              <p>Published tournaments will appear here when they are available.</p>
            </div>
          </div>
        ) : (
          <TournamentCatalogue referenceTime={referenceTime} tournaments={tournaments} />
        )}
      </section>
    </div>
  );
}
