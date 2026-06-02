import { getApi, getApiAuthenticated, postApiAuthenticated } from "@/lib/api";

export interface AnalyticsFilters {
  heroId?: string;
  limit?: number;
  profileId?: string;
  teamId?: string;
  tournamentId?: string;
}

export interface PlayerAnalyticsMetric {
  assists: number;
  avgAssists: number;
  avgDeaths: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgKills: number;
  avgXpm: number;
  deaths: number;
  displayName: string;
  gamesPlayed: number;
  kda: number;
  kills: number;
  losses: number;
  profileId: string;
  teamId: string | null;
  teamName: string | null;
  tournamentId: string | null;
  tournamentName: string | null;
  winRate: number;
  wins: number;
}

export interface TeamAnalyticsMetric {
  assists: number;
  avgAssists: number;
  avgDeaths: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgKda: number;
  avgKills: number;
  avgXpm: number;
  deaths: number;
  gamesPlayed: number;
  kills: number;
  losses: number;
  teamId: string;
  teamName: string;
  tournamentId: string | null;
  tournamentName: string | null;
  winRate: number;
  wins: number;
}

export interface HeroAnalyticsMetric {
  assists: number;
  avgAssists: number;
  avgDeaths: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgKills: number;
  avgXpm: number;
  dotaHeroId: number | null;
  gamesPlayed: number;
  heroId: string;
  iconUrl: string | null;
  imageUrl: string | null;
  kda: number;
  kills: number;
  localizedName: string;
  losses: number;
  name: string;
  tournamentId: string | null;
  tournamentName: string | null;
  winRate: number;
  wins: number;
}

export interface PickedHeroMetric {
  dotaHeroId: number | null;
  heroId: string;
  iconUrl: string | null;
  imageUrl: string | null;
  localizedName: string;
  picks: number;
}

export interface TournamentAnalyticsMetric {
  assists: number;
  avgDurationSeconds: number | null;
  avgKda: number;
  avgKillsPerGame: number;
  deaths: number;
  gamesPlayed: number;
  heroesPickedCount: number;
  kills: number;
  mostPickedHeroes: PickedHeroMetric[];
  playersCount: number;
  teamsCount: number;
  tournamentId: string;
  tournamentName: string;
}

export interface AnalyticsRefreshResult {
  completedAt: string | null;
  durationMs: number | null;
  message: string;
  reason: string;
  requestedAt: string | null;
  status: string;
}

export interface AnalyticsSnapshot {
  heroes: HeroAnalyticsMetric[];
  players: PlayerAnalyticsMetric[];
  teams: TeamAnalyticsMetric[];
  tournaments: TournamentAnalyticsMetric[];
}

export interface AnalyticsMatchHistory {
  dotaMatchId: string | null;
  matchGameId: string | null;
  matchId: string | null;
}

export interface RoleAnalyticsTeam {
  captainNickname: string | null;
  captainProfileId: string | null;
  id: string;
  name: string;
  slug: string | null;
  tag: string | null;
}

export interface PlayerAnalyticsResponse {
  heroPerformance: HeroAnalyticsMetric[];
  matchHistory: AnalyticsMatchHistory[];
  metrics: PlayerAnalyticsMetric[];
}

export interface CurrentTeamAnalyticsResponse {
  recentTeamMatches: AnalyticsMatchHistory[];
  rosterPerformance: PlayerAnalyticsMetric[];
  team: RoleAnalyticsTeam | null;
  teamSummary: TeamAnalyticsMetric[];
}

export interface OrganizerAnalyticsResponse {
  activePublishedTournaments: number;
  approvedRegistrations: number;
  importJobs: number;
  pendingRegistrations: number;
  processedMatchGames: number;
  tournaments: number;
}

export interface RecentImportMetric {
  completedAt: string | null;
  createdAt: string | null;
  dotaMatchId: string | null;
  errorCode: string | null;
  id: string;
  status: string;
}

export interface OrganizerTournamentAnalyticsResponse {
  avgDurationSeconds: number | null;
  gamesProcessed: number;
  heroMetrics: HeroAnalyticsMetric[];
  importCoveragePercent: number;
  matchesWithoutImport: number;
  recentImports: RecentImportMetric[];
  teamComparison: TeamAnalyticsMetric[];
  topTeams: TeamAnalyticsMetric[];
  tournamentId: string;
  tournamentSummary: TournamentAnalyticsMetric | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function arrayPayload(value: unknown) {
  if (Array.isArray(value)) {
    return value;
  }

  if (isRecord(value) && Array.isArray(value.content)) {
    return value.content;
  }

  if (isRecord(value) && Array.isArray(value.items)) {
    return value.items;
  }

  return null;
}

function analyticsArrayPayload(value: unknown, label: string) {
  const payload = arrayPayload(value);

  if (!payload) {
    throw new Error(`Analytics ${label} response has an invalid format.`);
  }

  return payload;
}

function text(value: unknown, fallback = "No data") {
  return typeof value === "string" && value.trim() ? value : fallback;
}

function nullableText(value: unknown) {
  return typeof value === "string" && value.trim() ? value : null;
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);

    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return 0;
}

function nullableNumber(value: unknown) {
  if (value === null || value === undefined) {
    return null;
  }

  const parsed = numberValue(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function queryString(filters?: AnalyticsFilters) {
  const params = new URLSearchParams();

  if (filters?.tournamentId) {
    params.set("tournamentId", filters.tournamentId);
  }

  if (filters?.teamId) {
    params.set("teamId", filters.teamId);
  }

  if (filters?.profileId) {
    params.set("profileId", filters.profileId);
  }

  if (filters?.heroId) {
    params.set("heroId", filters.heroId);
  }

  if (filters?.limit) {
    params.set("limit", String(filters.limit));
  }

  const value = params.toString();
  return value ? `?${value}` : "";
}

function mapPlayer(value: unknown): PlayerAnalyticsMetric {
  const item = isRecord(value) ? value : {};

  return {
    assists: numberValue(item.assists),
    avgAssists: numberValue(item.avgAssists),
    avgDeaths: numberValue(item.avgDeaths),
    avgGpm: numberValue(item.avgGpm),
    avgHeroDamage: numberValue(item.avgHeroDamage),
    avgKills: numberValue(item.avgKills),
    avgXpm: numberValue(item.avgXpm),
    deaths: numberValue(item.deaths),
    displayName: text(item.displayName, "Unknown player"),
    gamesPlayed: numberValue(item.gamesPlayed),
    kda: numberValue(item.kda),
    kills: numberValue(item.kills),
    losses: numberValue(item.losses),
    profileId: text(item.profileId, "unknown-player"),
    teamId: nullableText(item.teamId),
    teamName: nullableText(item.teamName),
    tournamentId: nullableText(item.tournamentId),
    tournamentName: nullableText(item.tournamentName),
    winRate: numberValue(item.winRate),
    wins: numberValue(item.wins)
  };
}

function mapTeam(value: unknown): TeamAnalyticsMetric {
  const item = isRecord(value) ? value : {};

  return {
    assists: numberValue(item.totalAssists),
    avgAssists: numberValue(item.avgAssists),
    avgDeaths: numberValue(item.avgDeaths),
    avgGpm: numberValue(item.avgGpm),
    avgHeroDamage: numberValue(item.avgHeroDamage),
    avgKda: numberValue(item.avgKda),
    avgKills: numberValue(item.avgKills),
    avgXpm: numberValue(item.avgXpm),
    deaths: numberValue(item.totalDeaths),
    gamesPlayed: numberValue(item.gamesPlayed),
    kills: numberValue(item.totalKills),
    losses: numberValue(item.losses),
    teamId: text(item.teamId, "unknown-team"),
    teamName: text(item.teamName, "Unknown team"),
    tournamentId: nullableText(item.tournamentId),
    tournamentName: nullableText(item.tournamentName),
    winRate: numberValue(item.winRate),
    wins: numberValue(item.wins)
  };
}

function mapHero(value: unknown): HeroAnalyticsMetric {
  const item = isRecord(value) ? value : {};

  return {
    assists: numberValue(item.totalAssists),
    avgAssists: numberValue(item.avgAssists),
    avgDeaths: numberValue(item.avgDeaths),
    avgGpm: numberValue(item.avgGpm),
    avgHeroDamage: numberValue(item.avgHeroDamage),
    avgKills: numberValue(item.avgKills),
    avgXpm: numberValue(item.avgXpm),
    dotaHeroId: nullableNumber(item.dotaHeroId),
    gamesPlayed: numberValue(item.gamesPlayed),
    heroId: text(item.heroId, "unknown-hero"),
    iconUrl: nullableText(item.iconUrl),
    imageUrl: nullableText(item.imageUrl),
    kda: numberValue(item.kda),
    kills: numberValue(item.totalKills),
    localizedName: text(item.localizedName, text(item.name, "Unknown hero")),
    losses: numberValue(item.losses),
    name: text(item.name, "unknown_hero"),
    tournamentId: nullableText(item.tournamentId),
    tournamentName: nullableText(item.tournamentName),
    winRate: numberValue(item.winRate),
    wins: numberValue(item.wins)
  };
}

function mapPickedHero(value: unknown): PickedHeroMetric {
  const item = isRecord(value) ? value : {};

  return {
    dotaHeroId: nullableNumber(item.dotaHeroId),
    heroId: text(item.heroId, "unknown-hero"),
    iconUrl: nullableText(item.iconUrl),
    imageUrl: nullableText(item.imageUrl),
    localizedName: text(item.localizedName, "Unknown hero"),
    picks: numberValue(item.picks)
  };
}

function mapTournament(value: unknown): TournamentAnalyticsMetric {
  const item = isRecord(value) ? value : {};

  return {
    assists: numberValue(item.totalAssists),
    avgDurationSeconds: nullableNumber(item.avgDurationSeconds),
    avgKda: numberValue(item.avgKda),
    avgKillsPerGame: numberValue(item.avgKillsPerGame),
    deaths: numberValue(item.totalDeaths),
    gamesPlayed: numberValue(item.gamesPlayed),
    heroesPickedCount: numberValue(item.heroesPickedCount),
    kills: numberValue(item.totalKills),
    mostPickedHeroes: (arrayPayload(item.mostPickedHeroes) ?? []).map(mapPickedHero),
    playersCount: numberValue(item.playersCount),
    teamsCount: numberValue(item.teamsCount),
    tournamentId: text(item.tournamentId, "unknown-tournament"),
    tournamentName: text(item.tournamentName, "Unknown tournament")
  };
}

function mapRefresh(value: unknown): AnalyticsRefreshResult {
  const item = isRecord(value) ? value : {};

  return {
    completedAt: nullableText(item.completedAt),
    durationMs: nullableNumber(item.durationMs),
    message: text(item.message, "Analytics refresh request completed."),
    reason: text(item.reason, "admin request"),
    requestedAt: nullableText(item.requestedAt),
    status: text(item.status, "UNKNOWN")
  };
}

function mapMatchHistory(value: unknown): AnalyticsMatchHistory {
  const item = isRecord(value) ? value : {};

  return {
    dotaMatchId: nullableText(item.dotaMatchId),
    matchGameId: nullableText(item.matchGameId),
    matchId: nullableText(item.matchId)
  };
}

function mapRoleAnalyticsTeam(value: unknown): RoleAnalyticsTeam | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    captainNickname: nullableText(value.captainNickname),
    captainProfileId: nullableText(value.captainProfileId),
    id: text(value.id, "unknown-team"),
    name: text(value.name, "Unknown team"),
    slug: nullableText(value.slug),
    tag: nullableText(value.tag)
  };
}

function mapPlayerAnalyticsResponse(value: unknown): PlayerAnalyticsResponse {
  const item = isRecord(value) ? value : {};

  return {
    heroPerformance: (arrayPayload(item.heroPerformance) ?? []).map(mapHero),
    matchHistory: (arrayPayload(item.matchHistory) ?? []).map(mapMatchHistory),
    metrics: (arrayPayload(item.metrics) ?? []).map(mapPlayer)
  };
}

function mapCurrentTeamAnalyticsResponse(value: unknown): CurrentTeamAnalyticsResponse {
  const item = isRecord(value) ? value : {};

  return {
    recentTeamMatches: (arrayPayload(item.recentTeamMatches) ?? []).map(mapMatchHistory),
    rosterPerformance: (arrayPayload(item.rosterPerformance) ?? []).map(mapPlayer),
    team: mapRoleAnalyticsTeam(item.team),
    teamSummary: (arrayPayload(item.teamSummary) ?? []).map(mapTeam)
  };
}

function mapOrganizerAnalyticsResponse(value: unknown): OrganizerAnalyticsResponse {
  const item = isRecord(value) ? value : {};

  return {
    activePublishedTournaments: numberValue(item.activePublishedTournaments),
    approvedRegistrations: numberValue(item.approvedRegistrations),
    importJobs: numberValue(item.importJobs),
    pendingRegistrations: numberValue(item.pendingRegistrations),
    processedMatchGames: numberValue(item.processedMatchGames),
    tournaments: numberValue(item.tournaments)
  };
}

function mapRecentImport(value: unknown): RecentImportMetric {
  const item = isRecord(value) ? value : {};

  return {
    completedAt: nullableText(item.completedAt),
    createdAt: nullableText(item.createdAt),
    dotaMatchId: nullableText(item.dotaMatchId),
    errorCode: nullableText(item.errorCode),
    id: text(item.id, "unknown-import"),
    status: text(item.status, "unknown")
  };
}

function mapOrganizerTournamentAnalyticsResponse(value: unknown): OrganizerTournamentAnalyticsResponse {
  const item = isRecord(value) ? value : {};

  return {
    avgDurationSeconds: nullableNumber(item.avgDurationSeconds),
    gamesProcessed: numberValue(item.gamesProcessed),
    heroMetrics: (arrayPayload(item.heroMetrics) ?? []).map(mapHero),
    importCoveragePercent: numberValue(item.importCoveragePercent),
    matchesWithoutImport: numberValue(item.matchesWithoutImport),
    recentImports: (arrayPayload(item.recentImports) ?? []).map(mapRecentImport),
    teamComparison: (arrayPayload(item.teamComparison) ?? []).map(mapTeam),
    topTeams: (arrayPayload(item.topTeams) ?? []).map(mapTeam),
    tournamentId: text(item.tournamentId, "unknown-tournament"),
    tournamentSummary: isRecord(item.tournamentSummary) ? mapTournament(item.tournamentSummary) : null
  };
}

export async function getPublicPlayerMetrics(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApi<unknown>(`/public/analytics/players${queryString(filters)}`),
    "players"
  ).map(mapPlayer);
}

export async function getPublicTeamMetrics(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApi<unknown>(`/public/analytics/teams${queryString(filters)}`),
    "teams"
  ).map(mapTeam);
}

export async function getPublicHeroMetrics(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApi<unknown>(`/public/analytics/heroes${queryString(filters)}`),
    "heroes"
  ).map(mapHero);
}

export async function getPublicTournamentMetrics(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApi<unknown>(`/public/analytics/tournaments${queryString(filters)}`),
    "tournaments"
  ).map(mapTournament);
}

export async function getPublicTournamentAnalytics(tournamentId: string) {
  return mapTournament(
    await getApi<unknown>(`/public/analytics/tournaments/${tournamentId}`)
  );
}

export async function getPublicAnalyticsSnapshot(filters?: AnalyticsFilters): Promise<AnalyticsSnapshot> {
  const [players, teams, heroes, tournaments] = await Promise.all([
    getPublicPlayerMetrics(filters),
    getPublicTeamMetrics(filters),
    getPublicHeroMetrics(filters),
    getPublicTournamentMetrics(filters)
  ]);

  return {
    heroes,
    players,
    teams,
    tournaments
  };
}

export async function getMyPlayerAnalytics() {
  return mapPlayerAnalyticsResponse(await getApiAuthenticated<unknown>("/me/analytics"));
}

export async function getMyTeamAnalytics() {
  return mapCurrentTeamAnalyticsResponse(await getApiAuthenticated<unknown>("/me/team/analytics"));
}

export async function getOrganizerAnalytics() {
  return mapOrganizerAnalyticsResponse(await getApiAuthenticated<unknown>("/organizer/analytics"));
}

export async function getOrganizerTournamentAnalytics(tournamentId: string) {
  return mapOrganizerTournamentAnalyticsResponse(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/analytics`)
  );
}

export async function refreshAnalyticsAdmin() {
  return mapRefresh(await postApiAuthenticated<unknown>("/admin/analytics/refresh", undefined));
}
