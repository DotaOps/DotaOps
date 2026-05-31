import { CalendarDays, GitBranch, Plus, ShieldCheck, UsersRound } from "lucide-react";
import Link from "next/link";

import { SectionHeader } from "@/components/section-header";
import { TournamentCommandHeader } from "@/components/tournament-command-header";
import { TournamentCard } from "@/components/tournament-card";
import { TournamentMetaGrid } from "@/components/tournament-meta-grid";
import { getTournaments } from "@/lib/data";

export const dynamic = "force-dynamic";

export default async function TournamentsPage() {
  const tournaments = await getTournaments();
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
        actions={
          <Link className="button ops-button-primary" href="/organizator">
            <Plus size={18} />
            <span>New Tournament</span>
          </Link>
        }
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
        <div className="tournament-card-grid">
          {tournaments.length === 0 ? (
            <p>No public tournaments are currently available.</p>
          ) : (
            tournaments.map((tournament) => (
              <TournamentCard key={tournament.id} tournament={tournament} />
            ))
          )}
        </div>
      </section>
    </div>
  );
}
