"use client";

import {
  AlertTriangle,
  BarChart3,
  Check,
  ChevronRight,
  Clock,
  FileClock,
  Lock,
  Mail,
  MoreVertical,
  PlayCircle,
  RefreshCcw,
  Save,
  Shield,
  Swords,
  TerminalSquare,
  Trash2,
  UserMinus,
  UserPlus,
  UsersRound,
  X
} from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { RefObject } from "react";
import type { LucideIcon } from "lucide-react";

import { RouteLoadingSkeleton } from "@/components/route-loading-skeleton";
import {
  createTeam,
  acceptTeamInvitation,
  cancelTeamInvitation,
  declineTeamInvitation,
  loadTeamManagementData,
  removeTeamMember,
  sendTeamInvitation,
  updateTeamMemberRole,
  type TeamInvitation,
  type TeamInvitationStatus,
  type TeamManagementViewModel,
  type TeamMember,
  type TeamMemberRole,
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

const recentForm: Array<"W" | "L"> = ["W", "W", "L", "W", "W"];
const emptyMembers: TeamMember[] = [];
const emptyInvitations: TeamInvitation[] = [];
const emptyEvents: TeamManagementViewModel["activeEvents"] = [];

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

function memberOnline(index: number) {
  return index !== 2;
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
  incomingInvitations,
  isMutating,
  message,
  onInvitationAction,
  onCreateTeam,
  onRefresh
}: {
  incomingInvitations: TeamInvitation[];
  isMutating: boolean;
  message: string | null;
  onInvitationAction: (action: "accept" | "decline", invitationId: string) => void;
  onCreateTeam: (input: CreateTeamInput) => Promise<void>;
  onRefresh: () => void;
}) {
  const [showCreateForm, setShowCreateForm] = useState(false);
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

  return (
    <section className="team-mgmt-state team-mgmt-empty-state ops-panel">
      <div className="team-mgmt-empty-copy">
        <div className="team-mgmt-empty-icon" aria-hidden="true">
          <UsersRound size={32} />
        </div>
        <p className="ops-label">No active squad</p>
        <h1>You are not currently in a team</h1>
        <p>
          Create your own team or accept an invitation from a captain. Once your squad is ready,
          you can manage roster slots and register for tournaments.
        </p>
        <div className="team-mgmt-empty-actions">
          <button
            className="button button-primary ops-button-primary team-mgmt-empty-primary"
            disabled={isMutating}
            onClick={() => setShowCreateForm((current) => !current)}
            type="button"
          >
            Create Team
          </button>
          <button className="button button-secondary team-mgmt-empty-utility" onClick={onRefresh} type="button">
            <RefreshCcw size={16} />
            Retry
          </button>
        </div>
        <div className="team-mgmt-empty-invite-note" aria-label="Join by invitation">
          <span className="team-mgmt-empty-invite-label">Join by invite</span>
          <span>Ask a team captain to send you an invitation.</span>
        </div>
      </div>

      <div className="team-mgmt-empty-briefing" aria-label="Team onboarding steps">
        <article>
          <Shield size={18} />
          <div>
            <strong>Create a roster</strong>
            <span>Start a team workspace and become its captain.</span>
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

      {message ? <p className="team-mgmt-message team-mgmt-error">{message}</p> : null}
      {formError ? <p className="team-mgmt-message team-mgmt-error">{formError}</p> : null}
      {showCreateForm ? (
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
      {incomingInvitations.length > 0 ? (
        <div className="team-mgmt-empty-list">
          <span className="ops-label">Incoming invitations</span>
          {incomingInvitations.map((invitation) => (
            <article key={invitation.id}>
              <strong>{invitation.teamName ?? "Team invitation"}</strong>
              <span>{invitation.status}</span>
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
  const [inviteMessage, setInviteMessage] = useState("");
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
  const outgoingInvitations = viewModel?.outgoingInvitations ?? emptyInvitations;
  const incomingInvitations = viewModel?.incomingInvitations ?? emptyInvitations;
  const allInvitations = [...incomingInvitations, ...outgoingInvitations];
  const pendingInvites = allInvitations.filter((invitation) => invitation.status === "pending");
  const acceptedThisWeek = allInvitations.filter((invitation) => invitation.status === "accepted").length;
  const activeEvents = viewModel?.activeEvents ?? emptyEvents;
  const canManageRoster = Boolean(viewModel?.canManageRoster);
  const rosterFilled = Math.min(members.filter((member) => member.active).length, 5);
  const openRoles = Math.max(0, 5 - rosterFilled);
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

    if (!canManageRoster) {
      setPlaceholder("Only the team captain can send roster invitations.");
      return;
    }

    if (viewModel?.dataSource !== "api") {
      setPlaceholder("Invite form is ready. Backend API is not connected, so this invite was not persisted.");
      return;
    }

    setIsMutating(true);
    setError(null);
    setNotice(null);
    try {
      await sendTeamInvitation(team.id, { invitee, proposedRole: inviteRole });
      setInvitee("");
      setInviteMessage("");
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
      setPlaceholder("Only the team captain can save roster changes.");
      return;
    }

    if (viewModel?.dataSource !== "api") {
      setPlaceholder("Roster editing is ready. Backend API is not connected, so no roster changes were persisted.");
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
      setPlaceholder("Only the team captain can remove roster members.");
      return;
    }

    if (viewModel?.dataSource !== "api") {
      setPlaceholder("Remove member is ready, but backend API is not connected.");
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

  async function createNewTeam(input: CreateTeamInput) {
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

  if (!team) {
    return (
      <NoTeamState
        incomingInvitations={incomingInvitations}
        isMutating={isMutating}
        message={error}
        onCreateTeam={createNewTeam}
        onInvitationAction={(action, invitationId) => invitationAction(action, invitationId)}
        onRefresh={load}
      />
    );
  }

  return (
    <div className="team-mgmt-page">
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

      {notice ? <p className="team-mgmt-message team-mgmt-notice">{notice}</p> : null}
      {error ? <p className="team-mgmt-message team-mgmt-error">{error}</p> : null}

      {activeTab === "overview" ? (
        <OverviewTab
          activeEvents={activeEvents}
          canManageRoster={canManageRoster}
          members={members}
          onFocusInvite={focusInviteForm}
          onPlaceholder={setPlaceholder}
          onRosterTab={() => setActiveTab("roster")}
          outgoingInvitations={outgoingInvitations}
          pendingInviteCount={pendingInvites.length}
          rosterFilled={rosterFilled}
          team={team}
        />
      ) : null}

      {activeTab === "roster" ? (
        <RosterTab
          canManageRoster={canManageRoster}
          inviteMessage={inviteMessage}
          inviteRole={inviteRole}
          invitee={invitee}
          isMutating={isMutating}
          members={members}
          onInviteMessageChange={setInviteMessage}
          onInviteRoleChange={setInviteRole}
          onInviteeChange={setInvitee}
          onPlaceholder={setPlaceholder}
          onRemoveMember={removeMember}
          onRoleDraftChange={(memberId, role) => setRoleDrafts((drafts) => ({ ...drafts, [memberId]: role }))}
          onSaveRoster={saveRosterChanges}
          onSendInvite={sendInvite}
          openRoles={openRoles}
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
          canManageRoster={canManageRoster}
          filter={filter}
          incomingInvitations={filteredIncoming}
          incomingTotal={incomingInvitations.length}
          isMutating={isMutating}
          members={members}
          onFilterChange={setFilter}
          onFocusInvite={focusInviteForm}
          onInvitationAction={invitationAction}
          onPlaceholder={setPlaceholder}
          outgoingInvitations={filteredOutgoing}
          outgoingTotal={outgoingInvitations.length}
          pendingInviteCount={pendingInvites.length}
          team={team}
        />
      ) : null}
    </div>
  );
}

function TeamHero({
  compact = false,
  members,
  onPlaceholder,
  team
}: {
  compact?: boolean;
  members: TeamMember[];
  onPlaceholder?: (message: string) => void;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <section className={classNames("team-mgmt-hero ops-panel", compact && "team-mgmt-hero-compact")}>
      <div className="team-mgmt-team-mark">
        <Shield size={compact ? 30 : 42} />
      </div>
      <div className="team-mgmt-hero-body">
        <div className="team-mgmt-hero-copy">
          <div className="team-mgmt-hero-tags">
            <span>{team.region ?? "EU West"}</span>
            <span>Tag: {team.tag ?? "TL"}</span>
          </div>
          <h1>
            {team.name}
            {team.tag ? ` (${team.tag})` : ""}
          </h1>
          {compact ? (
            <p>
              Captain: <strong>{team.captainNickname ?? "Unassigned"}</strong> - Status:{" "}
              <strong>Active</strong> - Roster: <strong>{Math.min(members.length, 5)} / 5</strong>
            </p>
          ) : (
            <p>{team.description ?? "Elite European organization dominating the global circuit."}</p>
          )}
        </div>
        {!compact ? (
          <div className="team-mgmt-hero-meta-row">
            <div>
              <span>Captain</span>
              <strong>{team.captainNickname ?? "Unassigned"}</strong>
            </div>
            <div>
              <span>Status</span>
              <strong className="team-mgmt-hero-status">Active</strong>
            </div>
            <div>
              <span>Roster</span>
              <strong>{Math.min(members.length, 5)} / 5</strong>
            </div>
          </div>
        ) : null}
      </div>
      <div className="team-mgmt-form-strip">
        <span>Recent performance</span>
        <div>
          {recentForm.map((result, index) => (
            <strong className={classNames(result === "W" ? "is-win" : "is-loss")} key={`${result}-${index}`}>
              {result}
            </strong>
          ))}
        </div>
        {!compact ? (
          <button
            className="team-mgmt-hero-action"
            onClick={() => onPlaceholder?.("Pre-match briefing workflow is not available yet.")}
            type="button"
          >
            <PlayCircle size={18} />
            Pre-Match Briefing
          </button>
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

function CommandCenter({
  canManageRoster,
  mode,
  onFocusInvite,
  onPlaceholder,
  onRosterTab,
  onSaveRoster,
  onSendInvite,
  isMutating
}: {
  canManageRoster: boolean;
  isMutating?: boolean;
  mode: "overview" | "roster" | "invitations";
  onFocusInvite?: () => void;
  onPlaceholder: (message: string) => void;
  onRosterTab?: () => void;
  onSaveRoster?: () => void;
  onSendInvite?: () => void;
}) {
  if (mode === "roster") {
    return (
      <aside className="team-mgmt-side-card ops-panel">
        <h2>Command Center</h2>
        <button className="team-mgmt-command-primary" disabled={isMutating || !canManageRoster} onClick={onSaveRoster} type="button">
          <Save size={18} />
          Save Roster Changes
        </button>
        <button disabled={isMutating || !canManageRoster} onClick={onSendInvite} type="button">
          <Mail size={18} />
          Send Invite
        </button>
        <button disabled={!canManageRoster} onClick={() => onPlaceholder("Roster lock endpoint is not available yet.")} type="button">
          <Lock size={18} />
          Lock Roster
        </button>
        <hr />
        <button disabled={!canManageRoster} onClick={() => onPlaceholder("Transfer captaincy endpoint is not available yet.")} type="button">
          <RefreshCcw size={18} />
          Transfer Captaincy
        </button>
        <button disabled={!canManageRoster} onClick={() => onPlaceholder("Leave team endpoint is not available yet.")} type="button">
          <UserMinus size={18} />
          Leave Team
        </button>
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
        <button className="team-mgmt-command-primary" disabled={!canManageRoster} onClick={onFocusInvite} type="button">
          New Invite
          <UserPlus size={18} />
        </button>
        <button onClick={() => onPlaceholder("Pending invitations are listed in the main panel.")} type="button">
          Review Pending
        </button>
        <button onClick={() => onPlaceholder("Invite history endpoint is not available yet.")} type="button">
          View Invite History
        </button>
        <button disabled={!canManageRoster} onClick={() => onPlaceholder("Clear declined endpoint is not available yet.")} type="button">
          Clear Declined
        </button>
      </aside>
    );
  }

  return (
    <aside className="team-mgmt-side-card ops-panel">
      <h2>
        <Shield size={18} />
        Command Center
      </h2>
      <button disabled={!canManageRoster} onClick={onRosterTab} type="button">
        Edit Roster
        <ChevronRight size={16} />
      </button>
      <button disabled={!canManageRoster} onClick={() => onPlaceholder("Transfer captaincy endpoint is not available yet.")} type="button">
        Transfer Captaincy
        <ChevronRight size={16} />
      </button>
      <button onClick={() => onPlaceholder("Audit logs endpoint is not available yet.")} type="button">
        Audit Logs
        <ChevronRight size={16} />
      </button>
      <hr />
      <button className="team-mgmt-danger" disabled={!canManageRoster} onClick={() => onPlaceholder("Disband organization endpoint is not available yet.")} type="button">
        Disband Organization
      </button>
    </aside>
  );
}

function OverviewTab({
  activeEvents,
  canManageRoster,
  members,
  onFocusInvite,
  onPlaceholder,
  onRosterTab,
  outgoingInvitations,
  pendingInviteCount,
  rosterFilled,
  team
}: {
  activeEvents: TeamManagementViewModel["activeEvents"];
  canManageRoster: boolean;
  members: TeamMember[];
  onFocusInvite: () => void;
  onPlaceholder: (message: string) => void;
  onRosterTab: () => void;
  outgoingInvitations: TeamInvitation[];
  pendingInviteCount: number;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero members={members} onPlaceholder={onPlaceholder} team={team} />
      <section className="team-mgmt-metrics">
        <MetricCard icon={UsersRound} label="Members" value={`${String(rosterFilled).padStart(2, "0")} / 05`} />
        <MetricCard icon={Mail} label="Pending Invites" tone="gold" value={String(pendingInviteCount).padStart(2, "0")} />
        <MetricCard icon={Swords} label="Active Events" tone="cyan" value={String(activeEvents.length).padStart(2, "0")} />
        <MetricCard icon={BarChart3} label="Win Rate" tone="red" value="68%" />
      </section>
      <section className="team-mgmt-layout">
        <main className="team-mgmt-main-stack">
          <div className="team-mgmt-section-heading">
            <h2>Active Roster</h2>
            <span>
              <i /> Online <i className="is-offline" /> Offline
            </span>
          </div>
          <div className="team-mgmt-roster-preview">
            {members.slice(0, 5).map((member, index) => (
              <article className="team-mgmt-roster-tile ops-panel" key={member.id}>
                <TeamAvatar member={member} />
                <div>
                  <strong>{member.displayName || member.nickname}</strong>
                  <p>{shortRole(member.role)}</p>
                </div>
                <span className={classNames("team-mgmt-online-dot", !memberOnline(index) && "is-offline")} />
                <div className="team-mgmt-tile-actions">
                  <button disabled onClick={() => onPlaceholder("Player profile route is not available yet.")} type="button">
                    Profile later
                  </button>
                  <button disabled onClick={() => onPlaceholder("Player stats route is not available yet.")} type="button">
                    Stats later
                  </button>
                </div>
              </article>
            ))}
            <button className="team-mgmt-invite-tile ops-panel" disabled={!canManageRoster} onClick={onFocusInvite} type="button">
              <UserPlus size={24} />
              Invite New Player
            </button>
          </div>
          <InviteRegistry invitations={outgoingInvitations} onPlaceholder={onPlaceholder} />
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canManageRoster={canManageRoster}
            mode="overview"
            onFocusInvite={onFocusInvite}
            onPlaceholder={onPlaceholder}
            onRosterTab={onRosterTab}
          />
          <JoinRequests />
          <TacticalAlert tone="red" />
        </aside>
      </section>
    </>
  );
}

function InviteRegistry({
  invitations,
  onPlaceholder
}: {
  invitations: TeamInvitation[];
  onPlaceholder: (message: string) => void;
}) {
  return (
    <section className="team-mgmt-registry ops-panel">
      <div className="team-mgmt-panel-title">
        <h2>Invite Registry</h2>
        <button onClick={() => onPlaceholder("Invite history endpoint is not available yet.")} type="button">
          View History
          <ChevronRight size={15} />
        </button>
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
              <button onClick={() => onPlaceholder("Invite row action endpoint is not available yet.")} type="button">
                {invitation.status === "declined" ? <RefreshCcw size={15} /> : <X size={15} />}
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function JoinRequests() {
  return (
    <section className="team-mgmt-side-card ops-panel">
      <h2>Join Requests</h2>
      <p className="team-mgmt-muted">
        Join requests are not available in this version. Add players by sending roster invitations.
      </p>
    </section>
  );
}

function TacticalAlert({ tone = "gold" }: { tone?: "red" | "gold" }) {
  return (
    <section className={classNames("team-mgmt-alert ops-panel", tone === "gold" && "team-mgmt-alert-gold")}>
      <h2>
        <AlertTriangle size={17} />
        Tactical Alert
      </h2>
      <strong>Roster Lock Imminent</strong>
      <p>DreamLeague roster locks in 04h 12m. Ensure all outgoing invitations are resolved before the deadline.</p>
    </section>
  );
}

function RosterTab({
  canManageRoster,
  inviteInputRef,
  inviteMessage,
  inviteRole,
  invitee,
  isMutating,
  members,
  onInviteMessageChange,
  onInviteRoleChange,
  onInviteeChange,
  onPlaceholder,
  onRemoveMember,
  onRoleDraftChange,
  onSaveRoster,
  onSendInvite,
  openRoles,
  outgoingInvitations,
  roleDrafts,
  rosterFilled,
  team
}: {
  canManageRoster: boolean;
  inviteInputRef: RefObject<HTMLInputElement | null>;
  inviteMessage: string;
  inviteRole: TeamMemberRole;
  invitee: string;
  isMutating: boolean;
  members: TeamMember[];
  onInviteMessageChange: (value: string) => void;
  onInviteRoleChange: (value: TeamMemberRole) => void;
  onInviteeChange: (value: string) => void;
  onPlaceholder: (message: string) => void;
  onRemoveMember: (memberId: string) => void;
  onRoleDraftChange: (memberId: string, role: TeamMemberRole) => void;
  onSaveRoster: () => void;
  onSendInvite: () => void;
  openRoles: number;
  outgoingInvitations: TeamInvitation[];
  roleDrafts: Record<string, TeamMemberRole>;
  rosterFilled: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <div className="team-mgmt-roster-header-grid">
        <TeamHero compact members={members} team={team} />
        <section className="team-mgmt-countdown ops-panel">
          <span>Roster Lock Inbound</span>
          <strong>04:12:38</strong>
          <p>Until qualifier S19 closure</p>
        </section>
      </div>
      <section className="team-mgmt-metrics">
        <MetricCard icon={UsersRound} label="Active Members" value={`${rosterFilled} / 5`} />
        <MetricCard icon={Mail} label="Pending Invites" tone="gold" value={String(outgoingInvitations.filter((invite) => invite.status === "pending").length).padStart(2, "0")} />
        <MetricCard icon={UserPlus} label="Open Roles" tone="red" value={String(openRoles).padStart(2, "0")} />
        <MetricCard icon={Shield} label="System Health" tone="green" value="Nominal" />
      </section>
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
            {members.map((member, index) => (
              <article className="team-mgmt-member-card ops-panel" key={member.id}>
                {team.captainProfileId === member.profileId ? <span className="team-mgmt-captain-badge">Captain</span> : null}
                <div className="team-mgmt-member-head">
                  <TeamAvatar member={member} />
                  <div>
                    <strong>{member.displayName || member.nickname}</strong>
                    <p>{shortRole(roleDrafts[member.id] ?? member.role)}</p>
                  </div>
                </div>
                <div className="team-mgmt-member-meta">
                  <span>Status</span>
                  <strong className={memberOnline(index) ? "is-online" : "is-offline"}>
                    {memberOnline(index) ? "Online" : "Offline"}
                  </strong>
                  <span>Joined</span>
                  <strong>{member.joinedAt ? new Date(member.joinedAt).toLocaleDateString("en", { month: "short", year: "numeric" }) : "Oct 2023"}</strong>
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
                  <button onClick={() => onPlaceholder("Player profile route is not available yet.")} type="button">
                    View Profile
                  </button>
                  <button disabled={!canManageRoster || isMutating} onClick={() => onRemoveMember(member.id)} type="button">
                    <Trash2 size={15} />
                  </button>
                  <button onClick={() => onPlaceholder("Additional roster menu actions are not available yet.")} type="button">
                    <MoreVertical size={15} />
                  </button>
                </div>
              </article>
            ))}
          </div>
          <RecruitmentTerminal
            canManageRoster={canManageRoster}
            inviteInputRef={inviteInputRef}
            inviteMessage={inviteMessage}
            inviteRole={inviteRole}
            invitee={invitee}
            isMutating={isMutating}
            onInviteMessageChange={onInviteMessageChange}
            onInviteRoleChange={onInviteRoleChange}
            onInviteeChange={onInviteeChange}
            onSendInvite={onSendInvite}
          />
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canManageRoster={canManageRoster}
            isMutating={isMutating}
            mode="roster"
            onPlaceholder={onPlaceholder}
            onSaveRoster={onSaveRoster}
            onSendInvite={onSendInvite}
          />
          <section className="team-mgmt-side-card ops-panel">
            <h2>Roster Activity</h2>
            <article className="team-mgmt-activity">
              <Check size={15} />
              <div>
                <strong>iNSaNiA joined the team</strong>
                <p>2 days ago</p>
              </div>
            </article>
            <article className="team-mgmt-activity">
              <FileClock size={15} />
              <div>
                <strong>Shotcall updated roles</strong>
                <p>5 days ago</p>
              </div>
            </article>
          </section>
        </aside>
      </section>
    </>
  );
}

function RecruitmentTerminal({
  canManageRoster,
  inviteInputRef,
  inviteMessage,
  inviteRole,
  invitee,
  isMutating,
  onInviteMessageChange,
  onInviteRoleChange,
  onInviteeChange,
  onSendInvite
}: {
  canManageRoster: boolean;
  inviteInputRef: RefObject<HTMLInputElement | null>;
  inviteMessage: string;
  inviteRole: TeamMemberRole;
  invitee: string;
  isMutating: boolean;
  onInviteMessageChange: (value: string) => void;
  onInviteRoleChange: (value: TeamMemberRole) => void;
  onInviteeChange: (value: string) => void;
  onSendInvite: () => void;
}) {
  return (
    <section className="team-mgmt-recruitment ops-panel">
      <h2>Recruitment Terminal</h2>
      <div className="team-mgmt-recruitment-grid">
        <label>
          <span>Player ID / Email</span>
          <input
            disabled={!canManageRoster || isMutating}
            onChange={(event) => onInviteeChange(event.target.value)}
            placeholder="UID-000000000 or player@email.com"
            ref={inviteInputRef}
            value={invitee}
          />
        </label>
        <label>
          <span>Invitation Message</span>
          <textarea
            disabled={!canManageRoster || isMutating}
            onChange={(event) => onInviteMessageChange(event.target.value)}
            placeholder="Briefly describe the role and terms..."
            value={inviteMessage}
          />
        </label>
        <label>
          <span>Intended Role</span>
          <select
            disabled={!canManageRoster || isMutating}
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
        <button disabled={!canManageRoster || isMutating} onClick={onSendInvite} type="button">
          {isMutating ? "Sending..." : "Send Invite"}
        </button>
      </div>
      <p>Tactical Note: Only team captains can edit roster roles and send invitations.</p>
    </section>
  );
}

function InvitationsTab({
  acceptedThisWeek,
  canManageRoster,
  filter,
  incomingInvitations,
  incomingTotal,
  isMutating,
  members,
  onFilterChange,
  onFocusInvite,
  onInvitationAction,
  onPlaceholder,
  outgoingInvitations,
  outgoingTotal,
  pendingInviteCount,
  team
}: {
  acceptedThisWeek: number;
  canManageRoster: boolean;
  filter: InviteFilter;
  incomingInvitations: TeamInvitation[];
  incomingTotal: number;
  isMutating: boolean;
  members: TeamMember[];
  onFilterChange: (filter: InviteFilter) => void;
  onFocusInvite: () => void;
  onInvitationAction: (action: "accept" | "decline" | "cancel", invitationId: string) => void;
  onPlaceholder: (message: string) => void;
  outgoingInvitations: TeamInvitation[];
  outgoingTotal: number;
  pendingInviteCount: number;
  team: NonNullable<TeamManagementViewModel["team"]>;
}) {
  return (
    <>
      <TeamHero compact members={members} team={team} />
      <section className="team-mgmt-metrics team-mgmt-invite-metrics">
        <MetricCard icon={Mail} label="Incoming Invites" value={String(incomingTotal).padStart(2, "0")} />
        <MetricCard icon={UserPlus} label="Outgoing Invites" value={String(outgoingTotal).padStart(2, "0")} />
        <MetricCard icon={Clock} label="Join Requests Unavailable" value="--" />
        <MetricCard icon={Check} label="Accepted This Week" tone="gold" value={String(acceptedThisWeek).padStart(2, "0")} />
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
            canManageRoster={canManageRoster}
            emptyMessage="No outgoing invitations match this filter."
            invitations={outgoingInvitations}
            isMutating={isMutating}
            kind="outgoing"
            onInvitationAction={onInvitationAction}
            title="Outgoing Invitations"
          />
          <section className="team-mgmt-join-placeholder ops-panel">
            <h2>Join Requests</h2>
            <p className="team-mgmt-muted">
              Join requests are not available in this version. Captains can manage roster access through invitations.
            </p>
          </section>
        </main>
        <aside className="team-mgmt-side-stack">
          <CommandCenter
            canManageRoster={canManageRoster}
            mode="invitations"
            onFocusInvite={onFocusInvite}
            onPlaceholder={onPlaceholder}
          />
          <section className="team-mgmt-note ops-panel">
            <p>
              Tactical note: all invitations are subject to regional roster lock regulations. Captains are
              responsible for slot management.
            </p>
          </section>
          <TacticalAlert />
        </aside>
      </section>
    </>
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
                  ? `Captain: ${invitation.inviterNickname ?? "Unknown"} - Role: ${roleLabel(invitation.proposedRole)}`
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
