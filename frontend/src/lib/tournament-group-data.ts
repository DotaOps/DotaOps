import {
  deleteApiAuthenticated,
  getApi,
  getApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";

export interface PublicTournamentTeam {
  bannerUrl: string | null;
  id: string;
  logoUrl: string | null;
  manualPlayers: PublicTournamentManualPlayer[];
  name: string;
  seedNumber: number | null;
  slug: string | null;
  tag: string | null;
}

export interface PublicTournamentManualPlayer {
  displayName: string;
  id: string;
  nickname: string | null;
  note: string | null;
}

export interface PublicTournamentGroup {
  displayOrder: number;
  groupId: string;
  groupName: string;
  teams: PublicTournamentTeam[];
  tournamentId: string;
}

export interface TournamentGroup {
  createdAt: string | null;
  id: string;
  name: string;
  sortOrder: number;
  teams: TournamentGroupTeam[];
  tournamentId: string;
  updatedAt: string | null;
}

export interface TournamentGroupTeam {
  createdAt: string | null;
  groupId: string;
  id: string;
  registrationId: string | null;
  seedNumber: number | null;
  teamId: string;
  teamName: string;
  teamSlug: string | null;
  teamTag: string | null;
  tournamentId: string;
  updatedAt: string | null;
}

export interface GroupStanding {
  gameDiff: number;
  gameLosses: number;
  gameWins: number;
  groupId: string;
  groupName: string | null;
  matchDraws: number;
  matchLosses: number;
  matchWins: number;
  matchesPlayed: number;
  points: number;
  rank: number;
  teamId: string;
  teamName: string;
  tournamentId: string;
}

export interface PublicGroupsStandingsData {
  groups: PublicTournamentGroup[];
  standings: GroupStanding[];
}

export interface OrganizerGroupsData {
  groups: TournamentGroup[];
  standings: GroupStanding[];
  standingsError: string | null;
}

export interface CreateTournamentGroupInput {
  name: string;
  sortOrder?: number | null;
}

export interface AddTeamToGroupInput {
  seedNumber?: number | null;
  teamId: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function arrayPayload(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }

  if (isRecord(value) && Array.isArray(value.content)) {
    return value.content;
  }

  if (isRecord(value) && Array.isArray(value.items)) {
    return value.items;
  }

  return [];
}

function text(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function nullableText(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function numberValue(value: unknown, fallback = 0) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function nullableNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function mapPublicTeam(value: unknown): PublicTournamentTeam {
  if (!isRecord(value)) {
    return {
      bannerUrl: null,
      id: "unknown-team",
      logoUrl: null,
      manualPlayers: [],
      name: "Unknown team",
      seedNumber: null,
      slug: null,
      tag: null
    };
  }

  return {
    bannerUrl: nullableText(value.bannerUrl),
    id: text(value.id, "unknown-team"),
    logoUrl: nullableText(value.logoUrl),
    manualPlayers: arrayPayload(value.manualPlayers)
      .filter(isRecord)
      .map((player) => ({
        displayName: text(player.displayName, "Manual player"),
        id: text(player.id),
        nickname: nullableText(player.nickname),
        note: nullableText(player.note)
      })),
    name: text(value.name, "Unknown team"),
    seedNumber: nullableNumber(value.seedNumber),
    slug: nullableText(value.slug),
    tag: nullableText(value.tag)
  };
}

function mapPublicGroup(value: unknown): PublicTournamentGroup {
  if (!isRecord(value)) {
    throw new Error("Public tournament group API returned an unexpected group shape.");
  }

  return {
    displayOrder: numberValue(value.displayOrder ?? value.sortOrder, 0),
    groupId: text(value.groupId ?? value.id),
    groupName: text(value.groupName ?? value.name, "Unnamed group"),
    teams: arrayPayload(value.teams).map(mapPublicTeam),
    tournamentId: text(value.tournamentId)
  };
}

function mapOrganizerGroup(value: unknown, teams: TournamentGroupTeam[] = []): TournamentGroup {
  if (!isRecord(value) || typeof value.id !== "string") {
    throw new Error("Organizer tournament group API returned an unexpected group shape.");
  }

  return {
    createdAt: nullableText(value.createdAt),
    id: value.id,
    name: text(value.name, "Unnamed group"),
    sortOrder: numberValue(value.sortOrder, 0),
    teams,
    tournamentId: text(value.tournamentId),
    updatedAt: nullableText(value.updatedAt)
  };
}

function mapOrganizerGroupTeam(value: unknown): TournamentGroupTeam {
  if (!isRecord(value) || typeof value.id !== "string" || typeof value.teamId !== "string") {
    throw new Error("Organizer group team API returned an unexpected team assignment shape.");
  }

  return {
    createdAt: nullableText(value.createdAt),
    groupId: text(value.groupId),
    id: value.id,
    registrationId: nullableText(value.registrationId),
    seedNumber: nullableNumber(value.seedNumber),
    teamId: value.teamId,
    teamName: text(value.teamName, "Unknown team"),
    teamSlug: nullableText(value.teamSlug),
    teamTag: nullableText(value.teamTag),
    tournamentId: text(value.tournamentId),
    updatedAt: nullableText(value.updatedAt)
  };
}

function mapStanding(value: unknown): GroupStanding {
  if (!isRecord(value) || typeof value.teamId !== "string") {
    throw new Error("Group standings API returned an unexpected standing shape.");
  }

  return {
    gameDiff: numberValue(value.gameDiff),
    gameLosses: numberValue(value.gameLosses),
    gameWins: numberValue(value.gameWins),
    groupId: text(value.groupId),
    groupName: nullableText(value.groupName),
    matchDraws: numberValue(value.matchDraws),
    matchLosses: numberValue(value.matchLosses),
    matchWins: numberValue(value.matchWins),
    matchesPlayed: numberValue(value.matchesPlayed),
    points: numberValue(value.points),
    rank: numberValue(value.rank),
    teamId: value.teamId,
    teamName: text(value.teamName, "Unknown team"),
    tournamentId: text(value.tournamentId)
  };
}

function mapPublicGroups(value: unknown) {
  return arrayPayload(value).map(mapPublicGroup);
}

function mapOrganizerGroups(value: unknown) {
  return arrayPayload(value).map((item) => mapOrganizerGroup(item));
}

function mapOrganizerGroupTeams(value: unknown) {
  return arrayPayload(value).map(mapOrganizerGroupTeam);
}

function mapStandings(value: unknown) {
  return arrayPayload(value).map(mapStanding);
}

export async function getPublicGroupsStandingsData(
  tournamentId: string
): Promise<PublicGroupsStandingsData> {
  const [groups, standings] = await Promise.all([
    getApi<unknown>(`/public/tournaments/${tournamentId}/groups`),
    getApi<unknown>(`/public/tournaments/${tournamentId}/standings`)
  ]);

  return {
    groups: mapPublicGroups(groups),
    standings: mapStandings(standings)
  };
}

export async function listOrganizerTournamentGroups(tournamentId: string) {
  return mapOrganizerGroups(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/groups`)
  );
}

export async function listOrganizerGroupTeams(groupId: string) {
  return mapOrganizerGroupTeams(
    await getApiAuthenticated<unknown>(`/organizer/tournament-groups/${groupId}/teams`)
  );
}

export async function listOrganizerTournamentGroupsWithTeams(tournamentId: string) {
  const groups = await listOrganizerTournamentGroups(tournamentId);
  const teamsByGroup = await Promise.all(
    groups.map(async (group) => ({
      groupId: group.id,
      teams: await listOrganizerGroupTeams(group.id)
    }))
  );

  const teamsByGroupId = new Map(teamsByGroup.map((item) => [item.groupId, item.teams]));

  return groups.map((group) => ({
    ...group,
    teams: teamsByGroupId.get(group.id) ?? []
  }));
}

export async function getOrganizerGroupsData(tournamentId: string): Promise<OrganizerGroupsData> {
  const groups = await listOrganizerTournamentGroupsWithTeams(tournamentId);
  let standings: GroupStanding[] = [];
  let standingsError: string | null = null;

  try {
    standings = mapStandings(
      await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/standings`)
    );
  } catch (error) {
    standingsError = error instanceof Error
      ? error.message
      : "Organizer standings endpoint is not available for private tournament data.";
  }

  return {
    groups,
    standings,
    standingsError
  };
}

export async function createOrganizerTournamentGroup(
  tournamentId: string,
  input: CreateTournamentGroupInput
) {
  return mapOrganizerGroup(
    await postApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/groups`, {
      name: input.name,
      sortOrder: input.sortOrder ?? null
    })
  );
}

export async function addTeamToOrganizerGroup(
  groupId: string,
  input: AddTeamToGroupInput
) {
  return mapOrganizerGroupTeam(
    await postApiAuthenticated<unknown>(`/organizer/tournament-groups/${groupId}/teams`, {
      seedNumber: input.seedNumber ?? null,
      teamId: input.teamId
    })
  );
}

export async function removeTeamFromOrganizerGroup(groupId: string, teamId: string) {
  await deleteApiAuthenticated<unknown>(`/organizer/tournament-groups/${groupId}/teams/${teamId}`);
}
