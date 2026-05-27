"use client";

import {
  deleteApiAuthenticated,
  fetchApi,
  getApi,
  getApiAuthenticated,
  patchApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";
import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import { getSupabaseBrowserClient } from "@/lib/supabase";

export type TeamMemberRole =
  | "carry"
  | "mid"
  | "offlane"
  | "support"
  | "roamer"
  | "coach"
  | "substitute";

export type TeamInvitationStatus =
  | "pending"
  | "accepted"
  | "declined"
  | "cancelled"
  | "expired";

export interface TeamSummary {
  captainNickname: string | null;
  captainProfileId: string | null;
  description: string | null;
  id: string;
  logoUrl: string | null;
  name: string;
  region: string | null;
  slug: string;
  tag: string | null;
}

export interface TeamMember {
  active: boolean;
  avatarUrl: string | null;
  displayName: string | null;
  id: string;
  joinedAt: string | null;
  leftAt: string | null;
  nickname: string;
  profileId: string;
  role: TeamMemberRole;
  teamId: string;
  updatedAt: string | null;
}

export interface TeamInvitation {
  acceptedAt: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  id: string;
  inviteeEmail: string | null;
  inviteeNickname: string | null;
  inviteeProfileId: string | null;
  inviterNickname: string | null;
  proposedRole: TeamMemberRole;
  status: TeamInvitationStatus;
  teamId: string;
  teamName: string | null;
  teamSlug: string | null;
  updatedAt: string | null;
}

export interface TournamentRegistration {
  checkedInAt: string | null;
  contactEmail: string | null;
  createdAt: string | null;
  id: string;
  status: string;
  teamId: string;
  tournamentId: string;
  tournamentSlug: string | null;
  tournamentTitle: string | null;
}

export interface TeamManagementViewModel {
  accessToken: string;
  activeEvents: TournamentRegistration[];
  canManageRoster: boolean;
  currentProfile: CurrentUserProfile;
  dataSource: "api" | "mock";
  incomingInvitations: TeamInvitation[];
  isCaptain: boolean;
  members: TeamMember[];
  outgoingInvitations: TeamInvitation[];
  protectedDataError: string | null;
  team: TeamSummary | null;
  teamResolution: string;
}

export interface TeamInvitationInput {
  invitee: string;
  proposedRole: TeamMemberRole;
}

export interface CreateTeamInput {
  description?: string;
  name: string;
  region?: string;
  slug?: string;
  tag?: string;
}

interface BackendTeamResponse {
  captainNickname?: string | null;
  captainProfileId?: string | null;
  description?: string | null;
  id: string;
  logoUrl?: string | null;
  name: string;
  region?: string | null;
  slug: string;
  tag?: string | null;
}

interface BackendTeamMemberResponse {
  active: boolean;
  avatarUrl?: string | null;
  displayName?: string | null;
  id: string;
  joinedAt?: string | null;
  leftAt?: string | null;
  nickname: string;
  profileId: string;
  role: TeamMemberRole;
  teamId: string;
  updatedAt?: string | null;
}

interface BackendCurrentTeamResponse {
  canManageRoster?: boolean;
  captain?: boolean;
  members?: BackendTeamMemberResponse[] | null;
  team?: BackendTeamResponse | null;
  teamResolution?: string | null;
}

interface BackendTeamInvitationResponse {
  acceptedAt?: string | null;
  createdAt?: string | null;
  expiresAt?: string | null;
  id: string;
  inviteeEmail?: string | null;
  inviteeNickname?: string | null;
  inviteeProfileId?: string | null;
  inviterNickname?: string | null;
  proposedRole?: TeamMemberRole | null;
  status: TeamInvitationStatus;
  teamId: string;
  teamName?: string | null;
  teamSlug?: string | null;
  updatedAt?: string | null;
}

interface BackendTournamentRegistrationResponse {
  checkedInAt?: string | null;
  contactEmail?: string | null;
  createdAt?: string | null;
  id: string;
  status: string;
  teamId: string;
  tournamentId: string;
  tournamentSlug?: string | null;
  tournamentTitle?: string | null;
}

interface BackendTeamPageResponse {
  content?: BackendTeamResponse[];
  data?: BackendTeamResponse[] | BackendTeamPageResponse | null;
  items?: BackendTeamResponse[];
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function requireSupabaseClient() {
  const supabase = getSupabaseBrowserClient();

  if (!supabase) {
    throw new Error("Supabase frontend environment variables are missing.");
  }

  return supabase;
}

async function getFreshAccessToken() {
  const supabase = requireSupabaseClient();
  const { data } = await supabase.auth.getSession();

  if (!data.session?.access_token) {
    throw new Error("Login session expired. Please log in again.");
  }

  return data.session.access_token;
}

function asTeam(response: BackendTeamResponse): TeamSummary {
  return {
    captainNickname: response.captainNickname ?? null,
    captainProfileId: response.captainProfileId ?? null,
    description: response.description ?? null,
    id: response.id,
    logoUrl: response.logoUrl ?? null,
    name: response.name,
    region: response.region ?? null,
    slug: response.slug,
    tag: response.tag ?? null
  };
}

function asMember(response: BackendTeamMemberResponse): TeamMember {
  return {
    active: response.active,
    avatarUrl: response.avatarUrl ?? null,
    displayName: response.displayName ?? null,
    id: response.id,
    joinedAt: response.joinedAt ?? null,
    leftAt: response.leftAt ?? null,
    nickname: response.nickname,
    profileId: response.profileId,
    role: response.role,
    teamId: response.teamId,
    updatedAt: response.updatedAt ?? null
  };
}

function asInvitation(response: BackendTeamInvitationResponse): TeamInvitation {
  return {
    acceptedAt: response.acceptedAt ?? null,
    createdAt: response.createdAt ?? null,
    expiresAt: response.expiresAt ?? null,
    id: response.id,
    inviteeEmail: response.inviteeEmail ?? null,
    inviteeNickname: response.inviteeNickname ?? null,
    inviteeProfileId: response.inviteeProfileId ?? null,
    inviterNickname: response.inviterNickname ?? null,
    proposedRole: response.proposedRole ?? "support",
    status: response.status,
    teamId: response.teamId,
    teamName: response.teamName ?? null,
    teamSlug: response.teamSlug ?? null,
    updatedAt: response.updatedAt ?? null
  };
}

function asRegistration(response: BackendTournamentRegistrationResponse): TournamentRegistration {
  return {
    checkedInAt: response.checkedInAt ?? null,
    contactEmail: response.contactEmail ?? null,
    createdAt: response.createdAt ?? null,
    id: response.id,
    status: response.status,
    teamId: response.teamId,
    tournamentId: response.tournamentId,
    tournamentSlug: response.tournamentSlug ?? null,
    tournamentTitle: response.tournamentTitle ?? null
  };
}

function isBackendTeam(value: unknown): value is BackendTeamResponse {
  return value !== null && typeof value === "object" && "id" in value && "name" in value && "slug" in value;
}

function extractTeamList(value: unknown): BackendTeamResponse[] | null {
  if (Array.isArray(value)) {
    return value.every(isBackendTeam) ? value : null;
  }

  if (!value || typeof value !== "object") {
    return null;
  }

  const payload = value as BackendTeamPageResponse;

  if (Array.isArray(payload.content)) {
    return payload.content.every(isBackendTeam) ? payload.content : null;
  }

  if (Array.isArray(payload.items)) {
    return payload.items.every(isBackendTeam) ? payload.items : null;
  }

  if (payload.data) {
    return extractTeamList(payload.data);
  }

  return null;
}

async function listTeams() {
  try {
    const payload = await getApi<unknown>("/teams");
    const teams = extractTeamList(payload);

    if (!teams) {
      throw new Error("Team list response has an unsupported shape.");
    }

    return { data: teams.map(asTeam), source: "api" as const };
  } catch {
    return { data: [] as TeamSummary[], source: "api" as const };
  }
}

function protectedErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Protected team data could not be loaded.";
}

export async function loadTeamManagementData(): Promise<TeamManagementViewModel | null> {
  const currentProfile = await getCurrentUserProfile();

  if (!currentProfile) {
    return null;
  }

  const accessToken = await getFreshAccessToken();
  let protectedDataError: string | null = null;
  let currentTeamAggregate: BackendCurrentTeamResponse | null = null;

  try {
    currentTeamAggregate = await getApiAuthenticated<BackendCurrentTeamResponse>("/me/team", accessToken);
  } catch (error) {
    throw new Error(protectedErrorMessage(error));
  }

  const teamsResult = await listTeams();
  let dataSource: "api" | "mock" = teamsResult.source;
  let teams = teamsResult.data;
  let membersByTeam = new Map<string, TeamMember[]>();
  const aggregateTeam = currentTeamAggregate?.team ? asTeam(currentTeamAggregate.team) : null;

  if (teams.length > 0) {
    const memberPairs = await Promise.all(
      teams.map(async (team) => {
        const result = await fetchApi<BackendTeamMemberResponse[]>(`/teams/${team.id}/members`, []);
        return [team.id, result.data.map(asMember)] as const;
      })
    );
    membersByTeam = new Map(memberPairs);
  }

  if (aggregateTeam) {
    dataSource = "api";
    teams = [aggregateTeam, ...teams.filter((team) => team.id !== aggregateTeam.id)];
    membersByTeam.set(
      aggregateTeam.id,
      (currentTeamAggregate?.members ?? []).map(asMember)
    );
  }

  const membershipTeam =
    !aggregateTeam && currentProfile.profileId
      ? teams.find((team) =>
          (membersByTeam.get(team.id) ?? []).some((member) => member.profileId === currentProfile.profileId)
        )
      : null;
  const selectedTeam = aggregateTeam ?? membershipTeam ?? null;
  const members = selectedTeam ? membersByTeam.get(selectedTeam.id) ?? [] : [];
  const isCaptain = aggregateTeam
    ? Boolean(currentTeamAggregate?.captain)
    : Boolean(
        selectedTeam?.captainProfileId &&
          currentProfile.profileId &&
          selectedTeam.captainProfileId === currentProfile.profileId
      );
  const canManageRoster = aggregateTeam ? Boolean(currentTeamAggregate?.canManageRoster) : isCaptain;
  const teamResolution = aggregateTeam
    ? currentTeamAggregate?.teamResolution ?? "Resolved from GET /api/me/team."
    : membershipTeam
      ? "Resolved from current profile membership."
      : currentTeamAggregate
        ? currentTeamAggregate.teamResolution ?? "No team found for the current profile."
        : "No team found for the current profile.";
  let outgoingInvitations: TeamInvitation[] = [];
  let incomingInvitations: TeamInvitation[] = [];
  let activeEvents: TournamentRegistration[] = [];

  try {
    incomingInvitations = (
      await getApiAuthenticated<BackendTeamInvitationResponse[]>("/me/team-invitations", accessToken)
    ).map(asInvitation);
  } catch (error) {
    protectedDataError = protectedErrorMessage(error);
  }

  if (selectedTeam && canManageRoster && dataSource === "api") {
    try {
      outgoingInvitations = (
        await getApiAuthenticated<BackendTeamInvitationResponse[]>(
          `/teams/${selectedTeam.id}/invitations`,
          accessToken
        )
      ).map(asInvitation);
    } catch (error) {
      protectedDataError = protectedDataError ?? protectedErrorMessage(error);
    }
  }

  if (selectedTeam && dataSource === "api") {
    try {
      activeEvents = (
        await getApiAuthenticated<BackendTournamentRegistrationResponse[]>(
          `/teams/${selectedTeam.id}/tournament-registrations`,
          accessToken
        )
      ).map(asRegistration);
    } catch (error) {
      protectedDataError = protectedDataError ?? protectedErrorMessage(error);
    }
  }

  return {
    accessToken,
    activeEvents,
    canManageRoster,
    currentProfile,
    dataSource,
    incomingInvitations,
    isCaptain,
    members,
    outgoingInvitations,
    protectedDataError,
    team: selectedTeam,
    teamResolution
  };
}

export async function sendTeamInvitation(teamId: string, input: TeamInvitationInput) {
  const accessToken = await getFreshAccessToken();
  const invitee = input.invitee.trim();

  if (!invitee) {
    throw new Error("Player id or email is required.");
  }

  return asInvitation(
    await postApiAuthenticated<BackendTeamInvitationResponse>(
      `/teams/${teamId}/invitations`,
      {
        inviteeEmail: uuidPattern.test(invitee) ? null : invitee,
        inviteeProfileId: uuidPattern.test(invitee) ? invitee : null,
        proposedRole: input.proposedRole
      },
      accessToken
    )
  );
}

export async function createTeam(input: CreateTeamInput) {
  const accessToken = await getFreshAccessToken();

  return asTeam(
    await postApiAuthenticated<BackendTeamResponse>(
      "/teams",
      {
        description: input.description?.trim() || null,
        name: input.name.trim(),
        region: input.region?.trim() || null,
        slug: input.slug?.trim() || null,
        tag: input.tag?.trim() || null
      },
      accessToken
    )
  );
}

export async function updateTeamMemberRole(teamId: string, memberId: string, role: TeamMemberRole) {
  const accessToken = await getFreshAccessToken();

  return asMember(
    await patchApiAuthenticated<BackendTeamMemberResponse>(
      `/teams/${teamId}/members/${memberId}`,
      { role },
      accessToken
    )
  );
}

export async function removeTeamMember(teamId: string, memberId: string) {
  const accessToken = await getFreshAccessToken();

  return asMember(
    await deleteApiAuthenticated<BackendTeamMemberResponse>(
      `/teams/${teamId}/members/${memberId}`,
      accessToken
    )
  );
}

export async function acceptTeamInvitation(invitationId: string) {
  const accessToken = await getFreshAccessToken();

  return asInvitation(
    await postApiAuthenticated<BackendTeamInvitationResponse>(
      `/team-invitations/${invitationId}/accept`,
      {},
      accessToken
    )
  );
}

export async function declineTeamInvitation(invitationId: string) {
  const accessToken = await getFreshAccessToken();

  return asInvitation(
    await postApiAuthenticated<BackendTeamInvitationResponse>(
      `/team-invitations/${invitationId}/decline`,
      {},
      accessToken
    )
  );
}

export async function cancelTeamInvitation(invitationId: string) {
  const accessToken = await getFreshAccessToken();

  return asInvitation(
    await postApiAuthenticated<BackendTeamInvitationResponse>(
      `/team-invitations/${invitationId}/cancel`,
      {},
      accessToken
    )
  );
}
