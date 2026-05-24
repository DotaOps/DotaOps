"use client";

import { AlertTriangle, CalendarDays, Lock, Play, RefreshCw, Square, Trophy, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { TournamentScheduleResultsPanel } from "@/components/tournament-schedule-results-panel";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import { ApiRequestError, type ApiFieldError } from "@/lib/api";
import type { OrganizerTournament } from "@/lib/organizer-tournament-data";
import {
  cancelOrganizerMatch,
  finishOrganizerMatch,
  getOrganizerTournamentMatches,
  scheduleOrganizerMatch,
  startOrganizerMatch,
  submitOrganizerMatchResult,
  type TournamentMatch
} from "@/lib/tournament-match-data";

interface OrganizerMatchResultsPanelProps {
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
      error.status === 401
        ? "Login session expired. Please log in again before managing matches."
        : error.status === 403
          ? "Permission denied. Your account cannot manage matches for this tournament."
          : error.status === 404
            ? "The requested tournament or match was not found."
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

function toLocalInput(value: string | null) {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);

  return local.toISOString().slice(0, 16);
}

function fromLocalInput(value: string) {
  return value ? new Date(value).toISOString() : "";
}

function realTeams(match: TournamentMatch | null) {
  if (!match || !match.teamA.id || !match.teamB.id || match.teamA.isBye || match.teamB.isBye || match.teamA.isTbd || match.teamB.isTbd) {
    return [];
  }

  return [
    { id: match.teamA.id, label: match.teamA.name },
    { id: match.teamB.id, label: match.teamB.name }
  ];
}

function canSubmitResult(match: TournamentMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();
  return realTeams(match).length === 2 && status !== "cancelled" && status !== "finished";
}

function canSchedule(match: TournamentMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();
  return status !== "finished" && status !== "cancelled" && status !== "live";
}

function canStart(match: TournamentMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();
  return realTeams(match).length === 2 && status !== "live" && status !== "finished" && status !== "cancelled";
}

function canCancel(match: TournamentMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();
  return status !== "finished" && status !== "cancelled";
}

function canFinish(match: TournamentMatch | null) {
  if (!match) {
    return false;
  }

  const status = match.status.toLowerCase();
  return Boolean(match.winnerTeamId) && status !== "finished" && status !== "cancelled";
}

function pendingResults(matches: TournamentMatch[]) {
  return matches.filter((match) => realTeams(match).length === 2 && match.status !== "finished" && match.status !== "cancelled").length;
}

export function OrganizerMatchResultsPanel({
  onRefresh,
  tournament
}: OrganizerMatchResultsPanelProps) {
  const [cancelReason, setCancelReason] = useState("");
  const [error, setError] = useState<PanelErrorState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [matches, setMatches] = useState<TournamentMatch[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [scheduledAt, setScheduledAt] = useState("");
  const [scoreA, setScoreA] = useState("");
  const [scoreB, setScoreB] = useState("");
  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null);
  const [winnerTeamId, setWinnerTeamId] = useState("");

  const selectedMatch = useMemo(
    () => matches.find((match) => match.id === selectedMatchId) ?? matches[0] ?? null,
    [matches, selectedMatchId]
  );
  const teams = useMemo(() => realTeams(selectedMatch), [selectedMatch]);

  function syncForms(match: TournamentMatch | null) {
    if (!match) {
      setCancelReason("");
      setScheduledAt("");
      setScoreA("");
      setScoreB("");
      setWinnerTeamId("");
      return;
    }

    const nextTeams = realTeams(match);
    setCancelReason(match.cancellationReason ?? "");
    setScheduledAt(toLocalInput(match.scheduledAt));
    setScoreA(String(match.scoreA ?? 0));
    setScoreB(String(match.scoreB ?? 0));
    setWinnerTeamId(match.winnerTeamId ?? nextTeams[0]?.id ?? "");
  }

  function selectMatch(match: TournamentMatch) {
    setSelectedMatchId(match.id);
    syncForms(match);
  }

  const loadMatches = useCallback(async (preferredMatchId?: string | null, options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setIsLoading(true);
    }
    setError(null);
    setFieldErrors([]);

    try {
      const nextMatches = await getOrganizerTournamentMatches(tournament.id);
      const nextSelected =
        preferredMatchId && nextMatches.some((match) => match.id === preferredMatchId)
          ? nextMatches.find((match) => match.id === preferredMatchId) ?? null
          : nextMatches[0] ?? null;

      setMatches(nextMatches);
      setSelectedMatchId(nextSelected?.id ?? null);
      syncForms(nextSelected);
    } catch (loadError) {
      if (!options?.silent) {
        setMatches([]);
        setSelectedMatchId(null);
        syncForms(null);
      }
      setError(panelError(loadError, "Organizer match API is unavailable."));
      throw loadError;
    } finally {
      if (!options?.silent) {
        setIsLoading(false);
      }
    }
  }, [tournament.id]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadMatches(), 0);

    return () => window.clearTimeout(timeout);
  }, [loadMatches]);

  const liveSync = useTournamentLiveRefresh({
    enabled: true,
    hiddenIntervalMs: 60_000,
    intervalMs: 15_000,
    label: "organizer matches",
    onRefresh: () => loadMatches(selectedMatchId, { silent: true })
  });

  async function runMatchMutation(
    action: () => Promise<unknown>,
    successMessage: string,
    fallback: string
  ) {
    if (!selectedMatch) {
      return;
    }

    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      await action();
      setNotice(successMessage);
      await onRefresh();
      await loadMatches(selectedMatch.id);
    } catch (mutationError) {
      const nextError = panelError(mutationError, fallback);
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  async function scheduleMatch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedMatch || !scheduledAt) {
      setError({ errors: [], message: "Choose a match and schedule time before saving.", status: null });
      return;
    }

    await runMatchMutation(
      () => scheduleOrganizerMatch(selectedMatch.id, { scheduledAt: fromLocalInput(scheduledAt) }),
      "Match schedule updated through backend API.",
      "Match schedule could not be updated."
    );
  }

  async function submitResult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedMatch || !canSubmitResult(selectedMatch)) {
      setError({ errors: [], message: "Result entry requires two assigned teams and an unlocked match.", status: null });
      return;
    }

    const parsedScoreA = Number(scoreA);
    const parsedScoreB = Number(scoreB);

    if (!Number.isInteger(parsedScoreA) || !Number.isInteger(parsedScoreB) || parsedScoreA < 0 || parsedScoreB < 0 || !winnerTeamId) {
      setError({ errors: [], message: "Enter non-negative scores and select the winner.", status: null });
      return;
    }

    await runMatchMutation(
      () => submitOrganizerMatchResult(selectedMatch.id, {
        scoreA: parsedScoreA,
        scoreB: parsedScoreB,
        winnerTeamId
      }),
      "Result submitted. Match list refreshed from backend state.",
      "Result could not be submitted."
    );
  }

  return (
    <section className="org-match-results-panel org-tournament-panel ops-panel" id="match-results">
      <div className="org-tournament-panel-title">
        <div>
          <p className="ops-label">Schedule & Result Entry</p>
          <h2>Schedule & Result Entry</h2>
          <p>Manage scheduled matches and submit backend-validated results.</p>
        </div>
        <span className="ops-badge">Backend validated</span>
      </div>

      <div className="org-match-results-summary">
        <SummaryCard label="Total Matches" value={String(matches.length)} />
        <SummaryCard label="Live" value={String(matches.filter((match) => match.status === "live").length)} />
        <SummaryCard label="Pending Results" value={String(pendingResults(matches))} />
        <SummaryCard label="Finished" value={String(matches.filter((match) => match.status === "finished").length)} />
        <SummaryCard label="Validation Mode" value="Best-of" />
      </div>

      <LiveSyncIndicator
        errorCount={liveSync.errorCount}
        lastError={liveSync.lastError}
        lastUpdated={liveSync.lastUpdated}
        onRefresh={liveSync.refreshNow}
        status={liveSync.status}
      />

      {notice ? <p className="org-match-results-notice">{notice}</p> : null}
      {error ? <PanelError error={error} /> : null}
      {fieldErrors.length > 0 ? (
        <div className="org-match-results-validation">
          {fieldErrors.map((fieldError, index) => (
            <span key={`${fieldError.field ?? "request"}-${index}`}>
              <strong>{fieldError.field ?? "request"}</strong>
              {fieldError.message ?? "Invalid value"}
            </span>
          ))}
        </div>
      ) : null}

      <div className="org-match-results-layout">
        <div className="org-match-results-main">
          <TournamentScheduleResultsPanel
            action={<span className="ops-badge">{matches.length} records</span>}
            error={error?.message ?? null}
            isLoading={isLoading}
            matches={matches}
            mode="organizer"
            onSelectMatch={selectMatch}
            selectedMatchId={selectedMatch?.id ?? null}
          />
        </div>

        <aside className="org-match-results-command-rail">
          <button className="org-tournament-secondary" disabled={isMutating} onClick={() => loadMatches(selectedMatchId)} type="button">
            <RefreshCw size={16} />
            Refresh Matches
          </button>

          <form className="org-match-results-command-card" onSubmit={scheduleMatch}>
            <div>
              <span className="ops-label">Schedule selected match</span>
              <strong>{selectedMatch?.displayCode ?? "No match selected"}</strong>
            </div>
            <label>
              <span>Scheduled time</span>
              <input
                disabled={!canSchedule(selectedMatch) || isMutating}
                onChange={(event) => setScheduledAt(event.target.value)}
                type="datetime-local"
                value={scheduledAt}
              />
            </label>
            <button disabled={!canSchedule(selectedMatch) || !scheduledAt || isMutating} type="submit">
              <CalendarDays size={16} />
              Schedule Match
            </button>
          </form>

          <div className="org-match-results-action-grid">
            <button disabled={!canStart(selectedMatch) || isMutating} onClick={() => runMatchMutation(
              () => startOrganizerMatch(selectedMatch?.id ?? ""),
              "Match started through backend API.",
              "Match could not be started."
            )} type="button">
              <Play size={16} />
              Start Match
            </button>
            <button disabled={!canFinish(selectedMatch) || isMutating} onClick={() => runMatchMutation(
              () => finishOrganizerMatch(selectedMatch?.id ?? ""),
              "Match finished through backend API.",
              "Match could not be finished."
            )} type="button">
              <Square size={16} />
              Finish Match
            </button>
          </div>

          <form className="org-match-results-command-card" onSubmit={(event) => {
            event.preventDefault();
            if (!selectedMatch) {
              return;
            }
            void runMatchMutation(
              () => cancelOrganizerMatch(selectedMatch.id, { reason: cancelReason || undefined }),
              "Match cancelled through backend API.",
              "Match could not be cancelled."
            );
          }}>
            <label>
              <span>Cancel reason</span>
              <input
                disabled={!canCancel(selectedMatch) || isMutating}
                onChange={(event) => setCancelReason(event.target.value)}
                placeholder="Optional reason"
                value={cancelReason}
              />
            </label>
            <button disabled={!canCancel(selectedMatch) || isMutating} type="submit">
              <X size={16} />
              Cancel Match
            </button>
          </form>

          <form className="org-match-results-command-card" onSubmit={submitResult}>
            <div>
              <span className="ops-label">Backend result validation</span>
              <strong>BO{selectedMatch?.bestOf ?? 1}</strong>
              <p>Backend validates best-of rules, winner, score limits, and assigned teams.</p>
            </div>
            <label>
              <span>Score A</span>
              <input
                disabled={!canSubmitResult(selectedMatch) || isMutating}
                min="0"
                onChange={(event) => setScoreA(event.target.value)}
                type="number"
                value={scoreA}
              />
            </label>
            <label>
              <span>Score B</span>
              <input
                disabled={!canSubmitResult(selectedMatch) || isMutating}
                min="0"
                onChange={(event) => setScoreB(event.target.value)}
                type="number"
                value={scoreB}
              />
            </label>
            <label>
              <span>Winner</span>
              <select
                disabled={!canSubmitResult(selectedMatch) || isMutating}
                onChange={(event) => setWinnerTeamId(event.target.value)}
                value={winnerTeamId}
              >
                {teams.length === 0 ? <option value="">No assigned teams</option> : null}
                {teams.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.label}
                  </option>
                ))}
              </select>
            </label>
            <button disabled={!canSubmitResult(selectedMatch) || isMutating} type="submit">
              <Trophy size={16} />
              Submit Result
            </button>
          </form>

          <div className="org-match-results-disabled-actions">
            <button disabled type="button"><Lock size={14} /> Bulk Schedule</button>
            <button disabled type="button"><Lock size={14} /> Auto Schedule</button>
            <button disabled type="button"><Lock size={14} /> Dispute Resolution</button>
            <button disabled type="button"><Lock size={14} /> Result Audit Log</button>
            <button disabled type="button"><Lock size={14} /> Manual Bracket Advancement</button>
            <button disabled type="button"><Lock size={14} /> External Score Import</button>
          </div>
        </aside>
      </div>
    </section>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="org-match-results-summary-card ops-card">
      <span className="ops-label">{label}</span>
      <strong className="ops-data">{value}</strong>
    </article>
  );
}

function PanelError({ error }: { error: PanelErrorState }) {
  return (
    <div className="org-match-results-error">
      <AlertTriangle size={17} />
      <div>
        <strong>
          {error.status ? `${error.status} ` : null}
          Match operation failed
        </strong>
        <p>{error.message}</p>
      </div>
    </div>
  );
}
