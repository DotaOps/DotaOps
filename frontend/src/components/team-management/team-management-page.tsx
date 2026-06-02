"use client";

import {
  BarChart3,
  Check,
  ChevronRight,
  Lock,
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
  loadTeamManagementData,
  removeTeamMember,
  sendTeamInvitation,
  updateTeamMemberRole,
  type TeamInvitation,
  type TeamInvitationStatus,
  type TeamJoinRequest,
  type TeamManagementViewModel,
  type TeamManualPlayer,
  type TeamMember,
  type TeamMemberRole,
  type TeamSummary,
  type CreateTeamInput
} from "@/lib/team-data";
import { classNames } from "@/lib/utils";
import { ApiRequestError } from "@/lib/api";

type TeamTab = "overview" | "roster" | "invitations";
type InviteFilter = "all" | TeamInvitationStatus;

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
    team.captainProfileId &&
      members.some((member) => member.active && member.profileId === team.captainProfileId)
  );
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
  const canInvitePlayers = Boolean(viewModel?.canInvitePlayers);
  const canManageTeam = Boolean(viewModel?.canManageTeam);
  const canManageRoster = Boolean(viewModel?.canManageRoster);
  const canRequestTeamMembership = Boolean(viewModel?.canRequestTeamMembership);
  const isTournamentOperator =
    viewModel?.currentProfile.role === "organizer" || viewModel?.currentProfile.role === "admin";
  const outgoingJoinRequests = viewModel?.outgoingJoinRequests ?? [];
  const teamJoinRequests = viewModel?.teamJoinRequests ?? [];
  const rosterFilled = team ? rosterParticipantCount(team, members, manualPlayers) : 0;
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
          canInvitePlayers={canInvitePlayers}
          canManageRoster={canManageRoster}
          manualPlayers={manualPlayers}
          members={members}
          onFocusInvite={focusInviteForm}
          onRosterTab={() => setActiveTab("roster")}
          outgoingInvitations={outgoingInvitations}
          pendingInviteCount={pendingInvites.length}
          rosterFilled={rosterFilled}
          team={team}
        />
      ) : null}

      {activeTab === "roster" ? (
        <RosterTab
          canInvitePlayers={canInvitePlayers}
          canManageRoster={canManageRoster}
          inviteRole={inviteRole}
          invitee={invitee}
          isMutating={isMutating}
          members={members}
          manualPlayers={manualPlayers}
          onInviteRoleChange={setInviteRole}
          onInviteeChange={setInvitee}
          onRemoveMember={removeMember}
          onRoleDraftChange={(memberId, role) => setRoleDrafts((drafts) => ({ ...drafts, [memberId]: role }))}
          onSaveRoster={saveRosterChanges}
          onSendInvite={sendInvite}
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
          team={team}
          teamJoinRequests={teamJoinRequests}
        />
      ) : null}
    </div>
  );
}

function TeamHero({
  compact = false,
  manualPlayers,
  members,
  team
}: {
  compact?: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  const rosterCount = rosterParticipantCount(team, members, manualPlayers);

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

function PlannedControls() {
  return (
    <div className="team-mgmt-planned-controls">
      <span className="team-mgmt-planned-title">Planned team controls</span>
      <PlannedAction>
        <RefreshCcw size={16} />
        Transfer Ownership
      </PlannedAction>
      <PlannedAction>
        <Lock size={16} />
        Lock Roster
      </PlannedAction>
      <PlannedAction>
        <UserMinus size={16} />
        Leave Team
      </PlannedAction>
      <PlannedAction>
        <TerminalSquare size={16} />
        Audit Logs
      </PlannedAction>
      <PlannedAction>
        <Trash2 size={16} />
        Disband Team
      </PlannedAction>
    </div>
  );
}

function CommandCenter({
  canInvitePlayers,
  canManageRoster,
  mode,
  onFocusInvite,
  onRosterTab,
  onSaveRoster,
  onSendInvite,
  isMutating
}: {
  canInvitePlayers: boolean;
  canManageRoster: boolean;
  isMutating?: boolean;
  mode: "overview" | "roster" | "invitations";
  onFocusInvite?: () => void;
  onRosterTab?: () => void;
  onSaveRoster?: () => void;
  onSendInvite?: () => void;
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
        {canManageRoster ? <PlannedControls /> : null}
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
      {canManageRoster ? <PlannedControls /> : null}
    </aside>
  );
}

function OverviewTab({
  activeEvents,
  canInvitePlayers,
  canManageRoster,
  manualPlayers,
  members,
  onFocusInvite,
  onRosterTab,
  outgoingInvitations,
  pendingInviteCount,
  rosterFilled,
  team
}: {
  activeEvents: TeamManagementViewModel["activeEvents"];
  canInvitePlayers: boolean;
  canManageRoster: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  onFocusInvite: () => void;
  onRosterTab: () => void;
  outgoingInvitations: TeamInvitation[];
  pendingInviteCount: number;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero manualPlayers={manualPlayers} members={members} team={team} />
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
                {team.captainProfileId === member.profileId ? (
                  <span className="team-mgmt-captain-badge">Team owner</span>
                ) : null}
                <TeamAvatar member={member} />
                <div>
                  <strong>{member.displayName || member.nickname}</strong>
                  <p>{shortRole(member.role)}</p>
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
            canInvitePlayers={canInvitePlayers}
            canManageRoster={canManageRoster}
            mode="overview"
            onRosterTab={onRosterTab}
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
  canManageRoster,
  inviteInputRef,
  inviteRole,
  invitee,
  isMutating,
  manualPlayers,
  members,
  onInviteRoleChange,
  onInviteeChange,
  onRemoveMember,
  onRoleDraftChange,
  onSaveRoster,
  onSendInvite,
  outgoingInvitations,
  roleDrafts,
  rosterFilled,
  team
}: {
  canInvitePlayers: boolean;
  canManageRoster: boolean;
  inviteInputRef: RefObject<HTMLInputElement | null>;
  inviteRole: TeamMemberRole;
  invitee: string;
  isMutating: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  onInviteRoleChange: (value: TeamMemberRole) => void;
  onInviteeChange: (value: string) => void;
  onRemoveMember: (memberId: string) => void;
  onRoleDraftChange: (memberId: string, role: TeamMemberRole) => void;
  onSaveRoster: () => void;
  onSendInvite: () => void;
  outgoingInvitations: TeamInvitation[];
  roleDrafts: Record<string, TeamMemberRole>;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero compact manualPlayers={manualPlayers} members={members} team={team} />
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
                {team.captainProfileId === member.profileId ? <span className="team-mgmt-captain-badge">Team owner</span> : null}
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
                  {canManageRoster && team.captainProfileId !== member.profileId ? (
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
            canInvitePlayers={canInvitePlayers}
            canManageRoster={canManageRoster}
            isMutating={isMutating}
            mode="roster"
            onSaveRoster={onSaveRoster}
            onSendInvite={onSendInvite}
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
  team: NonNullable<TeamManagementViewModel["team"]>;
  teamJoinRequests: TeamJoinRequest[];
}) {
  return (
    <>
      <TeamHero compact manualPlayers={manualPlayers} members={members} team={team} />
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
            canInvitePlayers={canInvitePlayers}
            canManageRoster={canManageRoster}
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
