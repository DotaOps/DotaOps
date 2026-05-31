import { AlertTriangle, CalendarDays, GitBranch, RefreshCcw, ShieldCheck, Trophy, UsersRound } from "lucide-react";
import Link from "next/link";

import { OrganizerCreateTournamentLink } from "@/components/organizer-create-tournament-link";
import { SectionHeader } from "@/components/section-header";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { TournamentCard } from "@/components/tournament-card";
import { TournamentMetaGrid } from "@/components/tournament-meta-grid";
import { getTournaments } from "@/lib/data";

export const dynamic = "force-dynamic";

export default async function TournamentsPage() {
  let tournaments: Awaited<ReturnType<typeof getTournaments>> = [];
  let hasLoadError = false;

  try {
    tournaments = await getTournaments();
  } catch {
    hasLoadError = true;
  }

  const liveTournaments = tournaments.filter((tournament) => tournament.status === "live").length;
  const openRegistrations = tournaments.filter(
    (tournament) => tournament.status === "registration"
  ).length;
  const totalRegistrations = tournaments.reduce(
    (total, tournament) => total + tournament.registrationsCount,
    0
  );
  const totalSlots = tournaments.reduce(
    (total, tournament) => total + tournament.teamsCount,
    0
  );

  return (
    <div className="tournament-command">
      <TournamentCommandHeader
        eyebrow="Tournament operations"
        title="Tournament Command Center"
        description="Public and organizer overview of statuses, registrations, formats, and initial data for Dota 2 tournaments."
        actions={<OrganizerCreateTournamentLink />}
      >
        <TournamentMetaGrid
          items={[
            {
              detail: "all circuits",
              icon: ShieldCheck,
              label: "Tournaments",
              tone: "red",
              value: String(tournaments.length)
            },
            {
              detail: "currently active",
              icon: GitBranch,
              label: "Live",
              tone: "green",
              value: String(liveTournaments)
            },
            {
              detail: `${totalRegistrations}/${totalSlots} teams`,
              icon: UsersRound,
              label: "Registrations",
              tone: "cyan",
              value: String(openRegistrations)
            },
            {
              detail: "operations calendar",
              icon: CalendarDays,
              label: "Schedule",
              tone: "gold",
              value: "SYNC"
            }
          ]}
        />
      </TournamentCommandHeader>

      <section className="tournament-command-panel ops-panel">
        <SectionHeader
          eyebrow="Tournament registry"
          title="All Tournaments"
          description="Statuses, registrations, teams, and format are ready for operational review."
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
          <div className="tournament-card-grid">
            {tournaments.map((tournament) => (
              <TournamentCard key={tournament.id} tournament={tournament} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
