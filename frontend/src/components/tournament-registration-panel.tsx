"use client";

import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  ClipboardCheck,
  LogIn,
  RefreshCw,
  ShieldCheck,
  UserRoundCheck,
  UsersRound,
  XCircle
} from "lucide-react";
import Link from "next/link";
import { FormEvent, type ReactNode, useCallback, useEffect, useMemo, useState } from "react";

import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { ApiRequestError } from "@/lib/api";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import { listOrganizerTournaments } from "@/lib/organizer-tournament-data";
import { isOrganizerRole } from "@/lib/route-access";
import { loadTeamManagementData, type TeamManagementViewModel } from "@/lib/team-data";
import {
  checkInTournamentRegistration,
  listTeamTournamentRegistrations,
  submitTeamTournamentRegistration,
  type RegistrationStatus,
  type TournamentRegistration
} from "@/lib/tournament-registration-data";
import type { Tournament } from "@/lib/types";
import { classNames, formatDateTime } from "@/lib/utils";

type LoadState = "loading" | "anonymous" | "ready" | "error";

interface TournamentRegistrationPanelProps {
  tournament: Tournament;
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function registrationStatusLabel(status: RegistrationStatus) {
  return status
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function requestErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return "Login session expired. Please log in again.";
    }

    if (error.status === 403) {
      return "You do not have permission to perform this registration action.";
    }

    if (error.status === 409) {
      return error.message || "This team is already registered for the tournament.";
    }
  }

  return error instanceof Error ? error.message : "Registration request failed.";
}

function findTournamentRegistration(
  registrations: TournamentRegistration[],
  tournament: Tournament
) {
  return registrations.find(
    (registration) =>
      registration.tournamentId === tournament.id ||
      registration.tournamentSlug === tournament.slug
  ) ?? null;
}

function canCheckIn(registration: TournamentRegistration | null) {
  return Boolean(registration && registration.status === "approved" && !registration.checkedInAt);
}

export function TournamentRegistrationPanel({ tournament }: TournamentRegistrationPanelProps) {
  const [contactEmail, setContactEmail] = useState("");
  const [data, setData] = useState<TeamManagementViewModel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isMutating, setIsMutating] = useState(false);
  const [message, setMessage] = useState("");
  const [manageableTournamentId, setManageableTournamentId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [registration, setRegistration] = useState<TournamentRegistration | null>(null);
  const [state, setState] = useState<LoadState>("loading");

  const load = useCallback(async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setState("loading");
      setManageableTournamentId(null);
      setNotice(null);
    }

    setError(null);

    try {
      const nextData = await loadTeamManagementData();

      if (!nextData) {
        setState("anonymous");
        setData(null);
        setRegistration(null);
        return;
      }

      setData(nextData);

      if (nextData.team && nextData.dataSource === "api") {
        const registrations = await listTeamTournamentRegistrations(nextData.team.id);
        setRegistration(findTournamentRegistration(registrations, tournament));
      } else {
        setRegistration(null);
      }

      if (isOrganizerRole(nextData.currentProfile.role)) {
        try {
          const organizerTournaments = await listOrganizerTournaments();
          const matchedTournament = organizerTournaments.find(
            (organizerTournament) =>
              organizerTournament.id === tournament.id ||
              organizerTournament.slug === tournament.slug
          );
          setManageableTournamentId(matchedTournament?.id ?? null);
        } catch {
          setManageableTournamentId(null);
        }
      }

      setState("ready");
    } catch (caught) {
      if (!options?.silent) {
        setState("error");
      }
      setError(requestErrorMessage(caught));
      throw caught;
    }
  }, [tournament]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void load(), 0);

    return () => window.clearTimeout(timeout);
  }, [load]);

  const liveSync = useTournamentLiveRefresh({
    enabled: state === "ready" && Boolean(data),
    hiddenIntervalMs: 60_000,
    intervalMs: 30_000,
    label: "registration status",
    onRefresh: () => load({ silent: true })
  });

  const activeMembers = useMemo(
    () => data?.members.filter((member) => member.active) ?? [],
    [data?.members]
  );
  const hasRealTournamentId = uuidPattern.test(tournament.id);
  const hasBackendTeam = Boolean(data?.team && data.dataSource === "api");
  const isCaptain = Boolean(data?.isCaptain && hasBackendTeam);
  const isOrganizer = isOrganizerRole(data?.currentProfile.role);
  const canSubmit = Boolean(isCaptain && !registration && hasRealTournamentId);
  const organizerHref = `/organizator?tournamentId=${encodeURIComponent(manageableTournamentId ?? tournament.id)}&slug=${encodeURIComponent(tournament.slug)}&view=registrations`;
  const validationItems = [
    {
      ok: Boolean(data?.team),
      text: data?.team ? "Current team resolved from backend." : "Missing team profile."
    },
    {
      ok: isCaptain,
      text: isCaptain ? "Captain permission confirmed." : "Only the team captain can submit."
    },
    {
      ok: activeMembers.length >= 5,
      text:
        activeMembers.length >= 5
          ? "Roster has enough active members."
          : "Backend may reject incomplete rosters."
    },
    {
      ok: tournament.status === "registration" || tournament.status === "published",
      text:
        tournament.status === "registration" || tournament.status === "published"
          ? "Tournament can accept registrations."
          : "Registration window may be closed."
    },
    {
      ok: hasRealTournamentId,
      text: hasRealTournamentId
        ? "Tournament backend id is available."
        : "Real tournament backend id is required."
    }
  ];

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!data?.team) {
      setError("A real backend team is required before submitting a tournament registration.");
      return;
    }

    if (!isCaptain) {
      setError("Only the team captain can submit this team's tournament registration.");
      return;
    }

    if (!hasRealTournamentId) {
      setError("Registration requires a real backend tournament id.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      const created = await submitTeamTournamentRegistration(tournament.id, {
        contactEmail,
        message,
        teamId: data.team.id
      });
      setRegistration(created);
      setNotice("Registration submitted. Status is pending organizer review.");
      setMessage("");
      setContactEmail("");
    } catch (caught) {
      setError(requestErrorMessage(caught));
    } finally {
      setIsMutating(false);
    }
  }

  async function checkIn() {
    if (!registration) {
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await checkInTournamentRegistration(tournament.id, registration.id);
      setRegistration(updated);
      setNotice("Team checked in successfully.");
    } catch (caught) {
      setError(requestErrorMessage(caught));
    } finally {
      setIsMutating(false);
    }
  }

  if (state === "loading") {
    return (
      <section className="tournament-registration-shell ops-panel">
        <p className="ops-label">Registration uplink</p>
        <h2>Loading team registration state...</h2>
      </section>
    );
  }

  if (state === "anonymous") {
    return (
      <section className="tournament-registration-shell ops-panel">
        <div>
          <p className="ops-label">Registration protocol</p>
          <h2>Login required</h2>
          <p>Log in to register your team for this tournament.</p>
        </div>
        <div className="tournament-registration-actions">
          <Link className="ops-button-primary button" href="/login">
            <LogIn size={17} />
            Login
          </Link>
          <Link className="ops-button-secondary button" href="/register">
            Register
          </Link>
        </div>
      </section>
    );
  }

  if (state === "error") {
    return (
      <section className="tournament-registration-shell ops-panel">
        <div>
          <p className="ops-label">Registration protocol</p>
          <h2>Registration state unavailable</h2>
          <p>{error}</p>
        </div>
        <button className="ops-button-secondary button" onClick={() => void load()} type="button">
          <RefreshCw size={17} />
          Retry
        </button>
      </section>
    );
  }

  return (
    <section className="tournament-registration-workspace">
      {state === "ready" && data ? (
        <LiveSyncIndicator
          errorCount={liveSync.errorCount}
          lastError={liveSync.lastError}
          lastUpdated={liveSync.lastUpdated}
          onRefresh={liveSync.refreshNow}
          status={liveSync.status}
        />
      ) : null}

      {notice ? <p className="tournament-registration-message is-success">{notice}</p> : null}
      {error ? <p className="tournament-registration-message is-error">{error}</p> : null}

      {registration ? (
        <RegistrationStatusView
          isMutating={isMutating}
          onCheckIn={checkIn}
          registration={registration}
          tournament={tournament}
        />
      ) : isOrganizer && !isCaptain && manageableTournamentId ? (
        <RegistrationInfoCard
          actions={
            <Link className="ops-button-primary button" href={organizerHref}>
              Open Organizer
            </Link>
          }
          detail="Organizer accounts manage tournament submissions from the protected organizer workspace. Captain-only team submission is hidden here unless this account is also the team's captain."
          eyebrow="Organizer controls"
          title="Manage registrations in Organizer"
        />
      ) : isOrganizer && !isCaptain ? (
        <RegistrationInfoCard
          actions={
            <Link className="ops-button-secondary button" href="/turnirji">
              View Tournaments
            </Link>
          }
          detail="This public tournament is not managed by your organizer account."
          eyebrow="Read-only public tournament"
          title="Organizer management unavailable"
        />
      ) : !hasBackendTeam ? (
        <RegistrationInfoCard
          actions={
            <Link className="ops-button-primary button" href="/ekipe">
              View My Team
            </Link>
          }
          detail="You need to join or create a team before registering for tournaments."
          eyebrow="Team required"
          title="No team found"
        />
      ) : !isCaptain ? (
        <RegistrationInfoCard
          actions={
            <Link className="ops-button-primary button" href="/ekipe">
              View My Team
            </Link>
          }
          detail="Only the team captain can submit this team to a tournament."
          eyebrow="Captain permission required"
          teamName={data?.team?.name}
          title="Team registration is captain-only"
        />
      ) : (
        <section className="tournament-registration-grid">
          <div className="tournament-registration-main ops-panel">
            <p className="ops-label">Registration protocol</p>
            <h2>Team Submission</h2>
            <p>
              Submit the current team roster for organizer review. Backend validation will enforce
              duplicate registrations, roster readiness, captain permission, capacity, and timing.
            </p>

            <div className="tournament-registration-summary">
              <article>
                <span>Tournament</span>
                <strong>{tournament.title}</strong>
              </article>
              <article>
                <span>Status</span>
                <strong>{tournament.status}</strong>
              </article>
              <article>
                <span>Format</span>
                <strong>{tournament.format}</strong>
              </article>
              <article>
                <span>Teams</span>
                <strong>
                  {tournament.registrationsCount}/{tournament.teamsCount}
                </strong>
              </article>
              <article>
                <span>Deadline</span>
                <strong>{tournament.registrationClosesAt ? formatDateTime(tournament.registrationClosesAt) : "TBD"}</strong>
              </article>
            </div>

            <form className="tournament-registration-form" onSubmit={submit}>
              <label>
                <span>Selected Team</span>
                <input readOnly value={data?.team ? `${data.team.name} (${data.team.tag ?? "NO TAG"})` : "No backend team"} />
              </label>
              <label>
                <span>Captain Contact Email</span>
                <input
                  onChange={(event) => setContactEmail(event.target.value)}
                  placeholder={data?.currentProfile.email ?? "captain@team.gg"}
                  type="email"
                  value={contactEmail}
                />
              </label>
              <label className="tournament-registration-field-wide">
                <span>Registration Message</span>
                <textarea
                  maxLength={1000}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder="Optional note for tournament staff..."
                  rows={4}
                  value={message}
                />
              </label>

              <div className="tournament-registration-actions">
                <button className="ops-button-primary button" disabled={!canSubmit || isMutating} type="submit">
                  <ClipboardCheck size={17} />
                  {isMutating ? "Submitting..." : "Submit Registration"}
                </button>
                <Link className="ops-button-secondary button" href={`/turnirji/${tournament.slug}`}>
                  Back to Tournament
                </Link>
                <Link className="ops-button-secondary button" href="/ekipe">
                  View My Team
                </Link>
              </div>
            </form>
          </div>

          <aside className="tournament-registration-side">
            <TeamReadinessCard data={data} activeMembers={activeMembers} />
            <ValidationPanel items={validationItems} />
          </aside>
        </section>
      )}
    </section>
  );
}

function RegistrationInfoCard({
  actions,
  detail,
  eyebrow,
  teamName,
  title
}: {
  actions: ReactNode;
  detail: string;
  eyebrow: string;
  teamName?: string | null;
  title: string;
}) {
  return (
    <section className="tournament-registration-shell ops-panel">
      <div>
        <p className="ops-label">{eyebrow}</p>
        <h2>{title}</h2>
        {teamName ? <p>Current team: <strong>{teamName}</strong></p> : null}
        <p>{detail}</p>
      </div>
      <div className="tournament-registration-actions">
        {actions}
      </div>
    </section>
  );
}

function TeamReadinessCard({
  activeMembers,
  data
}: {
  activeMembers: TeamManagementViewModel["members"];
  data: TeamManagementViewModel | null;
}) {
  return (
    <section className="tournament-registration-card ops-panel">
      <div className="tournament-registration-card-title">
        <UsersRound size={18} />
        <h3>Selected Team</h3>
      </div>
      {data?.team ? (
        <>
          <strong>{data.team.name}</strong>
          <p>
            Captain: {data.team.captainNickname ?? "Unknown"} / Roster {activeMembers.length}/5
          </p>
          <div className="tournament-registration-roster">
            {activeMembers.slice(0, 5).map((member) => (
              <span key={member.id}>{member.nickname}</span>
            ))}
          </div>
        </>
      ) : (
        <p>No current team was found for this account.</p>
      )}
    </section>
  );
}

function ValidationPanel({
  items
}: {
  items: Array<{ ok: boolean; text: string }>;
}) {
  return (
    <section className="tournament-registration-card ops-panel">
      <div className="tournament-registration-card-title">
        <AlertTriangle size={18} />
        <h3>Validation Matrix</h3>
      </div>
      <div className="tournament-registration-checks">
        {items.map((item) => (
          <p className={classNames(item.ok ? "is-ok" : "is-blocked")} key={item.text}>
            {item.ok ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
            {item.text}
          </p>
        ))}
      </div>
    </section>
  );
}

function RegistrationStatusView({
  isMutating,
  onCheckIn,
  registration,
  tournament
}: {
  isMutating: boolean;
  onCheckIn: () => void;
  registration: TournamentRegistration;
  tournament: Tournament;
}) {
  const timeline = [
    { active: true, label: "Submitted", value: registration.createdAt },
    { active: registration.status !== "pending", label: "Reviewed", value: registration.reviewedAt },
    { active: Boolean(registration.checkedInAt), label: "Checked-in", value: registration.checkedInAt }
  ];

  return (
    <section className="tournament-registration-status-grid">
      <div className="tournament-registration-status-main ops-panel">
        <p className="ops-label">Registration status</p>
        <h2>
          Registration Status: <span>{tournament.title}</span>
        </h2>
        <div className={classNames("tournament-registration-status-orb", `status-${registration.displayStatus}`)}>
          <ShieldCheck size={36} />
          <strong>{registrationStatusLabel(registration.displayStatus)}</strong>
        </div>
        <p>
          Team {registration.teamName} is currently marked as{" "}
          <strong>{registrationStatusLabel(registration.displayStatus)}</strong>.
        </p>
      </div>

      <div className="tournament-registration-card ops-panel">
        <div className="tournament-registration-card-title">
          <UserRoundCheck size={18} />
          <h3>Submission</h3>
        </div>
        <dl className="tournament-registration-details">
          <dt>Team</dt>
          <dd>{registration.teamName}</dd>
          <dt>Captain</dt>
          <dd>{registration.captainNickname ?? "Unknown"}</dd>
          <dt>Submitted</dt>
          <dd>{registration.createdAt ? formatDateTime(registration.createdAt) : "Unknown"}</dd>
          <dt>Reviewed</dt>
          <dd>{registration.reviewedAt ? formatDateTime(registration.reviewedAt) : "Pending"}</dd>
          <dt>Seed</dt>
          <dd>{registration.seedNumber ?? "Not assigned"}</dd>
        </dl>
      </div>

      <div className="tournament-registration-card ops-panel">
        <div className="tournament-registration-card-title">
          <Clock3 size={18} />
          <h3>Status Timeline</h3>
        </div>
        <div className="tournament-registration-timeline">
          {timeline.map((item) => (
            <article className={classNames(item.active && "is-active")} key={item.label}>
              <span />
              <div>
                <strong>{item.label}</strong>
                <p>{item.value ? formatDateTime(item.value) : "Awaiting signal"}</p>
              </div>
            </article>
          ))}
        </div>
        <div className="tournament-registration-actions is-column">
          <button className="ops-button-primary button" disabled={!canCheckIn(registration) || isMutating} onClick={onCheckIn} type="button">
            <CheckCircle2 size={17} />
            {registration.checkedInAt ? "Checked In" : "Check In"}
          </button>
          <button className="ops-button-secondary button" disabled type="button">
            Cancel Registration unavailable
          </button>
          <Link className="ops-button-secondary button" href="/ekipe">
            View Registration Team
          </Link>
        </div>
      </div>
    </section>
  );
}
