"use client";

import { getApiAuthenticated } from "@/lib/api";

export type MeDashboardRole = "admin" | "organizer" | "player";

export interface MeDashboardCapabilities {
  canCreateTeam: boolean;
  canInvitePlayers: boolean;
  canManageRoster: boolean;
  canManageTeam: boolean;
  canManageTournament: boolean;
  canTransferOwnership: boolean;
  canViewAnalytics: boolean;
  canViewOrganizerDashboard: boolean;
  currentUserTeamRole: string | null;
  isTeamOwner: boolean;
}

export interface MeDashboardTeam {
  captainNickname: string | null;
  captainProfileId: string | null;
  id: string;
  name: string;
  slug: string;
  tag: string | null;
}

export interface MeDashboardTeamMember {
  active: boolean;
  avatarUrl: string | null;
  displayName: string | null;
  id: string;
  nickname: string;
  profileId: string;
  role: string;
  teamOwner: boolean;
}

export interface MeDashboardManualPlayer {
  displayName: string;
  id: string;
  nickname: string | null;
}

export interface MeDashboardCurrentTeam {
  canCreateTeam: boolean;
  canInvitePlayers: boolean;
  canManageRoster: boolean;
  canManageTeam: boolean;
  canTransferOwnership: boolean;
  canViewAnalytics: boolean;
  capacity: number;
  currentUserTeamRole: string | null;
  isFull: boolean;
  isTeamOwner: boolean;
  manualPlayers: MeDashboardManualPlayer[];
  members: MeDashboardTeamMember[];
  participantsCount: number;
  slotsFilled: number;
  slotsRemaining: number;
  team: MeDashboardTeam | null;
  teamResolution: string | null;
}

export interface MePlayerDashboard {
  currentTeam: MeDashboardCurrentTeam;
  pendingInvitations: number;
  tournamentRegistrations: number;
}

export interface MeOrganizerDashboard {
  activePublishedTournaments: number;
  importJobs: number;
  pendingRegistrations: number;
  processedMatchGames: number;
  tournaments: number;
}

export interface MeAdminDashboard {
  importJobs: number;
  pendingRegistrations: number;
  profiles: number;
  tournaments: number;
}

export interface MeDashboard {
  admin: MeAdminDashboard | null;
  capabilities: MeDashboardCapabilities;
  organizer: MeOrganizerDashboard | null;
  player: MePlayerDashboard | null;
  role: MeDashboardRole;
}

interface BackendDashboardResponse {
  admin?: unknown;
  capabilities?: unknown;
  organizer?: unknown;
  player?: unknown;
  role?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function asBoolean(value: unknown) {
  return value === true;
}

function asNullableString(value: unknown) {
  return typeof value === "string" && value.trim() ? value : null;
}

function asString(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function asCount(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0
    ? Math.floor(value)
    : 0;
}

function asRole(value: unknown): MeDashboardRole {
  if (value === "player" || value === "organizer" || value === "admin") {
    return value;
  }

  throw new Error("Dashboard response contains an unsupported role.");
}

function mapCapabilities(value: unknown): MeDashboardCapabilities {
  const response = isRecord(value) ? value : {};

  return {
    canCreateTeam: asBoolean(response.canCreateTeam),
    canInvitePlayers: asBoolean(response.canInvitePlayers),
    canManageRoster: asBoolean(response.canManageRoster),
    canManageTeam: asBoolean(response.canManageTeam),
    canManageTournament: asBoolean(response.canManageTournament),
    canTransferOwnership: asBoolean(response.canTransferOwnership),
    canViewAnalytics: asBoolean(response.canViewAnalytics),
    canViewOrganizerDashboard: asBoolean(response.canViewOrganizerDashboard),
    currentUserTeamRole: asNullableString(response.currentUserTeamRole),
    isTeamOwner: asBoolean(response.isTeamOwner)
  };
}

function mapTeam(value: unknown): MeDashboardTeam | null {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.name !== "string") {
    return null;
  }

  return {
    captainNickname: asNullableString(value.captainNickname),
    captainProfileId: asNullableString(value.captainProfileId),
    id: value.id,
    name: value.name,
    slug: asString(value.slug),
    tag: asNullableString(value.tag)
  };
}

function mapMember(value: unknown): MeDashboardTeamMember | null {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.profileId !== "string"
  ) {
    return null;
  }

  return {
    active: asBoolean(value.active),
    avatarUrl: asNullableString(value.avatarUrl),
    displayName: asNullableString(value.displayName),
    id: value.id,
    nickname: asString(value.nickname, "Player"),
    profileId: value.profileId,
    role: asString(value.role, "member"),
    teamOwner: asBoolean(value.teamOwner)
  };
}

function mapManualPlayer(value: unknown): MeDashboardManualPlayer | null {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.displayName !== "string") {
    return null;
  }

  return {
    displayName: value.displayName,
    id: value.id,
    nickname: asNullableString(value.nickname)
  };
}

function mapArray<T>(value: unknown, mapper: (item: unknown) => T | null) {
  return Array.isArray(value)
    ? value.map(mapper).filter((item): item is T => item !== null)
    : [];
}

function mapCurrentTeam(value: unknown): MeDashboardCurrentTeam {
  const response = isRecord(value) ? value : {};

  return {
    canCreateTeam: asBoolean(response.canCreateTeam),
    canInvitePlayers: asBoolean(response.canInvitePlayers),
    canManageRoster: asBoolean(response.canManageRoster),
    canManageTeam: asBoolean(response.canManageTeam),
    canTransferOwnership: asBoolean(response.canTransferOwnership),
    canViewAnalytics: asBoolean(response.canViewAnalytics),
    capacity: asCount(response.capacity),
    currentUserTeamRole: asNullableString(response.currentUserTeamRole),
    isFull: asBoolean(response.isFull),
    isTeamOwner: asBoolean(response.isTeamOwner),
    manualPlayers: mapArray(response.manualPlayers, mapManualPlayer),
    members: mapArray(response.members, mapMember),
    participantsCount: asCount(response.participantsCount),
    slotsFilled: asCount(response.slotsFilled),
    slotsRemaining: asCount(response.slotsRemaining),
    team: mapTeam(response.team),
    teamResolution: asNullableString(response.teamResolution)
  };
}

function mapPlayer(value: unknown): MePlayerDashboard | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    currentTeam: mapCurrentTeam(value.currentTeam),
    pendingInvitations: asCount(value.pendingInvitations),
    tournamentRegistrations: asCount(value.tournamentRegistrations)
  };
}

function mapOrganizer(value: unknown): MeOrganizerDashboard | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    activePublishedTournaments: asCount(value.activePublishedTournaments),
    importJobs: asCount(value.importJobs),
    pendingRegistrations: asCount(value.pendingRegistrations),
    processedMatchGames: asCount(value.processedMatchGames),
    tournaments: asCount(value.tournaments)
  };
}

function mapAdmin(value: unknown): MeAdminDashboard | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    importJobs: asCount(value.importJobs),
    pendingRegistrations: asCount(value.pendingRegistrations),
    profiles: asCount(value.profiles),
    tournaments: asCount(value.tournaments)
  };
}

function mapMeDashboard(value: unknown): MeDashboard {
  if (!isRecord(value)) {
    throw new Error("Dashboard response is empty or has an unsupported shape.");
  }

  const response = value as BackendDashboardResponse;

  return {
    admin: mapAdmin(response.admin),
    capabilities: mapCapabilities(response.capabilities),
    organizer: mapOrganizer(response.organizer),
    player: mapPlayer(response.player),
    role: asRole(response.role)
  };
}

export async function getMeDashboard() {
  return mapMeDashboard(await getApiAuthenticated<unknown>("/me/dashboard"));
}
