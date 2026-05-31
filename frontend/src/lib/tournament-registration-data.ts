"use client";

import {
  getApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";

export type RegistrationStatus =
  | "pending"
  | "approved"
  | "rejected"
  | "waitlisted"
  | "checked-in";

export type ReviewRegistrationAction = "approve" | "reject" | "waitlist";

export interface CreateRegistrationPayload {
  contactEmail?: string | null;
  message?: string | null;
  teamId: string;
}

export interface TournamentRegistrationMember {
  avatarUrl: string | null;
  createdAt: string | null;
  displayName: string | null;
  id: string;
  manual: boolean;
  manualPlayerId: string | null;
  nickname: string;
  note: string | null;
  profileId: string | null;
  role: string | null;
  starter: boolean;
  teamMemberId: string | null;
  updatedAt: string | null;
}

export interface TournamentRegistration {
  captainNickname: string | null;
  captainProfileId: string | null;
  checkedInAt: string | null;
  contactEmail: string | null;
  createdAt: string | null;
  displayStatus: RegistrationStatus;
  id: string;
  members: TournamentRegistrationMember[];
  message: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
  reviewedByNickname: string | null;
  seedNumber: number | null;
  status: Exclude<RegistrationStatus, "checked-in">;
  teamId: string;
  teamName: string;
  teamSlug: string | null;
  teamTag: string | null;
  tournamentId: string;
  tournamentSlug: string | null;
  tournamentTitle: string | null;
  updatedAt: string | null;
}

interface TournamentRegistrationDto {
  captainNickname?: string | null;
  captainProfileId?: string | null;
  checkedInAt?: string | null;
  contactEmail?: string | null;
  createdAt?: string | null;
  id: string;
  members?: TournamentRegistrationMemberDto[] | null;
  message?: string | null;
  reviewedAt?: string | null;
  reviewedBy?: string | null;
  reviewedByNickname?: string | null;
  seedNumber?: number | null;
  status?: string | null;
  teamId: string;
  teamName?: string | null;
  teamSlug?: string | null;
  teamTag?: string | null;
  tournamentId: string;
  tournamentSlug?: string | null;
  tournamentTitle?: string | null;
  updatedAt?: string | null;
}

interface TournamentRegistrationMemberDto {
  avatarUrl?: string | null;
  createdAt?: string | null;
  displayName?: string | null;
  id: string;
  manual?: boolean | null;
  manualPlayerId?: string | null;
  nickname?: string | null;
  note?: string | null;
  profileId?: string | null;
  role?: string | null;
  starter?: boolean | null;
  teamMemberId?: string | null;
  updatedAt?: string | null;
}

const backendStatuses = ["pending", "approved", "rejected", "waitlisted"] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function normalizeBackendStatus(value?: string | null): TournamentRegistration["status"] {
  return backendStatuses.includes(value as TournamentRegistration["status"])
    ? (value as TournamentRegistration["status"])
    : "pending";
}

function normalizeOptionalText(value?: string | null) {
  const trimmed = value?.trim();

  return trimmed ? trimmed : null;
}

function mapMember(value: TournamentRegistrationMemberDto): TournamentRegistrationMember {
  return {
    avatarUrl: value.avatarUrl ?? null,
    createdAt: value.createdAt ?? null,
    displayName: value.displayName ?? null,
    id: value.id,
    manual: value.manual ?? Boolean(value.manualPlayerId),
    manualPlayerId: value.manualPlayerId ?? null,
    nickname: value.nickname ?? "Unknown player",
    note: value.note ?? null,
    profileId: value.profileId ?? null,
    role: value.role ?? null,
    starter: value.starter ?? false,
    teamMemberId: value.teamMemberId ?? null,
    updatedAt: value.updatedAt ?? null
  };
}

export function mapTournamentRegistration(value: unknown): TournamentRegistration {
  if (
    !isRecord(value) ||
    typeof value.id !== "string" ||
    typeof value.teamId !== "string" ||
    typeof value.tournamentId !== "string"
  ) {
    throw new Error("Tournament registration API returned an unexpected registration shape.");
  }

  const dto = value as unknown as TournamentRegistrationDto;
  const status = normalizeBackendStatus(dto.status);
  const checkedIn = Boolean(dto.checkedInAt);

  return {
    captainNickname: dto.captainNickname ?? null,
    captainProfileId: dto.captainProfileId ?? null,
    checkedInAt: dto.checkedInAt ?? null,
    contactEmail: dto.contactEmail ?? null,
    createdAt: dto.createdAt ?? null,
    displayStatus: checkedIn ? "checked-in" : status,
    id: dto.id,
    members: Array.isArray(dto.members) ? dto.members.map(mapMember) : [],
    message: dto.message ?? null,
    reviewedAt: dto.reviewedAt ?? null,
    reviewedBy: dto.reviewedBy ?? null,
    reviewedByNickname: dto.reviewedByNickname ?? null,
    seedNumber: dto.seedNumber ?? null,
    status,
    teamId: dto.teamId,
    teamName: dto.teamName ?? "Unknown team",
    teamSlug: dto.teamSlug ?? null,
    teamTag: dto.teamTag ?? null,
    tournamentId: dto.tournamentId,
    tournamentSlug: dto.tournamentSlug ?? null,
    tournamentTitle: dto.tournamentTitle ?? null,
    updatedAt: dto.updatedAt ?? null
  };
}

function mapRegistrationList(value: unknown): TournamentRegistration[] {
  if (!Array.isArray(value)) {
    throw new Error("Tournament registration API returned an unexpected list shape.");
  }

  return value.map(mapTournamentRegistration);
}

export function registrationPayload(input: CreateRegistrationPayload) {
  return {
    contactEmail: normalizeOptionalText(input.contactEmail),
    message: normalizeOptionalText(input.message),
    teamId: input.teamId
  };
}

export async function submitTeamTournamentRegistration(
  tournamentId: string,
  input: CreateRegistrationPayload
) {
  return mapTournamentRegistration(
    await postApiAuthenticated<unknown>(
      `/tournaments/${tournamentId}/registrations`,
      registrationPayload(input)
    )
  );
}

export async function listTeamTournamentRegistrations(teamId: string) {
  return mapRegistrationList(
    await getApiAuthenticated<unknown>(`/teams/${teamId}/tournament-registrations`)
  );
}

export async function listOrganizerTournamentRegistrations(
  tournamentId: string,
  status?: Exclude<RegistrationStatus, "checked-in"> | "all"
) {
  const query = status && status !== "all" ? `?status=${encodeURIComponent(status)}` : "";

  return mapRegistrationList(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/registrations${query}`)
  );
}

export async function reviewTournamentRegistration(
  tournamentId: string,
  registrationId: string,
  action: ReviewRegistrationAction,
  seedNumber?: number | null
) {
  return mapTournamentRegistration(
    await postApiAuthenticated<unknown>(
      `/organizer/tournaments/${tournamentId}/registrations/${registrationId}/${action}`,
      action === "approve" && seedNumber ? { seedNumber } : {}
    )
  );
}

export async function checkInTournamentRegistration(tournamentId: string, registrationId: string) {
  return mapTournamentRegistration(
    await postApiAuthenticated<unknown>(
      `/tournaments/${tournamentId}/registrations/${registrationId}/check-in`,
      {}
    )
  );
}
