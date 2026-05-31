import {
  ApiRequestError,
  getApi,
  getApiAuthenticated,
  getPagedApi,
  patchApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import type { Tournament, TournamentStatus } from "@/lib/types";

interface BackendTournamentDto {
  id?: string | null;
  slug?: string | null;
  title?: string | null;
  status?: string | null;
  format?: string | null;
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

const fallbackDate = "2026-05-20T19:00:00Z";
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
  return validStatuses.includes(status as TournamentStatus)
    ? (status as TournamentStatus)
    : "draft";
}

function fallbackSlug(value: BackendTournamentDto) {
  if (value.slug) {
    return value.slug;
  }

  return (value.title ?? value.id ?? "tournament")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

export function mapTournamentDto(value: BackendTournamentDto): Tournament {
  const id = value.id ?? fallbackSlug(value);

  return {
    description: value.description ?? "Tournament details are being prepared.",
    checkInClosesAt: value.checkInClosesAt ?? null,
    checkInOpensAt: value.checkInOpensAt ?? null,
    endsAt: value.endsAt ?? null,
    format: value.format ?? "Dota 2",
    id,
    organizer: value.organizerNickname ?? value.organizer ?? "DotaOps",
    prizePool: value.prizePool ?? "TBD",
    publicVisible: value.publicVisible ?? null,
    registrationClosesAt: value.registrationClosesAt ?? null,
    registrationOpensAt: value.registrationOpensAt ?? null,
    registrationsCount: value.registrationsCount ?? 0,
    slug: fallbackSlug(value),
    startsAt: value.startsAt ?? fallbackDate,
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

function safeMapTournament(value: unknown, fallback: Tournament | null): Tournament | null {
  if (!value) {
    return fallback;
  }

  if (!isRecord(value)) {
    console.warn("Tournament API returned an unexpected detail payload shape.");
    return fallback;
  }

  return mapTournamentDto(value as BackendTournamentDto);
}

function hasNextTournamentPage(value: unknown) {
  return (
    isRecord(value) &&
    isRecord(value.page) &&
    value.page.hasNext === true
  );
}

async function listPublicTournamentsFromApi(): Promise<Tournament[]> {
  const tournaments: Tournament[] = [];
  let page = 0;
  let payload: unknown;

  do {
    payload = await getPagedApi<unknown>(
      `/tournaments?page=${page}&size=${publicTournamentPageSize}`
    );
    tournaments.push(...safeMapTournamentList(payload));
    page += 1;
  } while (hasNextTournamentPage(payload));

  return tournaments;
}

async function listPublicTournamentsFromSupabase(): Promise<Tournament[]> {
  const supabase = await getSupabaseServerClient();

  if (!supabase) {
    return [];
  }

  const tournaments: Tournament[] = [];

  for (let from = 0; ; from += publicTournamentPageSize) {
    const { data, error } = await supabase
      .from("tournaments")
      .select(supabaseTournamentSelect)
      .eq("is_public", true)
      .in("status", publicStatuses)
      .order("starts_at", { ascending: true })
      .order("created_at", { ascending: false })
      .order("id", { ascending: false })
      .range(from, from + publicTournamentPageSize - 1);

    if (error) {
      throw error;
    }

    const page = (data as SupabaseTournamentRow[]).map(mapSupabaseTournamentRow);
    tournaments.push(...page);

    if (page.length < publicTournamentPageSize) {
      return tournaments;
    }
  }
}

async function getPublicTournamentBySlugFromSupabase(slug: string): Promise<Tournament | null> {
  const supabase = await getSupabaseServerClient();

  if (!supabase) {
    return null;
  }

  const { data, error } = await supabase
    .from("tournaments")
    .select(supabaseTournamentSelect)
    .eq("slug", slug)
    .eq("is_public", true)
    .in("status", publicStatuses)
    .maybeSingle();

  if (error) {
    throw error;
  }

  return data ? mapSupabaseTournamentRow(data as SupabaseTournamentRow) : null;
}

export async function getPublicTournaments(): Promise<Tournament[]> {
  try {
    return await listPublicTournamentsFromApi();
  } catch (error) {
    console.warn("Public tournaments API unavailable; trying Supabase.", error);
  }

  try {
    return await listPublicTournamentsFromSupabase();
  } catch (error) {
    console.error("Public tournaments database query failed.", error);
    return [];
  }
}

export async function getPublicTournamentBySlug(slug: string): Promise<Tournament | null> {
  try {
    return safeMapTournament(await getApi<unknown>(`/tournaments/${slug}`), null);
  } catch (error) {
    console.warn("Public tournament detail API unavailable; trying Supabase.", error);
  }

  try {
    return await getPublicTournamentBySlugFromSupabase(slug);
  } catch (error) {
    console.error("Public tournament database detail query failed.", error);
    return null;
  }
}

export async function getOrganizerTournamentsForCurrentUser(): Promise<Tournament[]> {
  return safeMapTournamentList(await getApiAuthenticated<unknown>("/organizer/tournaments"));
}

export async function getOrganizerTournamentForCurrentUser(
  tournamentId: string
): Promise<Tournament> {
  const tournament = safeMapTournament(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}`),
    null
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
