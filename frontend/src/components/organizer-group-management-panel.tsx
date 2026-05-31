"use client";

import { AlertTriangle, GitBranch, ListOrdered, Plus, RefreshCw, ShieldCheck, Trash2, UsersRound } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import { GroupsStandingsPanel } from "@/components/groups-standings-panel";
import { LiveSyncIndicator } from "@/components/live-sync-indicator";
import { ApiRequestError, type ApiFieldError } from "@/lib/api";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import type { OrganizerMatch } from "@/lib/organizer-match-data";
import type { OrganizerTournament } from "@/lib/organizer-tournament-data";
import {
  addTeamToOrganizerGroup,
  createOrganizerTournamentGroup,
  getOrganizerGroupsData,
  removeTeamFromOrganizerGroup,
  type OrganizerGroupsData,
  type TournamentGroup
} from "@/lib/tournament-group-data";
import type { TournamentRegistration } from "@/lib/tournament-registration-data";
import { classNames } from "@/lib/utils";

interface OrganizerGroupManagementPanelProps {
  matches: OrganizerMatch[];
  registrations: TournamentRegistration[];
  tournament: OrganizerTournament;
}

interface PanelErrorState {
  errors: ApiFieldError[];
  message: string;
  status: number | null;
}

function panelError(error: unknown, fallback: string): PanelErrorState {
  if (error instanceof ApiRequestError) {
    return {
      errors: error.errors,
      message: error.message || fallback,
      status: error.status
    };
  }

  return {
    errors: [],
    message: error instanceof Error ? error.message : fallback,
    status: null
  };
}

function optionalNumber(value: string) {
  const trimmed = value.trim();

  if (!trimmed) {
    return null;
  }

  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function standingsStatus(data: OrganizerGroupsData | null) {
  if (!data) {
    return "Loading";
  }

  if (data.standingsError) {
    return "Private/TODO";
  }

  return data.standings.length > 0 ? "Synced" : "Empty";
}

export function OrganizerGroupManagementPanel({
  matches,
  registrations,
  tournament
}: OrganizerGroupManagementPanelProps) {
  const [data, setData] = useState<OrganizerGroupsData | null>(null);
  const [error, setError] = useState<PanelErrorState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [groupName, setGroupName] = useState("");
  const [groupOrder, setGroupOrder] = useState("");
  const [selectedGroupId, setSelectedGroupId] = useState("");
  const [selectedTeamId, setSelectedTeamId] = useState("");
  const [seedNumber, setSeedNumber] = useState("");

  const loadGroups = useCallback(async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setIsLoading(true);
    }
    setError(null);
    setFieldErrors([]);

    try {
      const nextData = await getOrganizerGroupsData(tournament.id);
      setData(nextData);

      setSelectedGroupId((currentGroupId) => currentGroupId || nextData.groups[0]?.id || "");
    } catch (loadError) {
      if (!options?.silent) {
        setData(null);
      }
      setError(panelError(loadError, "Group management API is unavailable."));
      throw loadError;
    } finally {
      if (!options?.silent) {
        setIsLoading(false);
      }
    }
  }, [tournament.id]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadGroups(), 0);

    return () => window.clearTimeout(timeout);
  }, [loadGroups]);

  const liveSync = useTournamentLiveRefresh({
    enabled: true,
    hiddenIntervalMs: 60_000,
    intervalMs: 15_000,
    label: "organizer groups",
    onRefresh: () => loadGroups({ silent: true })
  });

  const assignedTeamIds = useMemo(() => {
    const ids = new Set<string>();
    data?.groups.forEach((group) => {
      group.teams.forEach((team) => ids.add(team.teamId));
    });
    return ids;
  }, [data?.groups]);

  const approvedUnassignedRegistrations = useMemo(
    () => registrations.filter((registration) =>
      registration.status === "approved" && !assignedTeamIds.has(registration.teamId)
    ),
    [assignedTeamIds, registrations]
  );

  const effectiveSelectedTeamId = approvedUnassignedRegistrations.some(
    (registration) => registration.teamId === selectedTeamId
  )
    ? selectedTeamId
    : approvedUnassignedRegistrations[0]?.teamId ?? "";

  const assignedTeams = data?.groups.reduce((total, group) => total + group.teams.length, 0) ?? 0;
  const finishedMatches = matches.filter((match) => match.status === "finished").length;

  async function createGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      await createOrganizerTournamentGroup(tournament.id, {
        name: groupName,
        sortOrder: optionalNumber(groupOrder)
      });
      setGroupName("");
      setGroupOrder("");
      setNotice("Group created from backend API.");
      await loadGroups();
    } catch (createError) {
      const nextError = panelError(createError, "Group could not be created.");
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  async function addTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedGroupId || !effectiveSelectedTeamId) {
      setError({
        errors: [],
        message: "Select a group and an approved unassigned team before adding a team.",
        status: null
      });
      return;
    }

    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      await addTeamToOrganizerGroup(selectedGroupId, {
        seedNumber: optionalNumber(seedNumber),
        teamId: effectiveSelectedTeamId
      });
      setSeedNumber("");
      setNotice("Team assignment saved through backend API.");
      await loadGroups();
    } catch (addError) {
      const nextError = panelError(addError, "Team could not be added to the selected group.");
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  async function removeTeam(group: TournamentGroup, teamId: string) {
    setIsMutating(true);
    setError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      await removeTeamFromOrganizerGroup(group.id, teamId);
      setNotice("Team removed from group through backend API.");
      await loadGroups();
    } catch (removeError) {
      const nextError = panelError(removeError, "Team could not be removed from this group.");
      setError(nextError);
      setFieldErrors(nextError.errors);
    } finally {
      setIsMutating(false);
    }
  }

  return (
    <section className="org-tournament-panel org-groups-panel ops-panel" id="group-management">
      <div className="org-tournament-panel-title">
        <div>
          <p className="ops-label">Group Management / Standings Control</p>
          <h2>Group Management</h2>
          <span>Assign approved teams to groups and monitor backend-calculated standings.</span>
        </div>
        <button className="org-tournament-secondary" disabled={isLoading || isMutating} onClick={() => void loadGroups()} type="button">
          <RefreshCw size={15} />
          Refresh Groups
        </button>
      </div>

      <LiveSyncIndicator
        errorCount={liveSync.errorCount}
        lastError={liveSync.lastError}
        lastUpdated={liveSync.lastUpdated}
        onRefresh={liveSync.refreshNow}
        status={liveSync.status}
      />

      {notice ? <p className="org-groups-notice">{notice}</p> : null}
      {error ? <GroupPanelError error={error} /> : null}
      {fieldErrors.length > 0 ? (
        <div className="org-groups-validation">
          {fieldErrors.map((fieldError, index) => (
            <span key={`${fieldError.field ?? "field"}-${index}`}>
              {fieldError.field ? `${fieldError.field}: ` : null}
              {fieldError.message ?? "Validation error"}
            </span>
          ))}
        </div>
      ) : null}

      <div className="org-groups-layout">
        <div className="org-groups-main">
          {isLoading ? (
            <div className="org-groups-state">
              <h3>Loading group assignments...</h3>
              <p>Connecting to organizer group endpoints.</p>
            </div>
          ) : !data || data.groups.length === 0 ? (
            <div className="org-groups-state">
              <h3>No groups configured yet.</h3>
              <p>Create the first group when approved teams are ready for group-stage assignment.</p>
            </div>
          ) : (
            <div className="org-groups-board">
              {data.groups.map((group) => (
                <article className="org-group-card ops-card" key={group.id}>
                  <div className="org-group-card-head">
                    <div>
                      <span className="ops-label">Group {group.sortOrder || "-"}</span>
                      <h3>{group.name}</h3>
                    </div>
                    <span className="ops-badge">{group.teams.length} teams</span>
                  </div>
                  <div className="org-group-team-stack">
                    {group.teams.length === 0 ? (
                      <p className="org-tournament-muted">No approved teams assigned.</p>
                    ) : (
                      group.teams.map((team) => (
                        <div className="org-group-team-row" key={team.teamId}>
                          <div>
                            <strong>{team.teamName}</strong>
                            <span>
                              {team.seedNumber ? `Seed #${team.seedNumber}` : "No seed"} / {team.teamTag ?? team.teamSlug ?? "No tag"}
                            </span>
                          </div>
                          <button disabled={isMutating} onClick={() => void removeTeam(group, team.teamId)} type="button">
                            <Trash2 size={14} />
                            Remove
                          </button>
                        </div>
                      ))
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}

          <GroupsStandingsPanel
            error={data?.standingsError ?? null}
            groups={data?.groups ?? []}
            isLoading={isLoading}
            mode="organizer"
            standings={data?.standings ?? []}
          />
        </div>

        <aside className="org-groups-command-rail">
          <form className="org-groups-command-card ops-card" onSubmit={createGroup}>
            <div>
              <p className="ops-label">Create Group</p>
              <h3>New Group</h3>
            </div>
            <label>
              <span>Group Name</span>
              <input disabled={isMutating} onChange={(event) => setGroupName(event.target.value)} placeholder="Group A" value={groupName} />
            </label>
            <label>
              <span>Sort Order</span>
              <input disabled={isMutating} min={1} onChange={(event) => setGroupOrder(event.target.value)} placeholder="1" type="number" value={groupOrder} />
            </label>
            <button className="org-tournament-primary" disabled={isMutating || !groupName.trim()} type="submit">
              <Plus size={15} />
              Create Group
            </button>
          </form>

          <form className="org-groups-command-card ops-card" onSubmit={addTeam}>
            <div>
              <p className="ops-label">Add Team</p>
              <h3>Approved Assignment</h3>
            </div>
            <label>
              <span>Target Group</span>
              <select disabled={isMutating || !data?.groups.length} onChange={(event) => setSelectedGroupId(event.target.value)} value={selectedGroupId}>
                {(data?.groups ?? []).map((group) => (
                  <option key={group.id} value={group.id}>{group.name}</option>
                ))}
              </select>
            </label>
            <label>
              <span>Approved Team</span>
              <select disabled={isMutating || approvedUnassignedRegistrations.length === 0} onChange={(event) => setSelectedTeamId(event.target.value)} value={effectiveSelectedTeamId}>
                {approvedUnassignedRegistrations.length === 0 ? (
                  <option value="">No approved unassigned teams available</option>
                ) : (
                  approvedUnassignedRegistrations.map((registration) => (
                    <option key={registration.teamId} value={registration.teamId}>
                      {registration.teamName}
                    </option>
                  ))
                )}
              </select>
            </label>
            <label>
              <span>Seed Number</span>
              <input disabled={isMutating} min={1} onChange={(event) => setSeedNumber(event.target.value)} placeholder="Optional" type="number" value={seedNumber} />
            </label>
            <button className="org-tournament-primary" disabled={isMutating || !selectedGroupId || !effectiveSelectedTeamId} type="submit">
              Add Team
            </button>
          </form>
        </aside>
      </div>

      <div className="org-groups-summary">
        <SummaryCard icon={GitBranch} label="Groups" value={String(data?.groups.length ?? 0)} tone="red" />
        <SummaryCard icon={UsersRound} label="Assigned Teams" value={String(assignedTeams)} tone="cyan" />
        <SummaryCard icon={ShieldCheck} label="Approved Unassigned Teams" value={String(approvedUnassignedRegistrations.length)} tone="gold" />
        <SummaryCard icon={ListOrdered} label="Finished Matches" value={String(finishedMatches)} tone="green" />
        <SummaryCard icon={ShieldCheck} label="Standings Status" value={standingsStatus(data)} tone={data?.standingsError ? "red" : "cyan"} />
      </div>
    </section>
  );
}

function SummaryCard({
  icon: Icon,
  label,
  tone,
  value
}: {
  icon: LucideIcon;
  label: string;
  tone: "cyan" | "gold" | "green" | "red";
  value: string;
}) {
  return (
    <article className={classNames("org-groups-summary-card ops-card", `is-${tone}`)}>
      <Icon size={18} />
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function GroupPanelError({ error }: { error: PanelErrorState }) {
  const title = error.status === 403
    ? "Permission denied"
    : error.status === 404
      ? "Group data not found"
      : "Group operation failed";

  return (
    <div className="org-groups-error">
      <AlertTriangle size={17} />
      <div>
        <strong>{title}</strong>
        <span>{error.message}</span>
      </div>
    </div>
  );
}
