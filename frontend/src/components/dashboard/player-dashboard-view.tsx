import { Gamepad2 } from "lucide-react";
import Link from "next/link";

import {
  FormChips,
  PlayerAvatar,
  RoleActionButton,
  RoleEmptyState,
  RolePanel,
  StatusChip
} from "@/components/dashboard/role-dashboard-primitives";
import type {
  MeDashboardCapabilities,
  MeDashboardManualPlayer,
  MeDashboardTeamMember,
  MePlayerDashboard
} from "@/lib/me-dashboard-data";
import type { DashboardTone, RosterPlayer } from "@/lib/role-dashboard-data";
import { classNames } from "@/lib/utils";

function initials(value: string) {
  return value
    .split(/\s+/)
    .map((part) => part.charAt(0))
    .join("")
    .slice(0, 2)
    .toUpperCase() || "PL";
}

function rosterPlayer(member: MeDashboardTeamMember): RosterPlayer {
  const name = member.displayName || member.nickname;

  return {
    avatarCode: initials(name),
    hero: "",
    id: member.id,
    name,
    role: member.teamOwner ? "Team owner" : member.role
  };
}

function manualRosterPlayer(player: MeDashboardManualPlayer): RosterPlayer {
  return {
    avatarCode: initials(player.displayName),
    hero: "",
    id: player.id,
    name: player.displayName,
    role: "Manual roster slot"
  };
}

function roleLabel(capabilities: MeDashboardCapabilities) {
  if (capabilities.isTeamOwner) {
    return "Team owner";
  }

  return capabilities.currentUserTeamRole || "Player";
}

export function PlayerDashboardView({
  capabilities,
  dashboard
}: {
  capabilities: MeDashboardCapabilities;
  dashboard: MePlayerDashboard;
}) {
  const currentTeam = dashboard.currentTeam;
  const team = currentTeam.team;
  const roster = [
    ...currentTeam.members.filter((member) => member.active).map(rosterPlayer),
    ...currentTeam.manualPlayers.map(manualRosterPlayer)
  ];
  const teamRole = team ? roleLabel(capabilities) : "No active team";
  const teamActionLabel = !team && capabilities.canCreateTeam ? "Create Team" : "Open My Team";
  const summary: Array<{ detail: string; label: string; tone: DashboardTone; value: string }> = [
    {
      detail: currentTeam.capacity ? `${currentTeam.slotsRemaining} slots remaining` : "Backend roster count",
      label: "Roster Participants",
      tone: "cyan",
      value: String(currentTeam.participantsCount)
    },
    {
      detail: currentTeam.capacity ? `${currentTeam.slotsFilled}/${currentTeam.capacity} filled` : "No capacity configured",
      label: "Roster Capacity",
      tone: "gold",
      value: currentTeam.capacity ? String(currentTeam.capacity) : "N/A"
    },
    {
      detail: "Incoming captain invitations",
      label: "Pending Invitations",
      tone: "red",
      value: String(dashboard.pendingInvitations)
    },
    {
      detail: "Registrations for current team",
      label: "Tournament Registrations",
      tone: "green",
      value: String(dashboard.tournamentRegistrations)
    }
  ];

  return (
    <div className="role-dashboard role-dashboard-player">
      <div className="role-dashboard-content">
        <section className="role-hero role-player-hero">
          <div className="role-player-hero-copy">
            <div className="role-hero-label-row">
              <span className="role-hero-kicker">Player workspace</span>
              <StatusChip tone={capabilities.isTeamOwner ? "gold" : "cyan"}>{teamRole}</StatusChip>
            </div>
            <h1>{team?.name ?? "Player Hub"}</h1>
            <p>
              {team
                ? "Review your active roster, tournament registrations, and team workspace."
                : "Create a team or accept an invitation to unlock roster and tournament registration workflows."}
            </p>
            <div className="role-player-hero-actions">
              <Link className="role-action-button role-action-primary" href="/ekipe">
                <Gamepad2 size={18} />
                <span>{teamActionLabel}</span>
              </Link>
              <Link className="role-action-button role-action-secondary" href="/turnirji">
                Browse Tournaments
              </Link>
            </div>
          </div>

          <div className="role-hero-form role-player-form">
            <span>Recent Form</span>
            <FormChips values={[]} />
          </div>
        </section>

        <section className="role-player-grid">
          <div className="role-player-main">
            <div className="role-section-heading">
              <h2>Team Workspace Summary</h2>
              <span>Live backend data</span>
            </div>

            <div className="role-performance-grid">
              {summary.map((metric) => (
                <article
                  className={classNames("role-performance-card", `role-tone-${metric.tone}`, "is-featured")}
                  key={metric.label}
                >
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <p>{metric.detail}</p>
                </article>
              ))}
            </div>

            <RolePanel title="Personal Performance">
              <RoleEmptyState
                detail="Personal KDA, win rate, rank, and recent form are not included in the dashboard endpoint yet."
                title="Personal analytics unavailable."
              />
            </RolePanel>
          </div>

          <RolePanel title="Team Roster" className="role-player-roster-panel">
            <div className="role-player-roster">
              {roster.length === 0 ? (
                <RoleEmptyState
                  detail="Open My Team to create a roster or accept an invitation."
                  title="No team roster to show yet."
                />
              ) : roster.map((player) => (
                <article key={player.id}>
                  <PlayerAvatar player={player} />
                  <div>
                    <strong>{player.name}</strong>
                    <span>{player.role}</span>
                  </div>
                  <StatusChip tone={player.role === "Team owner" ? "gold" : "muted"}>
                    {player.role === "Team owner" ? "Owner" : "Member"}
                  </StatusChip>
                </article>
              ))}
            </div>
            <RoleActionButton
              action={{ href: "/ekipe", icon: Gamepad2, label: "Open My Team" }}
              variant="secondary"
            />
          </RolePanel>
        </section>

        <RolePanel title="Recent Match Log">
          <div className="role-table-wrap">
            <table className="role-data-table role-match-log-table">
              <thead>
                <tr>
                  <th>Hero</th>
                  <th>Result</th>
                  <th>Type</th>
                  <th>K / D / A</th>
                  <th>Duration</th>
                  <th>GPM/XPM</th>
                  <th>Analyzed</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td colSpan={7}>
                    <RoleEmptyState
                      detail="Imported player match analytics are not included in the dashboard endpoint yet."
                      title="No analyzed matches available."
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </RolePanel>
      </div>
    </div>
  );
}
