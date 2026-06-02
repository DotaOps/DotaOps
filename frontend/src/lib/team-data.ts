"use client";

import {
  deleteApiAuthenticated,
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

export type TeamJoinRequestStatus = "pending" | "accepted" | "declined" | "cancelled";

export interface TeamSummary {
  bannerUrl: string | null;
  captainNickname: string | null;
  captainProfileId: string | null;
  description: string | null;
  id: string;
  logoUrl: string | null;
  manualPlayers: TeamManualPlayer[];
  name: string;
  region: string | null;
  slug: string;
  tag: string | null;
}

export interface TeamManualPlayer {
  createdAt: string | null;
  displayName: string;
  id: string;
  nickname: string | null;
  note: string | null;
  teamId: string;
  updatedAt: string | null;
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

export interface TeamJoinRequest {
  createdAt: string | null;
  id: string;
  message: string | null;
  requesterDisplayName: string | null;
  requesterProfileId: string;
  resolvedAt: string | null;
  resolvedByDisplayName: string | null;
  resolvedByProfileId: string | null;
  status: TeamJoinRequestStatus;
  teamId: string;
  teamName: string;
  teamSlug: string;
  updatedAt: string | null;
}

export interface TeamManagementViewModel {
  accessToken: string;
  activeEvents: TournamentRegistration[];
  availableTeams: TeamSummary[];
  canCreateTeam: boolean;
  canInvitePlayers: boolean;
  canManageTeam: boolean;
  canManageRoster: boolean;
  canRequestTeamMembership: boolean;
  canViewAnalytics: boolean;
  currentProfile: CurrentUserProfile;
  currentUserTeamRole: string | null;
  dataSource: "api";
  incomingInvitations: TeamInvitation[];
  isCaptain: boolean;
  isTeamOwner: boolean;
  manualPlayers: TeamManualPlayer[];
  members: TeamMember[];
  outgoingJoinRequests: TeamJoinRequest[];
  outgoingInvitations: TeamInvitation[];
  protectedDataError: string | null;
  team: TeamSummary | null;
  teamJoinRequests: TeamJoinRequest[];
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
  bannerUrl?: string | null;
  captainNickname?: string | null;
  captainProfileId?: string | null;
  description?: string | null;
  id: string;
  logoUrl?: string | null;
  manualPlayers?: BackendTeamManualPlayerResponse[] | null;
  name: string;
  region?: string | null;
  slug: string;
  tag?: string | null;
}

interface BackendTeamManualPlayerResponse {
  createdAt?: string | null;
  displayName: string;
  id: string;
  nickname?: string | null;
  note?: string | null;
  teamId: string;
  updatedAt?: string | null;
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
  canCreateTeam?: boolean;
  canInvitePlayers?: boolean;
  canManageTeam?: boolean;
  canManageRoster?: boolean;
  canViewAnalytics?: boolean;
  captain?: boolean;
  currentUserTeamRole?: string | null;
  isTeamOwner?: boolean;
  manualPlayers?: BackendTeamManualPlayerResponse[] | null;
  members?: BackendTeamMemberResponse[] | null;
  team?: BackendTeamResponse | null;
  teamResolution?: string | null;
}

interface BackendTeamJoinRequestResponse {
  createdAt?: string | null;
  id: string;
  message?: string | null;
  requesterDisplayName?: string | null;
  requesterProfileId: string;
  resolvedAt?: string | null;
  resolvedByDisplayName?: string | null;
  resolvedByProfileId?: string | null;
  status: TeamJoinRequestStatus;
  teamId: string;
  teamName: string;
  teamSlug: string;
  updatedAt?: string | null;
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
    bannerUrl: response.bannerUrl ?? null,
    captainNickname: response.captainNickname ?? null,
    captainProfileId: response.captainProfileId ?? null,
    description: response.description ?? null,
    id: response.id,
    logoUrl: response.logoUrl ?? null,
    manualPlayers: (response.manualPlayers ?? []).map(asManualPlayer),
    name: response.name,
    region: response.region ?? null,
    slug: response.slug,
    tag: response.tag ?? null
  };
}

function asManualPlayer(response: BackendTeamManualPlayerResponse): TeamManualPlayer {
  return {
    createdAt: response.createdAt ?? null,
    displayName: response.displayName,
    id: response.id,
    nickname: response.nickname ?? null,
    note: response.note ?? null,
    teamId: response.teamId,
    updatedAt: response.updatedAt ?? null
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

function asJoinRequest(response: BackendTeamJoinRequestResponse): TeamJoinRequest {
  return {
    createdAt: response.createdAt ?? null,
    id: response.id,
    message: response.message ?? null,
    requesterDisplayName: response.requesterDisplayName ?? null,
    requesterProfileId: response.requesterProfileId,
    resolvedAt: response.resolvedAt ?? null,
    resolvedByDisplayName: response.resolvedByDisplayName ?? null,
    resolvedByProfileId: response.resolvedByProfileId ?? null,
    status: response.status,
    teamId: response.teamId,
    teamName: response.teamName,
    teamSlug: response.teamSlug,
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
  const dataSource: "api" = teamsResult.source;
  const availableTeams = teamsResult.data;
  const aggregateTeam = currentTeamAggregate?.team ? asTeam(currentTeamAggregate.team) : null;
  const selectedTeam = aggregateTeam;
  const members = (currentTeamAggregate?.members ?? []).map(asMember);
  let manualPlayers =
    (currentTeamAggregate?.manualPlayers ?? currentTeamAggregate?.team?.manualPlayers ?? []).map(
      asManualPlayer
    );
  const hasAggregateManualPlayers =
    Array.isArray(currentTeamAggregate?.manualPlayers) ||
    Array.isArray(currentTeamAggregate?.team?.manualPlayers);

  if (selectedTeam && !hasAggregateManualPlayers) {
    try {
      manualPlayers = (
        await getApi<BackendTeamManualPlayerResponse[]>(`/teams/${selectedTeam.id}/manual-players`)
      ).map(asManualPlayer);
    } catch {
      manualPlayers = selectedTeam.manualPlayers;
    }
  }
  const isTeamOwner = Boolean(currentTeamAggregate?.isTeamOwner);
  const isCaptain = isTeamOwner;
  const canCreateTeam = Boolean(currentTeamAggregate?.canCreateTeam);
  const canManageTeam = Boolean(currentTeamAggregate?.canManageTeam);
  const canManageRoster = Boolean(currentTeamAggregate?.canManageRoster);
  const canInvitePlayers = Boolean(currentTeamAggregate?.canInvitePlayers);
  const canViewAnalytics = Boolean(currentTeamAggregate?.canViewAnalytics);
  const canRequestTeamMembership =
    currentProfile.role === "player" && currentTeamAggregate?.team === null;
  const currentUserTeamRole = currentTeamAggregate?.currentUserTeamRole ?? null;
  const teamResolution =
    currentTeamAggregate?.teamResolution ?? "No team found for the current profile.";
  let outgoingInvitations: TeamInvitation[] = [];
  let incomingInvitations: TeamInvitation[] = [];
  let outgoingJoinRequests: TeamJoinRequest[] = [];
  let teamJoinRequests: TeamJoinRequest[] = [];
  let activeEvents: TournamentRegistration[] = [];

  try {
    incomingInvitations = (
      await getApiAuthenticated<BackendTeamInvitationResponse[]>("/me/team-invitations", accessToken)
    ).map(asInvitation);
  } catch (error) {
    protectedDataError = protectedErrorMessage(error);
  }

  if (canRequestTeamMembership) {
    try {
      outgoingJoinRequests = (
        await getApiAuthenticated<BackendTeamJoinRequestResponse[]>("/me/team-join-requests", accessToken)
      ).map(asJoinRequest);
    } catch (error) {
      protectedDataError = protectedDataError ?? protectedErrorMessage(error);
    }
  }

  if (selectedTeam && canInvitePlayers) {
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

  if (selectedTeam && canManageTeam) {
    try {
      teamJoinRequests = (
        await getApiAuthenticated<BackendTeamJoinRequestResponse[]>(
          `/teams/${selectedTeam.id}/join-requests`,
          accessToken
        )
      ).map(asJoinRequest);
    } catch (error) {
      protectedDataError = protectedDataError ?? protectedErrorMessage(error);
    }
  }

  if (selectedTeam) {
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
    availableTeams,
    canCreateTeam,
    canInvitePlayers,
    canManageTeam,
    canManageRoster,
    canRequestTeamMembership,
    canViewAnalytics,
    currentProfile,
    currentUserTeamRole,
    dataSource,
    incomingInvitations,
    isCaptain,
    isTeamOwner,
    manualPlayers,
    members,
    outgoingJoinRequests,
    outgoingInvitations,
    protectedDataError,
    team: selectedTeam,
    teamJoinRequests,
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

export async function createTeamJoinRequest(teamId: string, message?: string) {
  const accessToken = await getFreshAccessToken();

  return asJoinRequest(
    await postApiAuthenticated<BackendTeamJoinRequestResponse>(
      `/teams/${teamId}/join-requests`,
      { message: message?.trim() || null },
      accessToken
    )
  );
}

export async function getMyTeamJoinRequests() {
  const accessToken = await getFreshAccessToken();

  return (
    await getApiAuthenticated<BackendTeamJoinRequestResponse[]>("/me/team-join-requests", accessToken)
  ).map(asJoinRequest);
}

export async function getTeamJoinRequests(teamId: string) {
  const accessToken = await getFreshAccessToken();

  return (
    await getApiAuthenticated<BackendTeamJoinRequestResponse[]>(
      `/teams/${teamId}/join-requests`,
      accessToken
    )
  ).map(asJoinRequest);
}

export async function acceptTeamJoinRequest(requestId: string) {
  const accessToken = await getFreshAccessToken();

  return asJoinRequest(
    await postApiAuthenticated<BackendTeamJoinRequestResponse>(
      `/team-join-requests/${requestId}/accept`,
      {},
      accessToken
    )
  );
}

export async function declineTeamJoinRequest(requestId: string) {
  const accessToken = await getFreshAccessToken();

  return asJoinRequest(
    await postApiAuthenticated<BackendTeamJoinRequestResponse>(
      `/team-join-requests/${requestId}/decline`,
      {},
      accessToken
    )
  );
}

export async function cancelTeamJoinRequest(requestId: string) {
  const accessToken = await getFreshAccessToken();

  return asJoinRequest(
    await postApiAuthenticated<BackendTeamJoinRequestResponse>(
      `/team-join-requests/${requestId}/cancel`,
      {},
      accessToken
    )
  );
}
