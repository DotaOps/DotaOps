import { AlertTriangle, GitBranch, ListOrdered, RadioTower, ShieldCheck, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

import { SectionHeader } from "@/components/section-header";
import type { BracketMatch, BracketSlot, TournamentBracket } from "@/lib/tournament-bracket-data";
import { classNames } from "@/lib/utils";

interface TournamentBracketPanelProps {
  action?: ReactNode;
  bracket: TournamentBracket | null;
  description?: string;
  error?: string | null;
  isLoading?: boolean;
  mode?: "public" | "organizer";
  onSelectMatch?: (match: BracketMatch) => void;
  selectedMatchId?: string | null;
  title?: string;
}

function labelText(value: string) {
  return value
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function finishedMatches(bracket: TournamentBracket | null) {
  return bracket?.matches.filter((match) => match.status === "finished").length ?? 0;
}

function openSlots(bracket: TournamentBracket | null) {
  return bracket?.matches.reduce(
    (total, match) => total + match.slots.filter((slot) => slot.state === "tbd" || slot.state === "source").length,
    0
  ) ?? 0;
}

function isWinnerSlot(match: BracketMatch, slot: BracketSlot) {
  return Boolean(match.winnerTeamId && slot.teamId && match.winnerTeamId === slot.teamId);
}

export function TournamentBracketPanel({
  action,
  bracket,
  description = "Playoff path based on official bracket slots and match results.",
  error,
  isLoading = false,
  mode = "public",
  onSelectMatch,
  selectedMatchId,
  title = "Bracket & Advancement"
}: TournamentBracketPanelProps) {
  const hasBracket = Boolean(bracket && bracket.matches.length > 0);

  return (
    <section className={classNames("tournament-bracket-panel ops-panel", mode === "organizer" && "is-organizer")}>
      <SectionHeader
        eyebrow={mode === "organizer" ? "Backend bracket state" : "Playoff bracket"}
        title={title}
        description={description}
        action={action ?? (
          <span className="ops-badge">
            <ShieldCheck size={14} />
            Backend sourced
          </span>
        )}
      />

      <div className="tournament-bracket-summary">
        <SummaryMetric icon={Trophy} label="Bracket Type" value={bracket ? labelText(bracket.bracketType) : "-"} />
        <SummaryMetric icon={GitBranch} label="Bracket Size" value={bracket ? String(bracket.bracketSize || "-") : "-"} />
        <SummaryMetric icon={ListOrdered} label="Rounds" value={bracket ? String(bracket.rounds.length) : "-"} />
        <SummaryMetric icon={RadioTower} label={mode === "organizer" ? "Open Slots" : "Finished Matches"} value={String(mode === "organizer" ? openSlots(bracket) : finishedMatches(bracket))} />
      </div>

      {isLoading ? (
        <BracketState
          title="Loading bracket..."
          detail="Connecting to the official tournament bracket API."
        />
      ) : error ? (
        <BracketState
          icon={AlertTriangle}
          tone="error"
          title="Bracket is unavailable."
          detail={error}
        />
      ) : !hasBracket ? (
        <BracketState
          title="Bracket has not been generated yet."
          detail="Official match slots and advancement will appear here once the organizer generates a bracket."
        />
      ) : (
        <div className="tournament-bracket-scroll" aria-label={`${title} rounds`}>
          {bracket?.rounds.map((round) => (
            <article className="tournament-bracket-round ops-card" key={round.roundNumber}>
              <div className="tournament-bracket-round-head">
                <span className="ops-label">Round {round.roundNumber}</span>
                <h3>{round.roundName ?? `Round ${round.roundNumber}`}</h3>
                <small>{round.matches.length} matches</small>
              </div>

              <div className="tournament-bracket-match-list">
                {round.matches.map((match) => {
                  const isSelected = selectedMatchId === match.matchId;
                  const canSelect = Boolean(onSelectMatch);
                  const matchBody = (
                    <>
                      <div className="tournament-bracket-match-head">
                        <span className="ops-mono">{match.matchCode}</span>
                        <span className={classNames("tournament-bracket-status", `is-${match.status.replace(/_/g, "-")}`)}>
                          {labelText(match.status)}
                        </span>
                      </div>

                      <div className="tournament-bracket-slots">
                        {match.slots.map((slot) => (
                          <div
                            className={classNames(
                              "tournament-bracket-slot",
                              `is-${slot.state}`,
                              isWinnerSlot(match, slot) && "is-winner"
                            )}
                            key={`${match.matchId}-${slot.slotNumber}`}
                          >
                            <div>
                              <span className="ops-label">Slot {slot.slotNumber}</span>
                              {slot.seedNumber ? <em>Seed #{slot.seedNumber}</em> : null}
                            </div>
                            <strong>{slot.teamName ?? slot.label}</strong>
                            {!slot.teamName && slot.sourceType !== "tbd" ? <small>{labelText(slot.sourceType)}</small> : null}
                          </div>
                        ))}
                      </div>

                      <div className="tournament-bracket-match-foot">
                        <span className="ops-mono">BO{match.bestOf}</span>
                        <strong className="ops-data">{match.scoreA}:{match.scoreB}</strong>
                        {match.winnerTeamName ? <span>{match.winnerTeamName} advanced</span> : <span>Awaiting result</span>}
                      </div>
                    </>
                  );

                  return canSelect ? (
                    <button
                      className={classNames("tournament-bracket-match", isSelected && "is-selected")}
                      key={match.matchId}
                      onClick={() => onSelectMatch?.(match)}
                      type="button"
                    >
                      {matchBody}
                    </button>
                  ) : (
                    <article className="tournament-bracket-match" key={match.matchId}>
                      {matchBody}
                    </article>
                  );
                })}
              </div>
            </article>
          ))}
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
    <article className="tournament-bracket-summary-card ops-card">
      <Icon size={18} />
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function BracketState({
  detail,
  icon: Icon = GitBranch,
  title,
  tone
}: {
  detail: string;
  icon?: LucideIcon;
  title: string;
  tone?: "error";
}) {
  return (
    <div className={classNames("tournament-bracket-state", tone === "error" && "is-error")}>
      <Icon size={18} />
      <div>
        <h3>{title}</h3>
        <p>{detail}</p>
      </div>
    </div>
  );
}
