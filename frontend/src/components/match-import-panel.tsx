"use client";

import { AlertTriangle, Clock3, History, Lock, RefreshCw, RotateCcw, UploadCloud } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { StatusBadge } from "@/components/status-badge";
import { ApiRequestError } from "@/lib/api";
import { getCurrentUserProfile } from "@/lib/auth";
import { isOrganizerRole } from "@/lib/route-access";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import {
  createMatchImport,
  getMatchImportEvents,
  getMatchImportJob,
  retryMatchImportJob,
  type MatchImportEvent,
  type MatchImportJob
} from "@/lib/match-import-data";
import { formatDateTime } from "@/lib/utils";

type AccessState = "checking" | "allowed" | "denied" | "login" | "error";

const matchIdPattern = /^[0-9]{1,20}$/;

function validateMatchId(value: string) {
  const trimmed = value.trim();

  if (!trimmed) {
    return "Match ID is required.";
  }

  if (!/^[0-9]+$/.test(trimmed)) {
    return "Match ID can contain digits only.";
  }

  if (trimmed.length > 20) {
    return "Match ID must be 20 digits or fewer.";
  }

  if (Number(trimmed) <= 0) {
    return "Match ID must be positive.";
  }

  if (!matchIdPattern.test(trimmed)) {
    return "Match ID format is invalid.";
  }

  return null;
}

function requestErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return "Login session expired. Please log in again.";
    }

    if (error.status === 403) {
      return "Only organizers and admins can import OpenDota match data.";
    }

    if (error.status === 404) {
      return "Import job was not found.";
    }

    if (error.errors.length > 0) {
      return error.errors
        .map((fieldError) => fieldError.message ?? "Import validation error.")
        .join(" ");
    }

    return error.message;
  }

  return error instanceof Error ? error.message : "Match import request failed.";
}

function readableErrorCode(value: string | null) {
  if (!value) {
    return null;
  }

  return value
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

function safeDate(value: string | null) {
  return value ? formatDateTime(value) : "Not recorded";
}

function isActiveJob(job: MatchImportJob | null) {
  return job?.status === "queued" || job?.status === "processing";
}

function mergeEvents(job: MatchImportJob, events: MatchImportEvent[]) {
  return {
    ...job,
    events: events.length > 0 ? events : job.events
  };
}

export function MatchImportPanel() {
  const [accessState, setAccessState] = useState<AccessState>("checking");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [job, setJob] = useState<MatchImportJob | null>(null);
  const [matchId, setMatchId] = useState("");
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadProfile() {
      try {
        const profile = await getCurrentUserProfile();

        if (!isMounted) {
          return;
        }

        if (!profile) {
          setAccessState("login");
          return;
        }

        setAccessState(isOrganizerRole(profile.role) ? "allowed" : "denied");
      } catch {
        if (isMounted) {
          setAccessState("error");
        }
      }
    }

    void loadProfile();

    return () => {
      isMounted = false;
    };
  }, []);

  const validationMessage = useMemo(() => validateMatchId(matchId), [matchId]);
  const jobId = job?.id ?? null;
  const displayedStatus = isSubmitting ? "processing" : job?.status ?? "idle";

  const refreshJob = useCallback(async () => {
    if (!jobId) {
      return;
    }

    try {
      const [nextJob, events] = await Promise.all([
        getMatchImportJob(jobId),
        getMatchImportEvents(jobId)
      ]);
      setJob(mergeEvents(nextJob, events));
      setError(null);
    } catch (refreshError) {
      setError(requestErrorMessage(refreshError));
      throw refreshError;
    }
  }, [jobId]);

  const liveSync = useTournamentLiveRefresh({
    enabled: Boolean(job?.id && isActiveJob(job)),
    hiddenIntervalMs: 60_000,
    intervalMs: 4_000,
    label: "match import job",
    onRefresh: refreshJob
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedMatchId = matchId.trim();
    const nextValidationMessage = validateMatchId(trimmedMatchId);

    if (nextValidationMessage) {
      setError(nextValidationMessage);
      return;
    }

    setError(null);
    setIsSubmitting(true);
    setNotice(null);

    try {
      const createdJob = await createMatchImport({ dotaMatchId: trimmedMatchId });
      const events = createdJob.id ? await getMatchImportEvents(createdJob.id) : createdJob.events;
      setJob(mergeEvents(createdJob, events));
      setNotice("Import job accepted.");
    } catch (importError) {
      setError(requestErrorMessage(importError));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function retryImport() {
    if (!job?.id || job.status !== "error") {
      return;
    }

    setError(null);
    setIsSubmitting(true);
    setNotice(null);

    try {
      const retriedJob = await retryMatchImportJob(job.id);
      const events = await getMatchImportEvents(retriedJob.id);
      setJob(mergeEvents(retriedJob, events));
      setNotice("Retry requested. The import flow restarted.");
    } catch (retryError) {
      setError(requestErrorMessage(retryError));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (accessState === "checking") {
    return (
      <section className="import-panel">
        <div className="card-title-row">
          <div>
            <p className="eyebrow">OpenDota Flow</p>
            <h3>Match Data Import</h3>
          </div>
          <StatusBadge status="idle" />
        </div>
        <p className="ops-label">Checking organizer access...</p>
      </section>
    );
  }

  if (accessState !== "allowed") {
    return (
      <section className="import-panel import-access-panel">
        <div className="card-title-row">
          <div>
            <p className="eyebrow">OpenDota Flow</p>
            <h3>Match Data Import</h3>
          </div>
          <Lock size={18} />
        </div>
        <p>
          {accessState === "login"
            ? "Login is required before importing OpenDota match data."
            : "Only organizer and admin accounts can import OpenDota match data."}
        </p>
      </section>
    );
  }

  return (
    <section className="import-panel">
      <div className="card-title-row">
        <div>
          <p className="eyebrow">OpenDota Flow</p>
          <h3>Match Data Import</h3>
        </div>
        <StatusBadge status={displayedStatus} />
      </div>

      <p className="import-panel-copy">
        Import OpenDota match data by match ID. DotaOps will normalize the match and update analytics records.
      </p>

      <form className="import-form" onSubmit={handleSubmit}>
        <label>
          <span>Match ID</span>
          <input
            inputMode="numeric"
            onChange={(event) => setMatchId(event.target.value)}
            placeholder="Enter Dota match ID"
            value={matchId}
          />
        </label>
        <button className="button button-primary" disabled={isSubmitting || Boolean(validationMessage)} type="submit">
          {isSubmitting ? <RefreshCw size={18} /> : <UploadCloud size={18} />}
          <span>{isSubmitting ? "Importing..." : "Import"}</span>
        </button>
      </form>

      {validationMessage ? <p className="import-helper">{validationMessage}</p> : null}
      {notice ? <p className="import-notice">{notice}</p> : null}
      {error ? (
        <div className="import-error">
          <AlertTriangle size={16} />
          <span>{error}</span>
        </div>
      ) : null}

      {job ? (
        <div className="import-job-shell">
          <LiveSyncIndicator
            errorCount={liveSync.errorCount}
            lastError={liveSync.lastError}
            lastUpdated={liveSync.lastUpdated}
            onRefresh={liveSync.refreshNow}
            status={liveSync.status}
          />

          <div className="import-job-grid">
            <ImportFact label="Job ID" value={job.id.slice(0, 8).toUpperCase()} />
            <ImportFact label="Dota Match" value={job.dotaMatchId} />
            <ImportFact label="Match ID" value={job.matchId ? job.matchId.slice(0, 8).toUpperCase() : "Standalone"} />
            <ImportFact label="Game ID" value={job.matchGameId ? job.matchGameId.slice(0, 8).toUpperCase() : "Pending"} />
            <ImportFact label="Created" value={safeDate(job.createdAt)} />
            <ImportFact label="Started" value={safeDate(job.startedAt)} />
            <ImportFact label="Completed" value={safeDate(job.completedAt)} />
            <ImportFact label="Updated" value={safeDate(job.updatedAt)} />
          </div>

          {job.status === "error" ? (
            <div className="import-error is-job-error">
              <AlertTriangle size={16} />
              <span>
                {readableErrorCode(job.errorCode) ? `${readableErrorCode(job.errorCode)}: ` : null}
                {job.errorMessage ?? "The import service reported an error."}
              </span>
            </div>
          ) : null}

          <div className="import-command-row">
            <button className="button button-secondary" disabled={!job.id || isSubmitting} onClick={() => void liveSync.refreshNow()} type="button">
              <RefreshCw size={16} />
              Refresh Job
            </button>
            {job.status === "error" ? (
              <button className="button button-secondary" disabled={isSubmitting} onClick={() => void retryImport()} type="button">
                <RotateCcw size={16} />
                Retry Import
              </button>
            ) : null}
          </div>

          <div className="import-event-history">
            <div className="import-event-history-title">
              <History size={16} />
              <strong>Event History</strong>
            </div>
            {job.events.length === 0 ? (
              <p className="ops-label">No import events recorded yet.</p>
            ) : (
              job.events.map((event) => (
                <article key={event.id}>
                  <Clock3 size={14} />
                  <div>
                    <strong>{event.eventType}</strong>
                    <span>{event.message ?? "Import event recorded."}</span>
                    {event.errorCode ? <em>{readableErrorCode(event.errorCode)}</em> : null}
                  </div>
                  <time>{safeDate(event.createdAt)}</time>
                </article>
              ))
            )}
          </div>
        </div>
      ) : null}

      <div className="pipeline-grid">
        <span>Input</span>
        <span>OpenDota import</span>
        <span>Normalization</span>
        <span>Metrics</span>
      </div>

      <div className="import-disabled-actions" aria-label="Unsupported import actions">
        <button disabled type="button">Cancel Import unavailable</button>
        <button disabled type="button">Delete Import unavailable</button>
        <button disabled type="button">Bulk Import unavailable</button>
        <button disabled type="button">Direct OpenDota disabled</button>
      </div>

      <p className="import-helper">
        Match import currently uses Dota match ID only. Tournament assignment can be added when that workflow is available.
      </p>
    </section>
  );
}

function ImportFact({
  label,
  value
}: {
  label: string;
  value: string;
}) {
  return (
    <article className="import-job-fact">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
