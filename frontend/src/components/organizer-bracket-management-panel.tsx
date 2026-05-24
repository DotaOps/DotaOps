"use client";

import { AlertTriangle, DatabaseZap, GitBranch, Lock, RefreshCw, ShieldCheck, Trophy } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import { TournamentBracketPanel } from "@/components/tournament-bracket-panel";
import { ApiRequestError, type ApiFieldError } from "@/lib/api";
import type { OrganizerTournament } from "@/lib/organizer-tournament-data";
import {
  generateOrganizerTournamentBracket,
  getOrganizerTournamentBracket,
  type BracketMatch,
  type TournamentBracket
} from "@/lib/tournament-bracket-data";
import { submitOrganizerMatchResult } from "@/lib/tournament-match-data";

interface OrganizerBracketManagementPanelProps {
  onRefresh: () => void;
  tournament: OrganizerTournament;
}

interface PanelErrorState {
  errors: ApiFieldError[];
  message: string;
  status: number | null;
}

function panelError(error: unknown, fallback: string): PanelErrorState {
  if (error instanceof ApiRequestError) {
    const detail =
      error.status === 403
        ? "Permission denied. Your account is not allowed to manage this bracket."
        : error.status === 404
          ? "Bracket or tournament record was not found."
          : error.message || fallback;

    return {
      errors: error.errors,
      message: detail,
      status: error.status
    };
  }

  return {
    errors: [],
    message: error instanceof Error ? error.message : fallback,
    status: null
  };
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

function resultTeams(match: BracketMatch | null) {
  if (!match) {
    return [];
  }

  return match.slots
    .filter((slot) => slot.teamId && slot.teamName && !slot.isBye)
    .slice(0, 2)
    .map((slot) => ({
      id: slot.teamId as string,
      name: slot.teamName as string,
      slotNumber: slot.slotNumber
    }));
}

function canEnterResult(match: BracketMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();

  return resultTeams(match).length === 2 && status !== "finished" && status !== "cancelled";
}

export function OrganizerBracketManagementPanel({
  onRefresh,
  tournament
}: OrganizerBracketManagementPanelProps) {
  const [bracket, setBracket] = useState<TournamentBracket | null>(null);
  const [error, setError] = useState<PanelErrorState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [scoreA, setScoreA] = useState("");
  const [scoreB, setScoreB] = useState("");
  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null);
  const [winnerTeamId, setWinnerTeamId] = useState("");

  const selectedMatch = useMemo(
    () => bracket?.matches.find((match) => match.matchId === selectedMatchId) ?? bracket?.matches[0] ?? null,
    [bracket?.matches, selectedMatchId]
  );
  const teams = useMemo(() => resultTeams(selectedMatch), [selectedMatch]);
  const resultAllowed = canEnterResult(selectedMatch);

  function syncResultForm(match: BracketMatch | null) {
    if (!match) {
      setScoreA("");
      setScoreB("");
      setWinnerTeamId("");
      return;
    }

    const nextTeams = resultTeams(match);
    setScoreA(String(match.scoreA ?? 0));
    setScoreB(String(match.scoreB ?? 0));
    setWinnerTeamId(match.winnerTeamId ?? nextTeams[0]?.id ?? "");
  }

  function selectMatch(match: BracketMatch) {
    setSelectedMatchId(match.matchId);
    syncResultForm(match);
  }

  const loadBracket = useCallback(async (preferredMatchId?: string | null) => {
    setIsLoading(true);
    setError(null);
    setFieldErrors([]);

    try {
      const nextBracket = await getOrganizerTournamentBracket(tournament.id);
      const nextSelectedMatch =
        preferredMatchId && nextBracket.matches.some((match) => match.matchId === preferredMatchId)
          ? nextBracket.matches.find((match) => match.matchId === preferredMatchId) ?? null
          : nextBracket.matches[0] ?? null;

      setBracket(nextBracket);
      setSelectedMatchId(nextSelectedMatch?.matchId ?? null);
      syncResultForm(nextSelectedMatch);
    } catch (loadError) {
      setBracket(null);
      setSelectedMatchId(null);
      syncResultForm(null);
      setError(panelError(loadError, "Organizer bracket API is unavailable."));
    } finally {
      setIsLoading(false);
    }
  }, [tournament.id]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadBracket(), 0);

    return () => window.clearTimeout(timeout);
  }, [loadBracket]);

  async function generateBracket() {
    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      const generated = await generateOrganizerTournamentBracket(tournament.id, {
        stageName: "Playoffs"
      });
      setBracket(generated);
      setSelectedMatchId(generated.matches[0]?.matchId ?? null);
      syncResultForm(generated.matches[0] ?? null);
      setNotice("Bracket generated through backend API.");
      await onRefresh();
      await loadBracket(generated.matches[0]?.matchId ?? null);
    } catch (generateError) {
      const nextError = panelError(generateError, "Bracket could not be generated.");
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  async function submitResult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedMatch || !resultAllowed) {
      setError({
        errors: [],
        message: "Result entry requires a selected match with two assigned teams.",
        status: null
      });
      return;
    }

    const parsedScoreA = Number(scoreA);
    const parsedScoreB = Number(scoreB);

    if (!Number.isInteger(parsedScoreA) || !Number.isInteger(parsedScoreB) || parsedScoreA < 0 || parsedScoreB < 0 || !winnerTeamId) {
      setError({
        errors: [],
        message: "Enter non-negative scores and choose the winning team.",
        status: null
      });
      return;
    }

    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      await submitOrganizerMatchResult(selectedMatch.matchId, {
        scoreA: parsedScoreA,
        scoreB: parsedScoreB,
        winnerTeamId
      });
      setNotice("Result submitted. Advancement refreshed from backend state.");
      await onRefresh();
      await loadBracket(selectedMatch.matchId);
    } catch (submitError) {
      const nextError = panelError(submitError, "Result could not be submitted.");
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  return (
    <section className="org-bracket-panel org-tournament-panel ops-panel" id="bracket-control">
      <div className="org-tournament-panel-title">
        <div>
          <p className="ops-label">Bracket Control</p>
          <h2>Bracket Control</h2>
          <p>Generate bracket slots, monitor advancement, and submit supported results.</p>
        </div>
        <span className="ops-badge">
          <DatabaseZap size={14} />
          Backend advancement
        </span>
      </div>

      <div className="org-bracket-summary-grid">
        <SummaryCard label="Bracket Status" value={bracket?.matches.length ? "Generated" : "Empty"} />
        <SummaryCard label="Generated Size" value={bracket ? String(bracket.bracketSize || "-") : "-"} />
        <SummaryCard label="Open Slots" value={String(openSlots(bracket))} />
        <SummaryCard label="Finished Matches" value={String(finishedMatches(bracket))} />
        <SummaryCard label="Advancement Mode" value="Backend" />
      </div>

      {notice ? <p className="org-bracket-notice">{notice}</p> : null}
      {error ? <PanelError error={error} /> : null}
      {fieldErrors.length > 0 ? (
        <div className="org-bracket-validation">
          {fieldErrors.map((fieldError, index) => (
            <span key={`${fieldError.field ?? "request"}-${index}`}>
              <strong>{fieldError.field ?? "request"}</strong>
              {fieldError.message ?? "Invalid value"}
            </span>
          ))}
        </div>
      ) : null}

      <div className="org-bracket-layout">
        <div className="org-bracket-main">
          <TournamentBracketPanel
            action={
              <span className="ops-badge">
                <ShieldCheck size={14} />
                Organizer API
              </span>
            }
            bracket={bracket}
            description="Official bracket slots and advancement are read from backend state."
            error={error?.message ?? null}
            isLoading={isLoading}
            mode="organizer"
            onSelectMatch={selectMatch}
            selectedMatchId={selectedMatch?.matchId ?? null}
            title="Bracket & Advancement"
          />
        </div>

        <aside className="org-bracket-command-rail">
          <button disabled={isMutating} onClick={generateBracket} type="button">
            <GitBranch size={16} />
            Generate Bracket
          </button>
          <button className="org-tournament-secondary" disabled={isMutating} onClick={() => loadBracket(selectedMatchId)} type="button">
            <RefreshCw size={16} />
            Refresh Bracket
          </button>

          <form className="org-bracket-result-form" onSubmit={submitResult}>
            <div>
              <span className="ops-label">Selected match</span>
              <strong>{selectedMatch?.matchCode ?? "No match selected"}</strong>
              <p>
                {selectedMatch
                  ? resultAllowed
                    ? "Submit result through the organizer match endpoint."
                    : "Result entry requires both teams assigned and an unlocked match."
                  : "Generate a bracket before entering results."}
              </p>
            </div>

            <label>
              <span>Score A</span>
              <input
                disabled={!resultAllowed || isMutating}
                min="0"
                onChange={(event) => setScoreA(event.target.value)}
                type="number"
                value={scoreA}
              />
            </label>

            <label>
              <span>Score B</span>
              <input
                disabled={!resultAllowed || isMutating}
                min="0"
                onChange={(event) => setScoreB(event.target.value)}
                type="number"
                value={scoreB}
              />
            </label>

            <label>
              <span>Winner</span>
              <select
                disabled={!resultAllowed || isMutating}
                onChange={(event) => setWinnerTeamId(event.target.value)}
                value={winnerTeamId}
              >
                {teams.length === 0 ? <option value="">No assigned teams</option> : null}
                {teams.map((team) => (
                  <option key={team.id} value={team.id}>
                    Slot {team.slotNumber}: {team.name}
                  </option>
                ))}
              </select>
            </label>

            <button disabled={!resultAllowed || isMutating} type="submit">
              <Trophy size={16} />
              Submit Result
            </button>
          </form>

          <div className="org-bracket-disabled-actions">
            <button disabled type="button"><Lock size={14} /> Rebuild Bracket</button>
            <button disabled type="button"><Lock size={14} /> Force Regenerate</button>
            <button disabled type="button"><Lock size={14} /> Manual Advancement</button>
            <button disabled type="button"><Lock size={14} /> Seed Override</button>
            <button disabled type="button"><Lock size={14} /> Slot Override</button>
            <button disabled type="button"><Lock size={14} /> Bracket Audit Log</button>
            <button disabled type="button"><Lock size={14} /> Double Elimination Generator</button>
          </div>
        </aside>
      </div>
    </section>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="org-bracket-summary-card ops-card">
      <span className="ops-label">{label}</span>
      <strong className="ops-data">{value}</strong>
    </article>
  );
}

function PanelError({ error }: { error: PanelErrorState }) {
  return (
    <div className="org-bracket-error">
      <AlertTriangle size={17} />
      <div>
        <strong>
          {error.status ? `${error.status} ` : null}
          Bracket operation failed
        </strong>
        <p>{error.message}</p>
      </div>
    </div>
  );
}
