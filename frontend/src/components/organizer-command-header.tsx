import { Brackets, DatabaseZap, RadioTower, Trophy } from "lucide-react";

interface OrganizerCommandHeaderProps {
  tournamentCount: number;
  registrationCount: number;
  importedMatches: number;
  activeChecklistItems: number;
}

export function OrganizerCommandHeader({
  tournamentCount,
  registrationCount,
  importedMatches,
  activeChecklistItems
}: OrganizerCommandHeaderProps) {
  return (
    <section className="organizer-command-header ops-panel">
      <div className="organizer-command-copy">
        <p className="ops-label">Organizer operations</p>
        <h1>Organizer Command Center</h1>
        <p className="ops-mono">
          Workspace for tournaments, team registrations, results, brackets, and
          OpenDota match_id data flow.
        </p>
      </div>

      <div className="organizer-command-status">
        <article>
          <Trophy size={18} />
          <span className="ops-label">Tournaments</span>
          <strong className="ops-data">{tournamentCount}</strong>
        </article>
        <article>
          <Brackets size={18} />
          <span className="ops-label">Registrations</span>
          <strong className="ops-data">{registrationCount}</strong>
        </article>
        <article>
          <DatabaseZap size={18} />
          <span className="ops-label">Match_id</span>
          <strong className="ops-data">{importedMatches}</strong>
        </article>
        <article>
          <RadioTower size={18} />
          <span className="ops-label">Launch ready</span>
          <strong className="ops-data">{activeChecklistItems}/8</strong>
        </article>
      </div>
    </section>
  );
}
