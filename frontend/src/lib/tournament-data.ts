import {
  ApiRequestError,
  getApi,
  getApiAuthenticated,
  getPagedApi,
  patchApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";
import type { Tournament, TournamentStatus } from "@/lib/types";

interface BackendTournamentDto {
  id?: string | null;
  slug?: string | null;
  title?: string | null;
  status?: string | null;
  format?: string | null;
  teamSize?: number | null;
  settings?: {
    teamSize?: number | null;
  } | null;
  organizer?: string | null;
  organizerNickname?: string | null;
  description?: string | null;
  rules?: string | null;
  prizePool?: string | null;
  maxTeams?: number | null;
  registrationsCount?: number | null;
  startsAt?: string | null;
  endsAt?: string | null;
  registrationOpensAt?: string | null;
  registrationClosesAt?: string | null;
  checkInOpensAt?: string | null;
  checkInClosesAt?: string | null;
  timezone?: string | null;
  publicVisible?: boolean | null;
  publishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

interface SupabaseTournamentRow {
  check_in_closes_at?: string | null;
  check_in_opens_at?: string | null;
  created_at?: string | null;
  description?: string | null;
  ends_at?: string | null;
  format?: string | null;
  id?: string | null;
  is_public?: boolean | null;
  max_teams?: number | null;
  prize_pool?: string | null;
  published_at?: string | null;
  registration_closes_at?: string | null;
  registration_opens_at?: string | null;
  slug?: string | null;
  starts_at?: string | null;
  status?: string | null;
  title?: string | null;
  tournament_registrations?: Array<{ count?: number | null }> | null;
  updated_at?: string | null;
}

export interface TournamentWriteInput {
  checkInClosesAt?: string | null;
  checkInOpensAt?: string | null;
  description?: string | null;
  endsAt?: string | null;
  format?: string | null;
  maxTeams?: number | null;
  prizePool?: string | null;
  registrationClosesAt?: string | null;
  registrationOpensAt?: string | null;
  rules?: string | null;
  slug?: string | null;
  startsAt?: string | null;
  timezone?: string | null;
  title?: string | null;
}

const validStatuses: TournamentStatus[] = [
  "draft",
  "registration",
  "published",
  "live",
  "finished",
  "archived"
];
const publicStatuses: TournamentStatus[] = [
  "registration",
  "published",
  "live",
  "finished"
];
const publicTournamentPageSize = 100;
const supabaseTournamentSelect = `
  id,
  slug,
  title,
  status,
  format,
  description,
  prize_pool,
  max_teams,
  starts_at,
  ends_at,
  registration_opens_at,
  registration_closes_at,
  check_in_opens_at,
  check_in_closes_at,
  is_public,
  published_at,
  created_at,
  updated_at,
  tournament_registrations(count)
`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function normalizeStatus(status?: string | null): TournamentStatus {
  if (!validStatuses.includes(status as TournamentStatus)) {
    throw new Error("Tournament API returned an unsupported tournament status.");
  }

  return status as TournamentStatus;
}

function normalizeTeamSize(value?: number | null) {
  return value === 1 || value === 3 || value === 5 ? value : null;
}

export function mapTournamentDto(value: BackendTournamentDto): Tournament {
  if (!value.id || !value.slug || !value.title) {
    throw new Error("Tournament API returned an incomplete tournament record.");
  }

  return {
    description: value.description ?? "No tournament description available.",
    checkInClosesAt: value.checkInClosesAt ?? null,
    checkInOpensAt: value.checkInOpensAt ?? null,
    endsAt: value.endsAt ?? null,
    format: value.format ?? "Format unavailable",
    teamSize: normalizeTeamSize(value.teamSize ?? value.settings?.teamSize),
    id: value.id,
    organizer: value.organizerNickname ?? value.organizer ?? "Organizer unavailable",
    prizePool: value.prizePool ?? "Not announced",
    publicVisible: value.publicVisible ?? null,
    registrationClosesAt: value.registrationClosesAt ?? null,
    registrationOpensAt: value.registrationOpensAt ?? null,
    registrationsCount: value.registrationsCount ?? 0,
    slug: value.slug,
    startsAt: value.startsAt ?? null,
    status: normalizeStatus(value.status),
    teamsCount: value.maxTeams ?? 0,
    title: value.title ?? "Untitled Tournament",
    updatedAt: value.updatedAt ?? null
  };
}

function mapSupabaseTournamentRow(value: SupabaseTournamentRow): Tournament {
  const registrationsCount =
    value.tournament_registrations?.reduce(
      (total, registration) => total + (registration.count ?? 0),
      0
    ) ?? 0;

  return mapTournamentDto({
    checkInClosesAt: value.check_in_closes_at,
    checkInOpensAt: value.check_in_opens_at,
    createdAt: value.created_at,
    description: value.description,
    endsAt: value.ends_at,
    format: value.format,
    id: value.id,
    maxTeams: value.max_teams,
    prizePool: value.prize_pool,
    publicVisible: value.is_public,
    publishedAt: value.published_at,
    registrationClosesAt: value.registration_closes_at,
    registrationOpensAt: value.registration_opens_at,
    registrationsCount,
    slug: value.slug,
    startsAt: value.starts_at,
    status: value.status,
    title: value.title,
    updatedAt: value.updated_at
  });
}

function safeMapTournamentList(value: unknown): Tournament[] {
  const items =
    Array.isArray(value)
      ? value
      : isRecord(value) && Array.isArray(value.content)
        ? value.content
        : isRecord(value) && Array.isArray(value.items)
          ? value.items
          : null;

  if (!items) {
    throw new Error("Tournament API returned an unexpected list payload shape.");
  }

  const invalidItems = items.filter((item) => !isRecord(item));

  if (invalidItems.length > 0) {
    throw new Error("Tournament API returned invalid list items.");
  }

  return items.map((item) => mapTournamentDto(item as BackendTournamentDto));
}

function safeMapTournament(value: unknown): Tournament | null {
  if (!value) {
    return null;
  }

  if (!isRecord(value)) {
    throw new Error("Tournament API returned an unexpected detail payload shape.");
  }

  return mapTournamentDto(value as BackendTournamentDto);
}

export async function getPublicTournaments(): Promise<Tournament[]> {
  return safeMapTournamentList(await getApi<unknown>("/tournaments"));
}

export async function getPublicTournamentBySlug(slug: string): Promise<Tournament | null> {
  return safeMapTournament(await getApi<unknown>(`/tournaments/${slug}`));
}

export async function getOrganizerTournamentsForCurrentUser(): Promise<Tournament[]> {
  return safeMapTournamentList(await getApiAuthenticated<unknown>("/organizer/tournaments"));
}

export async function getOrganizerTournamentForCurrentUser(
  tournamentId: string
): Promise<Tournament> {
  const tournament = safeMapTournament(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}`)
  );

  if (!tournament) {
    throw new ApiRequestError("Organizer tournament was not found.", 404);
  }

  return tournament;
}

export async function createOrganizerTournament(input: TournamentWriteInput): Promise<Tournament> {
  return mapTournamentDto(
    await postApiAuthenticated<BackendTournamentDto>("/organizer/tournaments", input)
  );
}

export async function updateOrganizerTournament(
  tournamentId: string,
  input: TournamentWriteInput
): Promise<Tournament> {
  return mapTournamentDto(
    await patchApiAuthenticated<BackendTournamentDto>(
      `/organizer/tournaments/${tournamentId}`,
      input
    )
  );
}

export async function publishOrganizerTournament(tournamentId: string): Promise<Tournament> {
  return mapTournamentDto(
    await postApiAuthenticated<BackendTournamentDto>(
      `/organizer/tournaments/${tournamentId}/publish`,
      {}
    )
  );
}

export async function archiveOrganizerTournament(tournamentId: string): Promise<Tournament> {
  return mapTournamentDto(
    await postApiAuthenticated<BackendTournamentDto>(
      `/organizer/tournaments/${tournamentId}/archive`,
      {}
    )
  );
}
