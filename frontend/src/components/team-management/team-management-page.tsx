"use client";

import {
  BarChart3,
  Check,
  ChevronRight,
  Mail,
  RefreshCcw,
  Save,
  Shield,
  Swords,
  TerminalSquare,
  Trash2,
  UserMinus,
  UserPlus,
  UsersRound
} from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import type { RefObject } from "react";
import type { LucideIcon } from "lucide-react";

import { RouteLoadingSkeleton } from "@/components/route-loading-skeleton";
import {
  createTeam,
  createTeamJoinRequest,
  acceptTeamInvitation,
  acceptTeamJoinRequest,
  cancelTeamJoinRequest,
  cancelTeamInvitation,
  declineTeamJoinRequest,
  declineTeamInvitation,
  disbandTeam,
  getTeamRosterProfile,
  leaveCurrentTeam,
  loadTeamManagementData,
  removeTeamMember,
  sendTeamInvitation,
  transferTeamOwnership,
  updateTeamMemberRole,
  type TeamInvitation,
  type TeamInvitationStatus,
  type TeamJoinRequest,
  type TeamManagementViewModel,
  type TeamManualPlayer,
  type TeamMember,
  type TeamMemberRole,
  type TeamRosterProfile,
  type TeamSummary,
  type CreateTeamInput
} from "@/lib/team-data";
import { classNames } from "@/lib/utils";
import { ApiRequestError } from "@/lib/api";

type TeamTab = "overview" | "roster" | "invitations";
type InviteFilter = "all" | TeamInvitationStatus;
type RosterProfileTab = "profile" | "stats";
type TeamConfirmAction = "leave" | "disband";

const tabs: Array<{ id: TeamTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "roster", label: "Roster" },
  { id: "invitations", label: "Invitations" }
];

const inviteFilters: Array<{ id: InviteFilter; label: string }> = [
  { id: "all", label: "All" },
  { id: "pending", label: "Pending" },
  { id: "accepted", label: "Accepted" },
  { id: "declined", label: "Declined" },
  { id: "cancelled", label: "Cancelled" }
];

const roleOptions: Array<{ label: string; value: TeamMemberRole }> = [
  { label: "Carry / Pos 1", value: "carry" },
  { label: "Mid / Pos 2", value: "mid" },
  { label: "Offlane / Pos 3", value: "offlane" },
  { label: "Support / Pos 4", value: "support" },
  { label: "Roamer", value: "roamer" },
  { label: "Coach", value: "coach" },
  { label: "Substitute / Stand-in", value: "substitute" }
];

const emptyMembers: TeamMember[] = [];
const emptyManualPlayers: TeamManualPlayer[] = [];
const emptyInvitations: TeamInvitation[] = [];
const emptyEvents: TeamManagementViewModel["activeEvents"] = [];

function hasActiveTeamOwnerMember(team: TeamSummary, members: TeamMember[]) {
  return Boolean(
    members.some((member) => member.active && isTeamOwnerMember(team, member))
  );
}

function isTeamOwnerMember(team: TeamSummary, member: TeamMember) {
  return member.teamOwner || Boolean(team.captainProfileId && member.profileId === team.captainProfileId);
}

function needsDerivedTeamOwnerParticipant(team: TeamSummary, members: TeamMember[]) {
  return Boolean(team.captainProfileId) && !hasActiveTeamOwnerMember(team, members);
}

function rosterParticipantCount(team: TeamSummary, members: TeamMember[], manualPlayers: TeamManualPlayer[]) {
  const activeMembers = members.filter((member) => member.active).length;
  const derivedTeamOwner = needsDerivedTeamOwnerParticipant(team, members) ? 1 : 0;

  return activeMembers + manualPlayers.length + derivedTeamOwner;
}

function roleLabel(role: TeamMemberRole) {
  return roleOptions.find((option) => option.value === role)?.label ?? role;
}

function shortRole(role: TeamMemberRole) {
  if (role === "carry") {
    return "POS 1 / Carry";
  }

  if (role === "mid") {
    return "POS 2 / Mid";
  }

  if (role === "offlane") {
    return "POS 3 / Offlane";
  }

  if (role === "support") {
    return "POS 4 / Support";
  }

  if (role === "substitute") {
    return "Substitute / Stand-in";
  }

  return roleLabel(role);
}

function initials(value: string) {
  return value
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function formatRelative(value: string | null) {
  if (!value) {
    return "recent";
  }

  const date = new Date(value);
  const diff = Date.now() - date.getTime();
  const hours = Math.max(1, Math.round(diff / 1000 / 60 / 60));

  if (hours < 24) {
    return `${hours}h ago`;
  }

  return `${Math.round(hours / 24)} days ago`;
}

function formatMetric(value: number, suffix = "") {
  return `${Number.isInteger(value) ? value.toString() : value.toFixed(2)}${suffix}`;
}

function statusClass(status: TeamInvitationStatus) {
  return `team-mgmt-status-${status}`;
}

function filterInvitations(invitations: TeamInvitation[], filter: InviteFilter) {
  if (filter === "all") {
    return invitations;
  }

  return invitations.filter((invitation) => invitation.status === filter);
}

function TeamAvatar({ member, size = "regular" }: { member: TeamMember; size?: "regular" | "large" }) {
  return (
    <span
      className={classNames("team-mgmt-avatar", size === "large" && "team-mgmt-avatar-large")}
      style={member.avatarUrl ? { backgroundImage: `url(${member.avatarUrl})` } : undefined}
      aria-hidden="true"
    >
      {member.avatarUrl ? null : initials(member.displayName || member.nickname)}
    </span>
  );
}

function TeamOwnerAvatar({ team }: { team: TeamSummary }) {
  return (
    <span className="team-mgmt-avatar" aria-hidden="true">
      {initials(team.captainNickname ?? "Team owner")}
    </span>
  );
}

function DerivedTeamOwnerPreview({ team }: { team: TeamSummary }) {
  return (
    <article className="team-mgmt-roster-tile ops-panel">
      <span className="team-mgmt-captain-badge">Team owner</span>
      <TeamOwnerAvatar team={team} />
      <div>
        <strong>{team.captainNickname ?? "Team owner"}</strong>
        <p>Captain / roster participant</p>
      </div>
      <div className="team-mgmt-tile-actions">
        <button
          className="team-mgmt-later-action"
          disabled
          title="Backend player profile page required"
          type="button"
        >
          Profile
          <small>Later</small>
        </button>
        <button
          className="team-mgmt-later-action"
          disabled
          title="Backend player stats page required"
          type="button"
        >
          Stats
          <small>Later</small>
        </button>
      </div>
    </article>
  );
}

function DerivedTeamOwnerRosterCard({ team }: { team: TeamSummary }) {
  return (
    <article className="team-mgmt-member-card ops-panel">
      <span className="team-mgmt-captain-badge">Team owner</span>
      <div className="team-mgmt-member-head">
        <TeamOwnerAvatar team={team} />
        <div>
          <strong>{team.captainNickname ?? "Team owner"}</strong>
          <p>Captain / roster participant</p>
        </div>
      </div>
      <div className="team-mgmt-member-meta">
        <span>Status</span>
        <strong className="is-online">Registered account</strong>
        <span>Team access</span>
        <strong>Owner</strong>
      </div>
      <div className="team-mgmt-member-actions">
        <button
          className="team-mgmt-later-action"
          disabled
          title="Backend player profile page required"
          type="button"
        >
          Profile
          <small>Later</small>
        </button>
        <button
          className="team-mgmt-later-action"
          disabled
          title="Backend player stats page required"
          type="button"
        >
          Stats
          <small>Later</small>
        </button>
      </div>
    </article>
  );
}

function LoginRequired() {
  return (
    <section className="team-mgmt-state ops-panel">
      <p className="ops-label">Team uplink locked</p>
      <h1>Login required</h1>
      <p>Team management uses private roster, invitation, and registration data.</p>
      <Link className="button button-primary ops-button-primary" href="/login">
        Login
      </Link>
    </section>
  );
}

function OrganizerTeamAccessState() {
  return (
    <section className="team-mgmt-state ops-panel">
      <p className="ops-label">Organizer workspace</p>
      <h1>Team management is available for player accounts</h1>
      <p>Organizer accounts manage tournaments. Use a player account to create or join a team.</p>
      <div className="team-mgmt-empty-actions">
        <Link className="button button-primary ops-button-primary" href="/organizator">
          Open Organizer
        </Link>
        <Link className="button button-secondary" href="/turnirji">
          View Tournaments
        </Link>
      </div>
    </section>
  );
}

function readableError(caught: unknown, fallback: string) {
  if (caught instanceof ApiRequestError && caught.errors.length > 0) {
    const details = caught.errors
      .map((fieldError) => [fieldError.field, fieldError.message].filter(Boolean).join(": "))
      .filter(Boolean)
      .join(" ");

    return details ? `${caught.message} ${details}` : caught.message;
  }

  return caught instanceof Error ? caught.message : fallback;
}

function NoTeamState({
  availableTeams,
  canCreateTeam,
  canRequestTeamMembership,
  incomingInvitations,
  isTournamentOperator,
  isMutating,
  message,
  notice,
  onInvitationAction,
  onJoinRequestAction,
  onRequestTeamMembership,
  onCreateTeam,
  outgoingJoinRequests,
  onRefresh
}: {
  availableTeams: TeamSummary[];
  canCreateTeam: boolean;
  canRequestTeamMembership: boolean;
  incomingInvitations: TeamInvitation[];
  isTournamentOperator: boolean;
  isMutating: boolean;
  message: string | null;
  notice: string | null;
  onInvitationAction: (action: "accept" | "decline", invitationId: string) => void;
  onJoinRequestAction: (action: "cancel", requestId: string) => void;
  onRequestTeamMembership: (teamId: string, message: string) => Promise<void>;
  onCreateTeam: (input: CreateTeamInput) => Promise<void>;
  outgoingJoinRequests: TeamJoinRequest[];
  onRefresh: () => void;
}) {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [joinRequestMessage, setJoinRequestMessage] = useState("");
  const [joinRequestTeamId, setJoinRequestTeamId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [teamInput, setTeamInput] = useState<CreateTeamInput>({
    description: "",
    name: "",
    region: "",
    slug: "",
    tag: ""
  });

  async function submitCreateTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (teamInput.name.trim().length < 2) {
      setFormError("Team name must be at least 2 characters.");
      return;
    }

    await onCreateTeam(teamInput);
  }

  async function submitJoinRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (!joinRequestTeamId) {
      setFormError("Select a team before sending a join request.");
      return;
    }

    await onRequestTeamMembership(joinRequestTeamId, joinRequestMessage);
    setJoinRequestMessage("");
    setJoinRequestTeamId("");
  }

  return (
    <section className="team-mgmt-state team-mgmt-empty-state ops-panel">
      <div className="team-mgmt-empty-copy">
        <div className="team-mgmt-empty-icon" aria-hidden="true">
          <UsersRound size={32} />
        </div>
        <p className="ops-label">{isTournamentOperator ? "Organizer workspace" : "No active squad"}</p>
        <h1>
          {isTournamentOperator ? "Team creation is available for player accounts" : "You are not currently in a team"}
        </h1>
        {isTournamentOperator ? (
          <p>Organizer accounts are for tournament management. Use a player account to create or join a team.</p>
        ) : (
          <p>
            Create your own team or accept an invitation from a team owner. Once your squad is ready,
            you can manage roster slots and register for tournaments.
          </p>
        )}
        <div className="team-mgmt-empty-actions">
          {canCreateTeam ? (
            <button
              className="button button-primary ops-button-primary team-mgmt-empty-primary"
              disabled={isMutating}
              onClick={() => setShowCreateForm((current) => !current)}
              type="button"
            >
              Create Team
            </button>
          ) : null}
          {isTournamentOperator ? (
            <Link className="button button-primary ops-button-primary" href="/organizator">
              Open Organizer
            </Link>
          ) : null}
          {!canCreateTeam && !isTournamentOperator ? (
            <Link className="button button-primary ops-button-primary" href="/turnirji">
              View Tournaments
            </Link>
          ) : null}
          <button className="button button-secondary team-mgmt-empty-utility" onClick={onRefresh} type="button">
            <RefreshCcw size={16} />
            Retry
          </button>
        </div>
        {canCreateTeam ? (
          <div className="team-mgmt-empty-invite-note" aria-label="Join by invitation">
            <span className="team-mgmt-empty-invite-label">Join by invite</span>
            <span>Ask a team owner to send you an invitation.</span>
          </div>
        ) : null}
      </div>

      {canCreateTeam ? (
        <div className="team-mgmt-empty-briefing" aria-label="Team onboarding steps">
          <article>
            <Shield size={18} />
            <div>
              <strong>Create a roster</strong>
              <span>Start a team workspace and become its owner.</span>
            </div>
          </article>
          <article>
            <UserPlus size={18} />
            <div>
              <strong>Invite players</strong>
              <span>Fill roster slots from the team management screen.</span>
            </div>
          </article>
          <article>
            <Swords size={18} />
            <div>
              <strong>Join tournaments</strong>
              <span>Submit the squad when registration is open.</span>
            </div>
          </article>
        </div>
      ) : null}

      {message ? <p className="team-mgmt-message team-mgmt-error">{message}</p> : null}
      {notice ? <p className="team-mgmt-message team-mgmt-notice">{notice}</p> : null}
      {formError ? <p className="team-mgmt-message team-mgmt-error">{formError}</p> : null}
      {showCreateForm && canCreateTeam ? (
        <form className="team-mgmt-create-form" onSubmit={submitCreateTeam}>
          <label>
            <span>Team name</span>
            <input
              disabled={isMutating}
              maxLength={80}
              minLength={2}
              onChange={(event) => setTeamInput((current) => ({ ...current, name: event.target.value }))}
              placeholder="Team Liquid"
              required
              type="text"
              value={teamInput.name}
            />
          </label>
          <label>
            <span>Team tag</span>
            <input
              disabled={isMutating}
              maxLength={16}
              onChange={(event) => setTeamInput((current) => ({ ...current, tag: event.target.value }))}
              placeholder="TL"
              type="text"
              value={teamInput.tag}
            />
          </label>
          <label>
            <span>Slug</span>
            <input
              disabled={isMutating}
              maxLength={80}
              onChange={(event) => setTeamInput((current) => ({ ...current, slug: event.target.value }))}
              pattern="[A-Za-z0-9]+(-[A-Za-z0-9]+)*"
              placeholder="team-liquid"
              type="text"
              value={teamInput.slug}
            />
          </label>
          <label>
            <span>Region</span>
            <input
              disabled={isMutating}
              maxLength={80}
              onChange={(event) => setTeamInput((current) => ({ ...current, region: event.target.value }))}
              placeholder="EU West"
              type="text"
              value={teamInput.region}
            />
          </label>
          <label className="team-mgmt-create-field-wide">
            <span>Description</span>
            <textarea
              disabled={isMutating}
              maxLength={500}
              onChange={(event) => setTeamInput((current) => ({ ...current, description: event.target.value }))}
              placeholder="Short team profile for tournament operations."
              rows={4}
              value={teamInput.description}
            />
          </label>
          <div className="team-mgmt-empty-actions team-mgmt-create-field-wide">
            <button className="button button-primary ops-button-primary" disabled={isMutating} type="submit">
              {isMutating ? "Creating..." : "Create Team"}
            </button>
            <button
              className="button button-secondary"
              disabled={isMutating}
              onClick={() => {
                setFormError(null);
                setShowCreateForm(false);
              }}
              type="button"
            >
              Cancel
            </button>
          </div>
        </form>
      ) : null}
      {canRequestTeamMembership ? (
        <form className="team-mgmt-join-request-form" onSubmit={submitJoinRequest}>
          <div>
            <span className="ops-label">Request team access</span>
            <p>Send a membership request to an existing team. The team owner must approve it.</p>
          </div>
          {availableTeams.length > 0 ? (
            <>
              <label>
                <span>Team</span>
                <select
                  disabled={isMutating}
                  onChange={(event) => setJoinRequestTeamId(event.target.value)}
                  required
                  value={joinRequestTeamId}
                >
                  <option value="">Select a team</option>
                  {availableTeams.map((team) => (
                    <option key={team.id} value={team.id}>
                      {team.name}
                      {team.tag ? ` (${team.tag})` : ""}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Message (optional)</span>
                <textarea
                  disabled={isMutating}
                  maxLength={1000}
                  onChange={(event) => setJoinRequestMessage(event.target.value)}
                  placeholder="Introduce yourself to the team owner."
                  rows={3}
                  value={joinRequestMessage}
                />
              </label>
              <button className="button button-secondary" disabled={isMutating} type="submit">
                {isMutating ? "Sending..." : "Send Join Request"}
              </button>
            </>
          ) : (
            <p className="team-mgmt-muted">No teams are currently available for membership requests.</p>
          )}
        </form>
      ) : null}
      {incomingInvitations.length > 0 ? (
        <div className="team-mgmt-empty-list">
          <span className="ops-label">Incoming invitations</span>
          {incomingInvitations.map((invitation) => (
            <article key={invitation.id}>
              <strong>{invitation.teamName ?? "Team invitation"}</strong>
              <span className={classNames("team-mgmt-status", statusClass(invitation.status))}>
                {invitation.status}
              </span>
              {invitation.status === "pending" ? (
                <div className="team-mgmt-empty-invite-actions">
                  <button
                    disabled={isMutating}
                    onClick={() => onInvitationAction("accept", invitation.id)}
                    type="button"
                  >
                    Accept
                  </button>
                  <button
                    disabled={isMutating}
                    onClick={() => onInvitationAction("decline", invitation.id)}
                    type="button"
                  >
                    Decline
                  </button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
      {outgoingJoinRequests.length > 0 ? (
        <div className="team-mgmt-empty-list">
          <span className="ops-label">Your join requests</span>
          {outgoingJoinRequests.map((request) => (
            <article key={request.id}>
              <div>
                <strong>{request.teamName}</strong>
                <p>{request.message ?? "No message was provided."}</p>
              </div>
              <span className={classNames("team-mgmt-status", `team-mgmt-status-${request.status}`)}>
                {request.status}
              </span>
              {request.status === "pending" ? (
                <div className="team-mgmt-empty-invite-actions">
                  <button
                    disabled={isMutating}
                    onClick={() => onJoinRequestAction("cancel", request.id)}
                    type="button"
                  >
                    Cancel Request
                  </button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export function TeamManagementPage() {
  const [activeTab, setActiveTab] = useState<TeamTab>("overview");
  const [filter, setFilter] = useState<InviteFilter>("all");
  const [viewModel, setViewModel] = useState<TeamManagementViewModel | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isMutating, setIsMutating] = useState(false);
  const [invitee, setInvitee] = useState("");
  const [inviteRole, setInviteRole] = useState<TeamMemberRole>("substitute");
  const [roleDrafts, setRoleDrafts] = useState<Record<string, TeamMemberRole>>({});
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
  const [transferOwnerProfileId, setTransferOwnerProfileId] = useState("");
  const [confirmAction, setConfirmAction] = useState<TeamConfirmAction | null>(null);
  const [rosterProfileMember, setRosterProfileMember] = useState<TeamMember | null>(null);
  const [rosterProfileTab, setRosterProfileTab] = useState<RosterProfileTab>("profile");
  const [rosterProfile, setRosterProfile] = useState<TeamRosterProfile | null>(null);
  const [rosterProfileError, setRosterProfileError] = useState<string | null>(null);
  const [isRosterProfileLoading, setIsRosterProfileLoading] = useState(false);
  const inviteInputRef = useRef<HTMLInputElement>(null);

  const load = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await loadTeamManagementData();
      setViewModel(data);
      setRoleDrafts(
        Object.fromEntries((data?.members ?? []).map((member) => [member.id, member.role]))
      );
      setNotice(data?.protectedDataError ?? null);
    } catch (caught) {
      setViewModel(null);
      setError(caught instanceof Error ? caught.message : "Team management data could not be loaded.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    let isMounted = true;

    async function loadInitial() {
      try {
        const data = await loadTeamManagementData();

        if (!isMounted) {
          return;
        }

        setViewModel(data);
        setRoleDrafts(
          Object.fromEntries((data?.members ?? []).map((member) => [member.id, member.role]))
        );
        setNotice(data?.protectedDataError ?? null);
      } catch (caught) {
        if (!isMounted) {
          return;
        }

        setViewModel(null);
        setError(caught instanceof Error ? caught.message : "Team management data could not be loaded.");
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadInitial();

    return () => {
      isMounted = false;
    };
  }, []);

  const team = viewModel?.team ?? null;
  const members = viewModel?.members ?? emptyMembers;
  const manualPlayers = viewModel?.manualPlayers ?? emptyManualPlayers;
  const outgoingInvitations = viewModel?.outgoingInvitations ?? emptyInvitations;
  const incomingInvitations = viewModel?.incomingInvitations ?? emptyInvitations;
  const allInvitations = [...incomingInvitations, ...outgoingInvitations];
  const pendingInvites = allInvitations.filter((invitation) => invitation.status === "pending");
  const acceptedThisWeek = allInvitations.filter((invitation) => invitation.status === "accepted").length;
  const activeEvents = viewModel?.activeEvents ?? emptyEvents;
  const availableTeams = viewModel?.availableTeams ?? [];
  const canCreateTeam = Boolean(viewModel?.canCreateTeam);
  const canDisbandTeam = Boolean(viewModel?.canDisbandTeam);
  const canInvitePlayers = Boolean(viewModel?.canInvitePlayers);
  const canLeaveTeam = Boolean(viewModel?.canLeaveTeam);
  const canManageTeam = Boolean(viewModel?.canManageTeam);
  const canManageRoster = Boolean(viewModel?.canManageRoster);
  const canTransferOwnership = Boolean(viewModel?.canTransferOwnership);
  const canRequestTeamMembership = Boolean(viewModel?.canRequestTeamMembership);
  const isTournamentOperator =
    viewModel?.currentProfile.role === "organizer" || viewModel?.currentProfile.role === "admin";
  const outgoingJoinRequests = viewModel?.outgoingJoinRequests ?? [];
  const teamJoinRequests = viewModel?.teamJoinRequests ?? [];
  const currentProfileId = viewModel?.currentProfile.profileId;
  const rosterFilled = team
    ? viewModel?.slotsFilled ?? viewModel?.participantsCount ?? rosterParticipantCount(team, members, manualPlayers)
    : 0;
  const eligibleTransferMembers = useMemo(
    () =>
      team
        ? members.filter(
            (member) =>
              member.active &&
              !isTeamOwnerMember(team, member) &&
              member.profileId !== currentProfileId
          )
        : [],
    [currentProfileId, members, team]
  );
  const filteredIncoming = useMemo(
    () => filterInvitations(incomingInvitations, filter),
    [filter, incomingInvitations]
  );
  const filteredOutgoing = useMemo(
    () => filterInvitations(outgoingInvitations, filter),
    [filter, outgoingInvitations]
  );

  function setPlaceholder(message: string) {
    setError(null);
    setNotice(message);
  }

  function focusInviteForm() {
    setActiveTab("roster");
    window.setTimeout(() => inviteInputRef.current?.focus(), 80);
  }

  async function sendInvite() {
    if (!team) {
      return;
    }

    if (!canInvitePlayers) {
      setPlaceholder("Only the team owner can send roster invitations.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);
    try {
      await sendTeamInvitation(team.id, { invitee, proposedRole: inviteRole });
      setInvitee("");
      setNotice("Invitation sent successfully.");
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Invitation could not be sent.");
    } finally {
      setIsMutating(false);
    }
  }

  async function saveRosterChanges() {
    if (!team) {
      return;
    }

    if (!canManageRoster) {
      setPlaceholder("Only the team owner can save roster changes.");
      return;
    }

    const changes = members.filter((member) => roleDrafts[member.id] && roleDrafts[member.id] !== member.role);

    if (changes.length === 0) {
      setPlaceholder("No roster role changes to save.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);
    try {
      await Promise.all(
        changes.map((member) => updateTeamMemberRole(team.id, member.id, roleDrafts[member.id]))
      );
      setNotice("Roster changes saved successfully.");
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Roster changes could not be saved.");
    } finally {
      setIsMutating(false);
    }
  }

  async function removeMember(memberId: string) {
    if (!team) {
      return;
    }

    if (!canManageRoster) {
      setPlaceholder("Only the team owner can remove roster members.");
      return;
    }

    setIsMutating(true);
    setError(null);
    try {
      await removeTeamMember(team.id, memberId);
      setNotice("Roster member removed.");
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Roster member could not be removed.");
    } finally {
      setIsMutating(false);
    }
  }

  function openTransferOwnership() {
    if (!canTransferOwnership) {
      setPlaceholder("Ownership transfer is not available for this account.");
      return;
    }

    if (eligibleTransferMembers.length === 0) {
      setPlaceholder("Add another registered roster member before transferring team ownership.");
      return;
    }

    setError(null);
    setTransferOwnerProfileId(eligibleTransferMembers[0].profileId);
    setIsTransferModalOpen(true);
  }

  function closeTransferOwnership() {
    if (isMutating) {
      return;
    }

    setIsTransferModalOpen(false);
    setTransferOwnerProfileId("");
  }

  async function confirmTransferOwnership() {
    if (!team || !canTransferOwnership || !transferOwnerProfileId) {
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      await transferTeamOwnership(team.id, transferOwnerProfileId);
      setIsTransferModalOpen(false);
      setTransferOwnerProfileId("");
      await load();
      setNotice("Team ownership transferred successfully.");
    } catch (caught) {
      setError(readableError(caught, "Team ownership could not be transferred."));
    } finally {
      setIsMutating(false);
    }
  }

  function openTeamConfirm(action: TeamConfirmAction) {
    if (action === "leave" && !canLeaveTeam) {
      setPlaceholder("Transfer ownership or disband the team first.");
      return;
    }

    if (action === "disband" && !canDisbandTeam) {
      setPlaceholder("Team disband is not available for this account.");
      return;
    }

    setError(null);
    setNotice(null);
    setConfirmAction(action);
  }

  function closeTeamConfirm() {
    if (isMutating) {
      return;
    }

    setConfirmAction(null);
  }

  async function confirmTeamAction() {
    if (!confirmAction || !team) {
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      if (confirmAction === "leave") {
        await leaveCurrentTeam();
        setConfirmAction(null);
        await load();
        setNotice("You left the team workspace.");
      } else {
        await disbandTeam(team.id);
        setConfirmAction(null);
        await load();
        setNotice("Team disbanded successfully.");
      }
    } catch (caught) {
      setError(
        readableError(
          caught,
          confirmAction === "leave" ? "Team could not be left." : "Team could not be disbanded."
        )
      );
    } finally {
      setIsMutating(false);
    }
  }

  async function openRosterProfile(member: TeamMember, initialTab: RosterProfileTab) {
    if (!team || !member.active || !member.profileId) {
      setPlaceholder("Roster profile is available only for active registered members.");
      return;
    }

    setRosterProfileMember(member);
    setRosterProfileTab(initialTab);
    setRosterProfile(null);
    setRosterProfileError(null);
    setIsRosterProfileLoading(true);

    try {
      setRosterProfile(await getTeamRosterProfile(team.id, member.profileId));
    } catch (caught) {
      setRosterProfileError(readableError(caught, "Roster profile could not be loaded."));
    } finally {
      setIsRosterProfileLoading(false);
    }
  }

  function closeRosterProfile() {
    setRosterProfileMember(null);
    setRosterProfile(null);
    setRosterProfileError(null);
    setIsRosterProfileLoading(false);
  }

  async function invitationAction(
    action: "accept" | "decline" | "cancel",
    invitationId: string
  ) {
    setIsMutating(true);
    setError(null);
    setNotice(null);
    try {
      if (action === "accept") {
        await acceptTeamInvitation(invitationId);
        setNotice("Invitation accepted.");
      } else if (action === "decline") {
        await declineTeamInvitation(invitationId);
        setNotice("Invitation declined.");
      } else {
        await cancelTeamInvitation(invitationId);
        setNotice("Invitation cancelled.");
      }
      await load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Invitation action failed.");
    } finally {
      setIsMutating(false);
    }
  }

  async function joinRequestAction(
    action: "accept" | "decline" | "cancel",
    requestId: string
  ) {
    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      if (action === "accept") {
        await acceptTeamJoinRequest(requestId);
        setNotice("Join request accepted.");
      } else if (action === "decline") {
        await declineTeamJoinRequest(requestId);
        setNotice("Join request declined.");
      } else {
        await cancelTeamJoinRequest(requestId);
        setNotice("Join request cancelled.");
      }

      await load();
    } catch (caught) {
      setError(readableError(caught, "Join request action failed."));
    } finally {
      setIsMutating(false);
    }
  }

  async function requestTeamMembership(teamId: string, message: string) {
    if (!canRequestTeamMembership) {
      setError("Team membership requests are available to players without an active team.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      await createTeamJoinRequest(teamId, message);
      setNotice("Join request sent successfully.");
      await load();
    } catch (caught) {
      setError(readableError(caught, "Join request could not be sent."));
    } finally {
      setIsMutating(false);
    }
  }

  async function createNewTeam(input: CreateTeamInput) {
    if (!canCreateTeam) {
      setError("Team creation is available for player accounts.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);

    try {
      await createTeam(input);
      setNotice("Team created successfully.");
      await load();
      setActiveTab("overview");
    } catch (caught) {
      setError(readableError(caught, "Team could not be created."));
    } finally {
      setIsMutating(false);
    }
  }

  if (isLoading) {
    return <RouteLoadingSkeleton />;
  }

  if (!viewModel && !error) {
    return <LoginRequired />;
  }

  if (!viewModel) {
    return (
      <section className="team-mgmt-state ops-panel">
        <p className="ops-label">Team uplink interrupted</p>
        <h1>{error === "Login session expired. Please log in again." ? "Session expired" : "Team data unavailable"}</h1>
        <p>{error}</p>
        <div className="team-mgmt-empty-actions">
          <Link className="button button-primary ops-button-primary" href="/login">
            Login
          </Link>
          <button className="button button-secondary" onClick={load} type="button">
            Retry
          </button>
        </div>
      </section>
    );
  }

  if (viewModel.currentProfile.role === "organizer") {
    return <OrganizerTeamAccessState />;
  }

  if (!team) {
    return (
      <NoTeamState
        availableTeams={availableTeams}
        canCreateTeam={canCreateTeam}
        canRequestTeamMembership={canRequestTeamMembership}
        incomingInvitations={incomingInvitations}
        isTournamentOperator={isTournamentOperator}
        isMutating={isMutating}
        message={error}
        notice={notice}
        onCreateTeam={createNewTeam}
        onInvitationAction={(action, invitationId) => invitationAction(action, invitationId)}
        onJoinRequestAction={(action, requestId) => joinRequestAction(action, requestId)}
        onRequestTeamMembership={requestTeamMembership}
        outgoingJoinRequests={outgoingJoinRequests}
        onRefresh={load}
      />
    );
  }

  return (
    <div className="team-mgmt-page">
      <div className="team-mgmt-tab-row">
        <div className="team-mgmt-tabs" role="tablist" aria-label="Team management tabs">
          {tabs.map((tab) => (
            <button
              className={classNames("team-mgmt-tab", activeTab === tab.id && "is-active")}
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              type="button"
            >
              {tab.label}
            </button>
          ))}
        </div>
        {viewModel.currentUserTeamRole ? (
          <span className="team-mgmt-role-context">
            Team access: {viewModel.currentUserTeamRole}
          </span>
        ) : null}
      </div>

      {notice ? <p className="team-mgmt-message team-mgmt-notice">{notice}</p> : null}
      {error ? <p className="team-mgmt-message team-mgmt-error">{error}</p> : null}

      {activeTab === "overview" ? (
        <OverviewTab
          activeEvents={activeEvents}
          canDisbandTeam={canDisbandTeam}
          canInvitePlayers={canInvitePlayers}
          canLeaveTeam={canLeaveTeam}
          canManageRoster={canManageRoster}
          canTransferOwnership={canTransferOwnership}
          isMutating={isMutating}
          manualPlayers={manualPlayers}
          members={members}
          onFocusInvite={focusInviteForm}
          onOpenRosterProfile={openRosterProfile}
          onTeamConfirm={openTeamConfirm}
          onRosterTab={() => setActiveTab("roster")}
          onTransferOwnership={openTransferOwnership}
          outgoingInvitations={outgoingInvitations}
          pendingInviteCount={pendingInvites.length}
          rosterFilled={rosterFilled}
          team={team}
        />
      ) : null}

      {activeTab === "roster" ? (
        <RosterTab
          canInvitePlayers={canInvitePlayers}
          canDisbandTeam={canDisbandTeam}
          canLeaveTeam={canLeaveTeam}
          canManageRoster={canManageRoster}
          canTransferOwnership={canTransferOwnership}
          inviteRole={inviteRole}
          invitee={invitee}
          isMutating={isMutating}
          members={members}
          manualPlayers={manualPlayers}
          onInviteRoleChange={setInviteRole}
          onInviteeChange={setInvitee}
          onOpenRosterProfile={openRosterProfile}
          onRemoveMember={removeMember}
          onRoleDraftChange={(memberId, role) => setRoleDrafts((drafts) => ({ ...drafts, [memberId]: role }))}
          onSaveRoster={saveRosterChanges}
          onSendInvite={sendInvite}
          onTeamConfirm={openTeamConfirm}
          onTransferOwnership={openTransferOwnership}
          outgoingInvitations={outgoingInvitations}
          roleDrafts={roleDrafts}
          rosterFilled={rosterFilled}
          team={team}
          inviteInputRef={inviteInputRef}
        />
      ) : null}

      {activeTab === "invitations" ? (
        <InvitationsTab
          acceptedThisWeek={acceptedThisWeek}
          canInvitePlayers={canInvitePlayers}
          canManageTeam={canManageTeam}
          canManageRoster={canManageRoster}
          filter={filter}
          incomingInvitations={filteredIncoming}
          incomingTotal={incomingInvitations.length}
          isMutating={isMutating}
          manualPlayers={manualPlayers}
          members={members}
          onFilterChange={setFilter}
          onFocusInvite={focusInviteForm}
          onInvitationAction={invitationAction}
          onJoinRequestAction={joinRequestAction}
          outgoingInvitations={filteredOutgoing}
          outgoingTotal={outgoingInvitations.length}
          pendingInviteCount={pendingInvites.length}
          rosterFilled={rosterFilled}
          team={team}
          teamJoinRequests={teamJoinRequests}
        />
      ) : null}

      {isTransferModalOpen && team ? (
        <TransferOwnershipModal
          eligibleMembers={eligibleTransferMembers}
          error={error}
          isMutating={isMutating}
          onCancel={closeTransferOwnership}
          onConfirm={confirmTransferOwnership}
          onSelectionChange={setTransferOwnerProfileId}
          selectedProfileId={transferOwnerProfileId}
          team={team}
        />
      ) : null}
      {confirmAction && team ? (
        <TeamConfirmModal
          action={confirmAction}
          error={error}
          isMutating={isMutating}
          onCancel={closeTeamConfirm}
          onConfirm={confirmTeamAction}
          team={team}
        />
      ) : null}
      {rosterProfileMember ? (
        <RosterProfileModal
          activeTab={rosterProfileTab}
          error={rosterProfileError}
          isLoading={isRosterProfileLoading}
          member={rosterProfileMember}
          onClose={closeRosterProfile}
          onTabChange={setRosterProfileTab}
          profile={rosterProfile}
        />
      ) : null}
    </div>
  );
}

function TransferOwnershipModal({
  eligibleMembers,
  error,
  isMutating,
  onCancel,
  onConfirm,
  onSelectionChange,
  selectedProfileId,
  team
}: {
  eligibleMembers: TeamMember[];
  error: string | null;
  isMutating: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  onSelectionChange: (profileId: string) => void;
  selectedProfileId: string;
  team: TeamSummary;
}) {
  return (
    <div
      aria-labelledby="team-transfer-title"
      aria-modal="true"
      className="team-mgmt-modal-backdrop"
      onClick={onCancel}
      role="dialog"
    >
      <section className="team-mgmt-modal ops-panel" onClick={(event) => event.stopPropagation()}>
        <div className="team-mgmt-modal-heading">
          <span className="team-mgmt-modal-icon">
            <RefreshCcw size={20} />
          </span>
          <div>
            <p className="ops-label">Team ownership</p>
            <h2 id="team-transfer-title">Transfer Ownership</h2>
          </div>
        </div>
        <p>
          Select an active registered member to become the new owner of <strong>{team.name}</strong>.
        </p>
        <label className="team-mgmt-modal-select">
          <span>New team owner</span>
          <select
            disabled={isMutating}
            onChange={(event) => onSelectionChange(event.target.value)}
            value={selectedProfileId}
          >
            {eligibleMembers.map((member) => (
              <option key={member.profileId} value={member.profileId}>
                {member.displayName || member.nickname} - {roleLabel(member.role)}
              </option>
            ))}
          </select>
        </label>
        {error ? <p className="team-mgmt-message team-mgmt-error">{error}</p> : null}
        <p className="team-mgmt-modal-warning">
          After transfer, you will lose owner permissions for this team.
        </p>
        <div className="team-mgmt-modal-actions">
          <button className="button button-secondary" disabled={isMutating} onClick={onCancel} type="button">
            Cancel
          </button>
          <button
            className="button button-primary ops-button-primary"
            disabled={isMutating || !selectedProfileId}
            onClick={onConfirm}
            type="button"
          >
            {isMutating ? "Transferring..." : "Confirm Transfer"}
          </button>
        </div>
      </section>
    </div>
  );
}

function TeamConfirmModal({
  action,
  error,
  isMutating,
  onCancel,
  onConfirm,
  team
}: {
  action: TeamConfirmAction;
  error: string | null;
  isMutating: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  team: TeamSummary;
}) {
  const isDisband = action === "disband";

  return (
    <div
      aria-labelledby="team-confirm-title"
      aria-modal="true"
      className="team-mgmt-modal-backdrop"
      onClick={onCancel}
      role="dialog"
    >
      <section
        className={classNames("team-mgmt-modal ops-panel", isDisband && "team-mgmt-modal-danger")}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="team-mgmt-modal-heading">
          <span className="team-mgmt-modal-icon">
            {isDisband ? <Trash2 size={20} /> : <UserMinus size={20} />}
          </span>
          <div>
            <p className="ops-label">{isDisband ? "Destructive team control" : "Team membership"}</p>
            <h2 id="team-confirm-title">{isDisband ? "Disband Team" : "Leave Team"}</h2>
          </div>
        </div>
        <p>
          {isDisband ? (
            <>
              This will disband <strong>{team.name}</strong>, deactivate active members, cancel pending
              invitations, and cancel pending join requests.
            </>
          ) : (
            <>
              You will lose access to the <strong>{team.name}</strong> team workspace.
            </>
          )}
        </p>
        {error ? <p className="team-mgmt-message team-mgmt-error">{error}</p> : null}
        <p className="team-mgmt-modal-warning">
          {isDisband
            ? "This action changes the team workspace for every member."
            : "Team owners must transfer ownership or disband the team before leaving."}
        </p>
        <div className="team-mgmt-modal-actions">
          <button className="button button-secondary" disabled={isMutating} onClick={onCancel} type="button">
            Cancel
          </button>
          <button
            className={classNames("button", isDisband ? "team-mgmt-danger" : "button-primary ops-button-primary")}
            disabled={isMutating}
            onClick={onConfirm}
            type="button"
          >
            {isMutating ? "Processing..." : isDisband ? "Confirm Disband" : "Confirm Leave"}
          </button>
        </div>
      </section>
    </div>
  );
}

function RosterProfileModal({
  activeTab,
  error,
  isLoading,
  member,
  onClose,
  onTabChange,
  profile
}: {
  activeTab: RosterProfileTab;
  error: string | null;
  isLoading: boolean;
  member: TeamMember;
  onClose: () => void;
  onTabChange: (tab: RosterProfileTab) => void;
  profile: TeamRosterProfile | null;
}) {
  const displayName = profile?.displayName || member.displayName || member.nickname;
  const stats = profile?.stats;
  const hasHeroData = (profile?.mostPlayedHeroes ?? []).length > 0;
  const hasRecentMatches = (profile?.recentMatches ?? []).length > 0;

  return (
    <div
      aria-labelledby="team-roster-profile-title"
      aria-modal="true"
      className="team-mgmt-modal-backdrop"
      onClick={onClose}
      role="dialog"
    >
      <section className="team-mgmt-modal team-mgmt-profile-modal ops-panel" onClick={(event) => event.stopPropagation()}>
        <div className="team-mgmt-modal-heading">
          <TeamAvatar member={member} size="large" />
          <div>
            <p className="ops-label">Roster profile</p>
            <h2 id="team-roster-profile-title">{displayName}</h2>
          </div>
        </div>
        <div className="team-mgmt-modal-tabs" role="tablist" aria-label="Roster profile tabs">
          <button
            className={classNames(activeTab === "profile" && "is-active")}
            onClick={() => onTabChange("profile")}
            type="button"
          >
            Profile
          </button>
          <button
            className={classNames(activeTab === "stats" && "is-active")}
            onClick={() => onTabChange("stats")}
            type="button"
          >
            Stats
          </button>
        </div>
        {isLoading ? <p className="team-mgmt-muted">Loading roster profile...</p> : null}
        {error ? <p className="team-mgmt-message team-mgmt-error">{error}</p> : null}
        {!isLoading && !error && activeTab === "profile" ? (
          <div className="team-mgmt-profile-grid">
            <article>
              <span>Nickname</span>
              <strong>{profile?.nickname ?? member.nickname}</strong>
            </article>
            <article>
              <span>Display name</span>
              <strong>{profile?.displayName ?? member.displayName ?? "Not set"}</strong>
            </article>
            <article>
              <span>Role</span>
              <strong>{shortRole(profile?.role ?? member.role)}</strong>
            </article>
            <article>
              <span>Team access</span>
              <strong>{profile?.teamOwner ?? member.teamOwner ? "Team owner" : "Member"}</strong>
            </article>
            <article>
              <span>Joined</span>
              <strong>
                {(profile?.joinedAt ?? member.joinedAt)
                  ? new Date(profile?.joinedAt ?? member.joinedAt ?? "").toLocaleDateString("en", {
                      day: "2-digit",
                      month: "short",
                      year: "numeric"
                    })
                  : "Unavailable"}
              </strong>
            </article>
          </div>
        ) : null}
        {!isLoading && !error && activeTab === "stats" ? (
          <div className="team-mgmt-profile-stats">
            <div className="team-mgmt-profile-stat-grid">
              <MetricCard icon={Swords} label="Games" value={formatMetric(stats?.gamesPlayed ?? 0)} />
              <MetricCard icon={Check} label="Wins" tone="green" value={formatMetric(stats?.wins ?? 0)} />
              <MetricCard icon={BarChart3} label="Win Rate" tone="cyan" value={formatMetric(stats?.winRate ?? 0, "%")} />
              <MetricCard icon={Shield} label="KDA" tone="gold" value={formatMetric(stats?.kda ?? 0)} />
            </div>
            <div className="team-mgmt-profile-grid">
              <article>
                <span>Losses</span>
                <strong>{formatMetric(stats?.losses ?? 0)}</strong>
              </article>
              <article>
                <span>Avg kills</span>
                <strong>{formatMetric(stats?.avgKills ?? 0)}</strong>
              </article>
              <article>
                <span>Avg deaths</span>
                <strong>{formatMetric(stats?.avgDeaths ?? 0)}</strong>
              </article>
              <article>
                <span>Avg assists</span>
                <strong>{formatMetric(stats?.avgAssists ?? 0)}</strong>
              </article>
            </div>
            <section className="team-mgmt-profile-list">
              <span className="ops-label">Most played heroes</span>
              {hasHeroData ? (
                profile?.mostPlayedHeroes.map((hero, index) => (
                  <article key={`${hero.heroId ?? hero.heroName ?? "hero"}-${index}`}>
                    <strong>{hero.heroName ?? `Hero ${hero.heroId ?? "unknown"}`}</strong>
                    <span>
                      {hero.gamesPlayed ?? 0} games / {hero.winRate ?? 0}% WR
                    </span>
                  </article>
                ))
              ) : (
                <p className="team-mgmt-muted">No analyzed hero data yet.</p>
              )}
            </section>
            <section className="team-mgmt-profile-list">
              <span className="ops-label">Recent matches</span>
              {hasRecentMatches ? (
                <p className="team-mgmt-muted">Recent match records are available from the backend payload.</p>
              ) : (
                <p className="team-mgmt-muted">No recent analyzed matches yet.</p>
              )}
            </section>
          </div>
        ) : null}
        <div className="team-mgmt-modal-actions">
          <button className="button button-secondary" onClick={onClose} type="button">
            Close
          </button>
        </div>
      </section>
    </div>
  );
}

function TeamHero({
  compact = false,
  manualPlayers,
  members,
  rosterFilled,
  team
}: {
  compact?: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  rosterFilled?: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  const rosterCount = rosterFilled ?? rosterParticipantCount(team, members, manualPlayers);

  return (
    <section
      className={classNames("team-mgmt-hero ops-panel", compact && "team-mgmt-hero-compact")}
      style={
        team.bannerUrl
          ? {
              backgroundImage: `linear-gradient(90deg, rgba(11, 13, 18, 0.94), rgba(11, 13, 18, 0.72)), url("${team.bannerUrl}")`
            }
          : undefined
      }
    >
      <div className="team-mgmt-team-mark">
        <Shield size={compact ? 30 : 42} />
      </div>
      <div className="team-mgmt-hero-body">
        <div className="team-mgmt-hero-copy">
          <div className="team-mgmt-hero-tags">
            <span>{team.region ?? "Region unavailable"}</span>
            <span>Tag: {team.tag ?? "Not set"}</span>
          </div>
          <h1>
            {team.name}
            {team.tag ? ` (${team.tag})` : ""}
          </h1>
          {compact ? (
            <p>
              Team owner: <strong>{team.captainNickname ?? "Unassigned"}</strong> - Status:{" "}
              <strong>Active team</strong> - Roster: <strong>{rosterCount} participants</strong>
            </p>
          ) : (
            <p>{team.description ?? "No team description has been provided."}</p>
          )}
        </div>
        {!compact ? (
          <div className="team-mgmt-hero-meta-row">
            <div>
              <span>Team owner</span>
              <strong>{team.captainNickname ?? "Unassigned"}</strong>
            </div>
            <div>
              <span>Status</span>
              <strong className="team-mgmt-hero-status">Active team</strong>
            </div>
            <div>
              <span>Roster</span>
              <strong>{rosterCount} participants</strong>
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}

function MetricCard({
  icon: Icon,
  label,
  tone = "red",
  value
}: {
  icon: LucideIcon;
  label: string;
  tone?: "red" | "gold" | "cyan" | "green";
  value: string;
}) {
  return (
    <article className={classNames("team-mgmt-metric ops-panel", `team-mgmt-tone-${tone}`)}>
      <div>
        <span>{label}</span>
        <Icon size={17} />
      </div>
      <strong>{value}</strong>
      <em />
    </article>
  );
}

function PlannedAction({
  children,
  label = "Backend required"
}: {
  children: ReactNode;
  label?: string;
}) {
  return (
    <button className="team-mgmt-later-action" disabled type="button">
      <span>{children}</span>
      <small>{label}</small>
    </button>
  );
}

function PlannedControls({
  canDisbandTeam,
  canLeaveTeam,
  canTransferOwnership,
  isMutating,
  onDisbandTeam,
  onLeaveTeam,
  onTransferOwnership
}: {
  canDisbandTeam: boolean;
  canLeaveTeam: boolean;
  canTransferOwnership: boolean;
  isMutating?: boolean;
  onDisbandTeam?: () => void;
  onLeaveTeam?: () => void;
  onTransferOwnership?: () => void;
}) {
  return (
    <div className="team-mgmt-planned-controls">
      <span className="team-mgmt-planned-title">Team controls</span>
      {canTransferOwnership ? (
        <button
          className="team-mgmt-transfer-action"
          disabled={isMutating}
          onClick={onTransferOwnership}
          type="button"
        >
          <span>
            <RefreshCcw size={16} />
            Transfer Ownership
          </span>
          <small>Available</small>
        </button>
      ) : (
        <PlannedAction label="Not available">
          <RefreshCcw size={16} />
          Transfer Ownership
        </PlannedAction>
      )}
      {canLeaveTeam ? (
        <button className="team-mgmt-transfer-action" disabled={isMutating} onClick={onLeaveTeam} type="button">
          <span>
            <UserMinus size={16} />
            Leave Team
          </span>
          <small>Available</small>
        </button>
      ) : (
        <PlannedAction label="Transfer/disband first">
          <UserMinus size={16} />
          Leave Team
        </PlannedAction>
      )}
      {canDisbandTeam ? (
        <button className="team-mgmt-transfer-action team-mgmt-disband-action" disabled={isMutating} onClick={onDisbandTeam} type="button">
          <span>
            <Trash2 size={16} />
            Disband Team
          </span>
          <small>Available</small>
        </button>
      ) : (
        <PlannedAction label="Not available">
          <Trash2 size={16} />
          Disband Team
        </PlannedAction>
      )}
    </div>
  );
}

function CommandCenter({
  canDisbandTeam,
  canInvitePlayers,
  canLeaveTeam,
  canManageRoster,
  canTransferOwnership,
  mode,
  onFocusInvite,
  onTeamConfirm,
  onRosterTab,
  onSaveRoster,
  onSendInvite,
  onTransferOwnership,
  isMutating
}: {
  canDisbandTeam: boolean;
  canInvitePlayers: boolean;
  canLeaveTeam: boolean;
  canManageRoster: boolean;
  canTransferOwnership: boolean;
  isMutating?: boolean;
  mode: "overview" | "roster" | "invitations";
  onFocusInvite?: () => void;
  onTeamConfirm?: (action: TeamConfirmAction) => void;
  onRosterTab?: () => void;
  onSaveRoster?: () => void;
  onSendInvite?: () => void;
  onTransferOwnership?: () => void;
}) {
  if (mode === "roster") {
    return (
      <aside className="team-mgmt-side-card ops-panel">
        <h2>Command Center</h2>
        {canManageRoster ? (
          <button className="team-mgmt-command-primary" disabled={isMutating} onClick={onSaveRoster} type="button">
            <Save size={18} />
            Save Roster Changes
          </button>
        ) : null}
        {canInvitePlayers ? (
          <button disabled={isMutating} onClick={onSendInvite} type="button">
            <Mail size={18} />
            Send Invite
          </button>
        ) : null}
        {canManageRoster ? (
          <PlannedControls
            canDisbandTeam={canDisbandTeam}
            canLeaveTeam={canLeaveTeam}
            canTransferOwnership={canTransferOwnership}
            isMutating={isMutating}
            onDisbandTeam={() => onTeamConfirm?.("disband")}
            onLeaveTeam={() => onTeamConfirm?.("leave")}
            onTransferOwnership={onTransferOwnership}
          />
        ) : null}
      </aside>
    );
  }

  if (mode === "invitations") {
    return (
      <aside className="team-mgmt-side-card ops-panel">
        <h2>
          <TerminalSquare size={18} />
          Command Center
        </h2>
        {canInvitePlayers ? (
          <button className="team-mgmt-command-primary" onClick={onFocusInvite} type="button">
            New Invite
            <UserPlus size={18} />
          </button>
        ) : null}
        <p className="team-mgmt-command-note">Invitation and join request history actions require backend support.</p>
      </aside>
    );
  }

  return (
    <aside className="team-mgmt-side-card ops-panel">
      <h2>
        <Shield size={18} />
        Command Center
      </h2>
      {canManageRoster ? (
        <button onClick={onRosterTab} type="button">
          Edit Roster
          <ChevronRight size={16} />
        </button>
      ) : null}
      {canManageRoster ? (
        <PlannedControls
          canDisbandTeam={canDisbandTeam}
          canLeaveTeam={canLeaveTeam}
          canTransferOwnership={canTransferOwnership}
          isMutating={isMutating}
          onDisbandTeam={() => onTeamConfirm?.("disband")}
          onLeaveTeam={() => onTeamConfirm?.("leave")}
          onTransferOwnership={onTransferOwnership}
        />
      ) : null}
    </aside>
  );
}

function OverviewTab({
  activeEvents,
  canDisbandTeam,
  canInvitePlayers,
  canLeaveTeam,
  canManageRoster,
  canTransferOwnership,
  isMutating,
  manualPlayers,
  members,
  onFocusInvite,
  onOpenRosterProfile,
  onRosterTab,
  onTeamConfirm,
  onTransferOwnership,
  outgoingInvitations,
  pendingInviteCount,
  rosterFilled,
  team
}: {
  activeEvents: TeamManagementViewModel["activeEvents"];
  canDisbandTeam: boolean;
  canInvitePlayers: boolean;
  canLeaveTeam: boolean;
  canManageRoster: boolean;
  canTransferOwnership: boolean;
  isMutating: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  onFocusInvite: () => void;
  onOpenRosterProfile: (member: TeamMember, initialTab: RosterProfileTab) => void;
  onRosterTab: () => void;
  onTeamConfirm: (action: TeamConfirmAction) => void;
  onTransferOwnership: () => void;
  outgoingInvitations: TeamInvitation[];
  pendingInviteCount: number;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero manualPlayers={manualPlayers} members={members} rosterFilled={rosterFilled} team={team} />
      <section className="team-mgmt-metrics">
        <MetricCard icon={UsersRound} label="Roster Participants" value={String(rosterFilled)} />
        <MetricCard icon={Mail} label="Pending Invites" tone="gold" value={String(pendingInviteCount)} />
        <MetricCard icon={Swords} label="Active Events" tone="cyan" value={String(activeEvents.length)} />
        <MetricCard icon={BarChart3} label="Win Rate" tone="red" value="No data" />
      </section>
      <section className="team-mgmt-layout">
        <main className="team-mgmt-main-stack">
          <div className="team-mgmt-section-heading">
            <h2>Active Roster</h2>
            <span>
              Registered accounts and manual entries
            </span>
          </div>
          <div className="team-mgmt-roster-preview">
            {rosterFilled === 0 ? (
              <article className="team-mgmt-roster-empty ops-panel">
                <strong>No roster participants yet.</strong>
                <p>Invite registered players to build the team roster.</p>
              </article>
            ) : null}
            {needsDerivedTeamOwnerParticipant(team, members) ? (
              <DerivedTeamOwnerPreview team={team} />
            ) : null}
            {members.map((member) => (
              <article className="team-mgmt-roster-tile ops-panel" key={member.id}>
                {isTeamOwnerMember(team, member) ? (
                  <span className="team-mgmt-captain-badge">Team owner</span>
                ) : null}
                <TeamAvatar member={member} />
                <div>
                  <strong>{member.displayName || member.nickname}</strong>
                  <p>{shortRole(member.role)}</p>
                </div>
                <div className="team-mgmt-tile-actions">
                  <button
                    className="team-mgmt-profile-action"
                    disabled={!member.active}
                    onClick={() => onOpenRosterProfile(member, "profile")}
                    title="Open roster profile"
                    type="button"
                  >
                    Profile
                  </button>
                  <button
                    className="team-mgmt-profile-action"
                    disabled={!member.active}
                    onClick={() => onOpenRosterProfile(member, "stats")}
                    title="Open roster stats"
                    type="button"
                  >
                    Stats
                  </button>
                </div>
              </article>
            ))}
            {manualPlayers.map((player) => (
              <article className="team-mgmt-roster-tile ops-panel" key={player.id}>
                <span className="team-mgmt-avatar" aria-hidden="true">
                  {initials(player.displayName)}
                </span>
                <div>
                  <strong>{player.nickname ?? player.displayName}</strong>
                  <p>Manual player</p>
                </div>
                <span className="team-mgmt-manual-badge">Manual</span>
              </article>
            ))}
            <button className="team-mgmt-invite-tile ops-panel" disabled={!canInvitePlayers} onClick={onFocusInvite} type="button">
              <UserPlus size={24} />
              Invite New Player
            </button>
          </div>
          <InviteRegistry invitations={outgoingInvitations} />
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canDisbandTeam={canDisbandTeam}
            canInvitePlayers={canInvitePlayers}
            canLeaveTeam={canLeaveTeam}
            canManageRoster={canManageRoster}
            canTransferOwnership={canTransferOwnership}
            isMutating={isMutating}
            mode="overview"
            onRosterTab={onRosterTab}
            onTeamConfirm={onTeamConfirm}
            onTransferOwnership={onTransferOwnership}
          />
        </aside>
      </section>
    </>
  );
}

function InviteRegistry({
  invitations
}: {
  invitations: TeamInvitation[];
}) {
  return (
    <section className="team-mgmt-registry ops-panel">
      <div className="team-mgmt-panel-title">
        <h2>Invite Registry</h2>
      </div>
      {invitations.length === 0 ? (
        <p className="team-mgmt-muted">No outgoing invitations are available for this team.</p>
      ) : (
        <div className="team-mgmt-registry-table">
          <div className="team-mgmt-registry-head">
            <span>Invitee</span>
            <span>Role</span>
            <span>Time Sent</span>
            <span>Status</span>
            <span>Actions</span>
          </div>
          {invitations.slice(0, 3).map((invitation) => (
            <article className="team-mgmt-invite-row" key={invitation.id}>
              <span className="team-mgmt-mini-avatar">{initials(invitation.inviteeNickname || invitation.inviteeEmail || "PL")}</span>
              <div>
                <strong>{invitation.inviteeNickname || invitation.inviteeEmail || "Pending player"}</strong>
              </div>
              <p>{roleLabel(invitation.proposedRole)}</p>
              <p>{formatRelative(invitation.createdAt)}</p>
              <span className={classNames("team-mgmt-status", statusClass(invitation.status))}>{invitation.status}</span>
              <span className="team-mgmt-inline-note">Manage in Invitations</span>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function RosterTab({
  canInvitePlayers,
  canDisbandTeam,
  canLeaveTeam,
  canManageRoster,
  canTransferOwnership,
  inviteInputRef,
  inviteRole,
  invitee,
  isMutating,
  manualPlayers,
  members,
  onInviteRoleChange,
  onInviteeChange,
  onOpenRosterProfile,
  onRemoveMember,
  onRoleDraftChange,
  onSaveRoster,
  onSendInvite,
  onTeamConfirm,
  onTransferOwnership,
  outgoingInvitations,
  roleDrafts,
  rosterFilled,
  team
}: {
  canInvitePlayers: boolean;
  canDisbandTeam: boolean;
  canLeaveTeam: boolean;
  canManageRoster: boolean;
  canTransferOwnership: boolean;
  inviteInputRef: RefObject<HTMLInputElement | null>;
  inviteRole: TeamMemberRole;
  invitee: string;
  isMutating: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  onInviteRoleChange: (value: TeamMemberRole) => void;
  onInviteeChange: (value: string) => void;
  onOpenRosterProfile: (member: TeamMember, initialTab: RosterProfileTab) => void;
  onRemoveMember: (memberId: string) => void;
  onRoleDraftChange: (memberId: string, role: TeamMemberRole) => void;
  onSaveRoster: () => void;
  onSendInvite: () => void;
  onTeamConfirm: (action: TeamConfirmAction) => void;
  onTransferOwnership: () => void;
  outgoingInvitations: TeamInvitation[];
  roleDrafts: Record<string, TeamMemberRole>;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero compact manualPlayers={manualPlayers} members={members} rosterFilled={rosterFilled} team={team} />
      <section className="team-mgmt-metrics team-mgmt-roster-metrics">
        <MetricCard icon={UsersRound} label="Roster Participants" value={String(rosterFilled)} />
        <MetricCard icon={Mail} label="Pending Invites" tone="gold" value={String(outgoingInvitations.filter((invite) => invite.status === "pending").length)} />
      </section>
      <p className="team-mgmt-roster-note">Roster size is checked when registering for a tournament.</p>
      <section className="team-mgmt-layout">
        <main className="team-mgmt-main-stack">
          <div className="team-mgmt-section-heading">
            <h2>
              <UsersRound size={22} />
              Active Roster
            </h2>
            <span>{rosterFilled} slots filled</span>
          </div>
          <div className="team-mgmt-roster-management-grid">
            {rosterFilled === 0 ? (
              <article className="team-mgmt-roster-empty ops-panel">
                <strong>No roster participants yet.</strong>
                <p>Use the recruitment terminal to invite registered players.</p>
              </article>
            ) : null}
            {needsDerivedTeamOwnerParticipant(team, members) ? (
              <DerivedTeamOwnerRosterCard team={team} />
            ) : null}
            {members.map((member) => (
              <article className="team-mgmt-member-card ops-panel" key={member.id}>
                {isTeamOwnerMember(team, member) ? <span className="team-mgmt-captain-badge">Team owner</span> : null}
                <div className="team-mgmt-member-head">
                  <TeamAvatar member={member} />
                  <div>
                    <strong>{member.displayName || member.nickname}</strong>
                    <p>{shortRole(roleDrafts[member.id] ?? member.role)}</p>
                  </div>
                </div>
                <div className="team-mgmt-member-meta">
                  <span>Status</span>
                  <strong className="is-online">Registered account</strong>
                  <span>Joined</span>
                  <strong>{member.joinedAt ? new Date(member.joinedAt).toLocaleDateString("en", { month: "short", year: "numeric" }) : "Unavailable"}</strong>
                </div>
                <label className="team-mgmt-role-select">
                  <span>Role</span>
                  <select
                    disabled={!canManageRoster || isMutating}
                    onChange={(event) => onRoleDraftChange(member.id, event.target.value as TeamMemberRole)}
                    value={roleDrafts[member.id] ?? member.role}
                  >
                    {roleOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="team-mgmt-member-actions">
                  <button
                    className="team-mgmt-profile-action"
                    disabled={!member.active}
                    onClick={() => onOpenRosterProfile(member, "profile")}
                    title="Open roster profile"
                    type="button"
                  >
                    Profile
                  </button>
                  <button
                    className="team-mgmt-profile-action"
                    disabled={!member.active}
                    onClick={() => onOpenRosterProfile(member, "stats")}
                    title="Open roster stats"
                    type="button"
                  >
                    Stats
                  </button>
                  {canManageRoster && !isTeamOwnerMember(team, member) ? (
                    <button disabled={isMutating} onClick={() => onRemoveMember(member.id)} type="button">
                      <Trash2 size={15} />
                    </button>
                  ) : null}
                </div>
              </article>
            ))}
            {manualPlayers.map((player) => (
              <article className="team-mgmt-member-card ops-panel" key={player.id}>
                <span className="team-mgmt-manual-badge">Manual player</span>
                <div className="team-mgmt-member-head">
                  <span className="team-mgmt-avatar" aria-hidden="true">
                    {initials(player.displayName)}
                  </span>
                  <div>
                    <strong>{player.nickname ?? player.displayName}</strong>
                    <p>Roster entry without linked account</p>
                  </div>
                </div>
                <div className="team-mgmt-member-meta">
                  <span>Status</span>
                  <strong>Manual roster entry</strong>
                  <span>Note</span>
                  <strong>{player.note ?? "No note"}</strong>
                </div>
              </article>
            ))}
          </div>
          <RecruitmentTerminal
            canInvitePlayers={canInvitePlayers}
            inviteInputRef={inviteInputRef}
            inviteRole={inviteRole}
            invitee={invitee}
            isMutating={isMutating}
            onInviteRoleChange={onInviteRoleChange}
            onInviteeChange={onInviteeChange}
            onSendInvite={onSendInvite}
          />
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canDisbandTeam={canDisbandTeam}
            canInvitePlayers={canInvitePlayers}
            canLeaveTeam={canLeaveTeam}
            canManageRoster={canManageRoster}
            canTransferOwnership={canTransferOwnership}
            isMutating={isMutating}
            mode="roster"
            onSaveRoster={onSaveRoster}
            onSendInvite={onSendInvite}
            onTeamConfirm={onTeamConfirm}
            onTransferOwnership={onTransferOwnership}
          />
        </aside>
      </section>
    </>
  );
}

function RecruitmentTerminal({
  canInvitePlayers,
  inviteInputRef,
  inviteRole,
  invitee,
  isMutating,
  onInviteRoleChange,
  onInviteeChange,
  onSendInvite
}: {
  canInvitePlayers: boolean;
  inviteInputRef: RefObject<HTMLInputElement | null>;
  inviteRole: TeamMemberRole;
  invitee: string;
  isMutating: boolean;
  onInviteRoleChange: (value: TeamMemberRole) => void;
  onInviteeChange: (value: string) => void;
  onSendInvite: () => void;
}) {
  return (
    <section className="team-mgmt-recruitment ops-panel">
      <h2>Recruitment Terminal</h2>
      <div className="team-mgmt-recruitment-grid">
        <label>
          <span>Player email</span>
          <input
            disabled={!canInvitePlayers || isMutating}
            onChange={(event) => onInviteeChange(event.target.value)}
            placeholder="player@email.com"
            ref={inviteInputRef}
            value={invitee}
          />
        </label>
        <label>
          <span>Intended Role</span>
          <select
            disabled={!canInvitePlayers || isMutating}
            onChange={(event) => onInviteRoleChange(event.target.value as TeamMemberRole)}
            value={inviteRole}
          >
            {roleOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <button disabled={!canInvitePlayers || isMutating} onClick={onSendInvite} type="button">
          {isMutating ? "Sending..." : "Send Invite"}
        </button>
      </div>
      <p>Advanced: internal profile UUID is also supported. Only the team owner can edit roster roles and send invitations.</p>
    </section>
  );
}

function InvitationsTab({
  acceptedThisWeek,
  canInvitePlayers,
  canManageTeam,
  canManageRoster,
  filter,
  incomingInvitations,
  incomingTotal,
  isMutating,
  manualPlayers,
  members,
  onFilterChange,
  onFocusInvite,
  onInvitationAction,
  onJoinRequestAction,
  outgoingInvitations,
  outgoingTotal,
  pendingInviteCount,
  rosterFilled,
  team,
  teamJoinRequests
}: {
  acceptedThisWeek: number;
  canInvitePlayers: boolean;
  canManageTeam: boolean;
  canManageRoster: boolean;
  filter: InviteFilter;
  incomingInvitations: TeamInvitation[];
  incomingTotal: number;
  isMutating: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  onFilterChange: (filter: InviteFilter) => void;
  onFocusInvite: () => void;
  onInvitationAction: (action: "accept" | "decline" | "cancel", invitationId: string) => void;
  onJoinRequestAction: (action: "accept" | "decline" | "cancel", requestId: string) => void;
  outgoingInvitations: TeamInvitation[];
  outgoingTotal: number;
  pendingInviteCount: number;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
  teamJoinRequests: TeamJoinRequest[];
}) {
  return (
    <>
      <TeamHero compact manualPlayers={manualPlayers} members={members} rosterFilled={rosterFilled} team={team} />
      <section className="team-mgmt-metrics team-mgmt-invite-metrics">
        <MetricCard icon={Mail} label="Incoming Invites" value={String(incomingTotal)} />
        <MetricCard icon={UserPlus} label="Outgoing Invites" value={String(outgoingTotal)} />
        <MetricCard icon={Check} label="Accepted Invites" tone="gold" value={String(acceptedThisWeek)} />
      </section>
      <section className="team-mgmt-layout">
        <main className="team-mgmt-main-stack">
          <div className="team-mgmt-filter-bar ops-panel">
            {inviteFilters.map((option) => (
              <button
                className={classNames(filter === option.id && "is-active")}
                key={option.id}
                onClick={() => onFilterChange(option.id)}
                type="button"
              >
                {option.label}
                {option.id === "pending" ? ` (${pendingInviteCount})` : ""}
              </button>
            ))}
          </div>
          <InvitationList
            emptyMessage="No incoming invitations match this filter."
            invitations={incomingInvitations}
            isMutating={isMutating}
            kind="incoming"
            onInvitationAction={onInvitationAction}
            title="Incoming Invitations"
          />
          <InvitationList
            canManageRoster={canInvitePlayers}
            emptyMessage="No outgoing invitations match this filter."
            invitations={outgoingInvitations}
            isMutating={isMutating}
            kind="outgoing"
            onInvitationAction={onInvitationAction}
            title="Outgoing Invitations"
          />
          {canManageTeam ? (
            <TeamJoinRequestList
              canManageTeam={canManageTeam}
              isMutating={isMutating}
              onJoinRequestAction={onJoinRequestAction}
              requests={teamJoinRequests}
            />
          ) : null}
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canDisbandTeam={false}
            canInvitePlayers={canInvitePlayers}
            canLeaveTeam={false}
            canManageRoster={canManageRoster}
            canTransferOwnership={false}
            mode="invitations"
            onFocusInvite={onFocusInvite}
          />
          <section className="team-mgmt-note ops-panel">
            <p>
              Team owners can send roster invitations. Roster size is checked when registering for a
              tournament.
            </p>
          </section>
        </aside>
      </section>
    </>
  );
}

function TeamJoinRequestList({
  canManageTeam,
  isMutating,
  onJoinRequestAction,
  requests
}: {
  canManageTeam: boolean;
  isMutating: boolean;
  onJoinRequestAction: (action: "accept" | "decline", requestId: string) => void;
  requests: TeamJoinRequest[];
}) {
  return (
    <section className="team-mgmt-join-placeholder ops-panel">
      <h2>
        <UsersRound size={18} />
        Join Requests
      </h2>
      {requests.length === 0 ? (
        <p className="team-mgmt-muted">No membership requests are waiting for this team.</p>
      ) : (
        requests.map((request) => (
          <article key={request.id}>
            <span className="team-mgmt-mini-avatar">
              {initials(request.requesterDisplayName ?? "PL")}
            </span>
            <div>
              <strong>{request.requesterDisplayName ?? "Player request"}</strong>
              <p>{request.message ?? "No message was provided."}</p>
            </div>
            <span className={classNames("team-mgmt-status", `team-mgmt-status-${request.status}`)}>
              {request.status}
            </span>
            {request.status === "pending" ? (
              <>
                <button
                  disabled={isMutating || !canManageTeam}
                  onClick={() => onJoinRequestAction("accept", request.id)}
                  type="button"
                >
                  Accept
                </button>
                <button
                  disabled={isMutating || !canManageTeam}
                  onClick={() => onJoinRequestAction("decline", request.id)}
                  type="button"
                >
                  Decline
                </button>
              </>
            ) : null}
          </article>
        ))
      )}
    </section>
  );
}

function InvitationList({
  canManageRoster = true,
  emptyMessage,
  invitations,
  isMutating,
  kind,
  onInvitationAction,
  title
}: {
  canManageRoster?: boolean;
  emptyMessage: string;
  invitations: TeamInvitation[];
  isMutating: boolean;
  kind: "incoming" | "outgoing";
  onInvitationAction: (action: "accept" | "decline" | "cancel", invitationId: string) => void;
  title: string;
}) {
  return (
    <section className="team-mgmt-invite-list">
      <h2>
        <Mail size={16} />
        {title}
      </h2>
      {invitations.length === 0 ? (
        <div className="team-mgmt-invite-empty ops-panel">{emptyMessage}</div>
      ) : (
        invitations.map((invitation) => (
          <article className="team-mgmt-invite-card ops-panel" key={invitation.id}>
            <span className="team-mgmt-mini-avatar">
              {initials(invitation.inviteeNickname || invitation.inviteeEmail || invitation.teamName || "TM")}
            </span>
            <div>
              <strong>{kind === "incoming" ? invitation.teamName ?? "Team invitation" : invitation.inviteeNickname || invitation.inviteeEmail || "Pending player"}</strong>
              <p>
                {kind === "incoming"
                  ? `Team owner: ${invitation.inviterNickname ?? "Unknown"} - Role: ${roleLabel(invitation.proposedRole)}`
                  : `Role: ${roleLabel(invitation.proposedRole)} - Sent: ${formatRelative(invitation.createdAt)}`}
              </p>
            </div>
            <span className={classNames("team-mgmt-status", statusClass(invitation.status))}>{invitation.status}</span>
            {kind === "incoming" && invitation.status === "pending" ? (
              <>
                <button disabled={isMutating} onClick={() => onInvitationAction("accept", invitation.id)} type="button">
                  Accept
                </button>
                <button disabled={isMutating} onClick={() => onInvitationAction("decline", invitation.id)} type="button">
                  Decline
                </button>
              </>
            ) : null}
            {kind === "outgoing" && invitation.status === "pending" ? (
              <button
                className="team-mgmt-cancel"
                disabled={isMutating || !canManageRoster}
                onClick={() => onInvitationAction("cancel", invitation.id)}
                type="button"
              >
                Cancel Invite
              </button>
            ) : null}
          </article>
        ))
      )}
    </section>
  );
}
