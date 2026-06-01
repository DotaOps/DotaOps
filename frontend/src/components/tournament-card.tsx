import { CalendarDays, ChevronRight, Clock3, Trophy, UsersRound } from "lucide-react";
import Image from "next/image";
import Link from "next/link";

import { StatusBadge } from "@/components/status-badge";
import type { Tournament } from "@/lib/types";
import { formatDateTime } from "@/lib/utils";

const TOURNAMENT_FALLBACK_IMAGE = "/dota2-tournament-fallback.webp";

function formatOptionalDate(value: string | null | undefined, fallback: string) {
  return value ? formatDateTime(value) : fallback;
}

export function TournamentCard({ tournament }: { tournament: Tournament }) {
  const registrationCapacity =
    tournament.teamsCount > 0
      ? `${tournament.registrationsCount}/${tournament.teamsCount}`
      : String(tournament.registrationsCount);
  const capacityPercent =
    tournament.teamsCount > 0
      ? Math.min(100, Math.round((tournament.registrationsCount / tournament.teamsCount) * 100))
      : 0;
  const teamSizeLabel = tournament.teamSize ? `${tournament.teamSize}v${tournament.teamSize}` : null;

  return (
    <article className="tournament-card ops-card">
      <div className="tournament-card-media">
        <Image
          alt={`${tournament.title} tournament artwork`}
          className="tournament-card-image"
          fill
          sizes="(max-width: 680px) 100vw, (max-width: 1180px) 50vw, 33vw"
          src={TOURNAMENT_FALLBACK_IMAGE}
          unoptimized
        />
        <div className="tournament-card-media-shade" />
        <div className="tournament-card-media-badges">
          <StatusBadge status={tournament.status} />
          {teamSizeLabel ? <span className="ops-badge">{teamSizeLabel}</span> : null}
        </div>
      </div>

      <div className="tournament-card-body">
        <div className="card-title-row">
          <div>
            <h3>{tournament.title}</h3>
            <p>{tournament.format}</p>
          </div>
        </div>

        <p className="card-description">{tournament.description}</p>

        <div className="card-meta-grid tournament-card-meta">
          <span className="ops-mono">
            <CalendarDays size={16} />
            Starts {formatOptionalDate(tournament.startsAt, "TBD")}
          </span>
          <span className="ops-mono">
            <Clock3 size={16} />
            Registration {formatOptionalDate(tournament.registrationClosesAt, "TBD")}
          </span>
          <span className="ops-mono">
            <UsersRound size={16} />
            {registrationCapacity} teams
          </span>
        </div>

        <div className="tournament-card-capacity">
          <div>
            <span className="ops-label">Capacity</span>
            <strong className="ops-data">{registrationCapacity}</strong>
          </div>
          <span className="ops-mono">{capacityPercent}% full</span>
          <div aria-hidden="true" className="tournament-card-progress">
            <span style={{ width: `${capacityPercent}%` }} />
          </div>
        </div>

        <div className="tournament-card-footer">
          <div>
            <span className="ops-label">Prize pool</span>
            <strong>
              <Trophy size={15} />
              {tournament.prizePool}
            </strong>
          </div>

          <Link className="text-link" href={`/turnirji/${tournament.slug}`}>
            <span>Open Tournament</span>
            <ChevronRight size={16} />
          </Link>
        </div>
      </div>
    </article>
  );
}
