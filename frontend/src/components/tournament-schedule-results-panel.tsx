import { Activity, AlertTriangle, CalendarDays, Clock, RadioTower, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { SectionHeader } from "@/components/section-header";
import type { TournamentMatch } from "@/lib/tournament-match-data";
import { classNames, formatDateTime } from "@/lib/utils";

interface TournamentScheduleResultsPanelProps {
  action?: ReactNode;
  error?: string | null;
  isLoading?: boolean;
  matches: TournamentMatch[];
  mode?: "public" | "organizer";
  onSelectMatch?: (match: TournamentMatch) => void;
  selectedMatchId?: string | null;
  title?: string;
}

function statusLabel(status: string) {
  return status
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function safeDate(value: string | null) {
  return value ? formatDateTime(value) : "Not scheduled";
}

function summary(matches: TournamentMatch[]) {
  return {
    finished: matches.filter((match) => match.status === "finished").length,
    live: matches.filter((match) => match.status === "live").length,
    total: matches.length,
    upcoming: matches.filter((match) => match.status !== "finished" && match.status !== "cancelled" && match.status !== "live").length
  };
}

function teamClass(match: TournamentMatch, teamId: string | null) {
  return classNames(
    "schedule-results-team",
    teamId && match.winnerTeamId === teamId && "is-winner"
  );
}

export function TournamentScheduleResultsPanel({
  action,
  error,
  isLoading = false,
  matches,
  mode = "public",
  onSelectMatch,
  selectedMatchId,
  title = "Schedule & Results"
}: TournamentScheduleResultsPanelProps) {
  const counts = summary(matches);
  const canSelect = Boolean(onSelectMatch);

  return (
    <section className={classNames("schedule-results-panel ops-panel", mode === "organizer" && "is-organizer")}>
      <SectionHeader
        eyebrow={mode === "organizer" ? "Organizer match control" : "Official schedule"}
        title={title}
        description="Official match times, statuses, and submitted scores."
        action={action}
      />

      <div className="schedule-results-summary">
        <SummaryMetric icon={CalendarDays} label="Matches" value={String(counts.total)} />
        <SummaryMetric icon={RadioTower} label="Live" value={String(counts.live)} />
        <SummaryMetric icon={Trophy} label="Finished" value={String(counts.finished)} />
        <SummaryMetric icon={Clock} label="Upcoming" value={String(counts.upcoming)} />
      </div>

      {isLoading ? (
        <ScheduleState title="Loading match schedule..." detail="Connecting to the official tournament match API." />
      ) : error ? (
        <ScheduleState icon={AlertTriangle} tone="error" title="Schedule is unavailable." detail={error} />
      ) : matches.length === 0 ? (
        <ScheduleState title="No matches scheduled yet." detail="Official match times and results will appear here once matches are available." />
      ) : (
        <div className="schedule-results-list">
          {matches.map((match) => {
            const selected = selectedMatchId === match.id;
            const matchBody = (
              <>
                <div className="schedule-results-match-head">
                  <div>
                    <span className="ops-label">
                      {match.stageName ?? "Tournament"} / {match.roundName ?? `Round ${match.roundNumber || 1}`}
                    </span>
                    <strong className="ops-mono">{match.displayCode}</strong>
                  </div>
                  <span className={classNames("schedule-results-status", `is-${match.status.replace(/_/g, "-")}`)}>
                    {statusLabel(match.status)}
                  </span>
                </div>

                <div className="schedule-results-teams">
                  <div className={teamClass(match, match.teamA.id)}>
                    <span>{match.teamA.seedNumber ? `#${match.teamA.seedNumber}` : "A"}</span>
                    <strong>{match.teamA.name}</strong>
                    {match.teamA.sourceLabel ? <small>{match.teamA.sourceLabel}</small> : null}
                  </div>
                  <div className="schedule-results-score">
                    <strong className="ops-data">{match.scoreA}:{match.scoreB}</strong>
                    <span>BO{match.bestOf}</span>
                  </div>
                  <div className={teamClass(match, match.teamB.id)}>
                    <span>{match.teamB.seedNumber ? `#${match.teamB.seedNumber}` : "B"}</span>
                    <strong>{match.teamB.name}</strong>
                    {match.teamB.sourceLabel ? <small>{match.teamB.sourceLabel}</small> : null}
                  </div>
                </div>

                <div className="schedule-results-meta">
                  <span>
                    <Clock size={14} />
                    {safeDate(match.scheduledAt)}
                  </span>
                  {match.winnerTeamName ? (
                    <span>
                      <Trophy size={14} />
                      {match.winnerTeamName} won
                    </span>
                  ) : null}
                  {match.cancellationReason ? (
                    <span>
                      <AlertTriangle size={14} />
                      {match.cancellationReason}
                    </span>
                  ) : null}
                </div>
              </>
            );

            return canSelect ? (
              <button
                className={classNames("schedule-results-match ops-card", selected && "is-selected")}
                key={match.id}
                onClick={() => onSelectMatch?.(match)}
                type="button"
              >
                {matchBody}
              </button>
            ) : (
              <article className="schedule-results-match ops-card" key={match.id}>
                {matchBody}
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function SummaryMetric({
  icon: Icon,
  label,
  value
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <article className="schedule-results-summary-card ops-card">
      <Icon size={18} />
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function ScheduleState({
  detail,
  icon: Icon = Activity,
  title,
  tone
}: {
  detail: string;
  icon?: LucideIcon;
  title: string;
  tone?: "error";
}) {
  return (
    <div className={classNames("schedule-results-state", tone === "error" && "is-error")}>
      <Icon size={18} />
      <div>
        <h3>{title}</h3>
        <p>{detail}</p>
      </div>
    </div>
  );
}
