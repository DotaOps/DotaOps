import { getApi, getApiAuthenticated, postApiAuthenticated } from "@/lib/api";

export interface AnalyticsFilters {
  from?: string;
  heroId?: string;
  limit?: number;
  profileId?: string;
  teamId?: string;
  to?: string;
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
  playedAt: string | null;
  teamAId: string | null;
  teamAName: string | null;
  teamBId: string | null;
  teamBName: string | null;
  tournamentId: string | null;
  tournamentName: string | null;
  winnerTeamId: string | null;
}

export interface PlayerProgressPoint {
  assists: number;
  deaths: number;
  denies: number | null;
  direScore: number | null;
  dotaHeroId: number | null;
  dotaMatchId: string | null;
  durationSeconds: number | null;
  goldPerMin: number | null;
  heroDamage: number | null;
  heroHealing: number | null;
  heroId: string | null;
  heroName: string | null;
  kda: number;
  kills: number;
  lastHits: number | null;
  level: number | null;
  matchGameId: string | null;
  matchId: string | null;
  netWorth: number | null;
  playedAt: string | null;
  radiantScore: number | null;
  teamSide: string | null;
  towerDamage: number | null;
  won: boolean | null;
  winnerSide: string | null;
  xpPerMin: number | null;
}

export interface PlayerHeroPerformance {
  avgAssists: number;
  avgDeaths: number;
  avgDenies: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgHeroHealing: number;
  avgKda: number;
  avgKills: number;
  avgLastHits: number;
  avgTowerDamage: number;
  avgXpm: number;
  bestDotaMatchId: string | null;
  bestKda: number;
  bestMatchGameId: string | null;
  bestMatchId: string | null;
  bestPlayedAt: string | null;
  dotaHeroId: number | null;
  heroId: string | null;
  heroName: string | null;
  losses: number;
  matches: number;
  recentDotaMatchId: string | null;
  recentMatchGameId: string | null;
  recentMatchId: string | null;
  recentPlayedAt: string | null;
  winRate: number;
  wins: number;
}

export type PlayerInsightCategory = "INFO" | "WARNING" | "POSITIVE";
export type ContextWeightClassification = "NORMAL" | "ROUGH_GAME" | "STOMP_LOSS" | "LOW_CONFIDENCE";
export type ContextWeightReason =
  | "HIGH_DEATHS"
  | "LOW_KDA"
  | "LOW_OBJECTIVE_PRESSURE"
  | "TEAM_SCORE_DISADVANTAGE"
  | "STOMP_LOSS_CONTEXT"
  | "LOW_NET_WORTH_OR_LEVEL"
  | "SUPPORT_IMPACT_PROTECTED"
  | "INSUFFICIENT_BASELINE";

export interface PlayerInsightContextWeight {
  classification: ContextWeightClassification;
  message: string;
  reasons: ContextWeightReason[];
  weight: number;
}

export interface PlayerInsight {
  category: PlayerInsightCategory;
  comparisonValue: number | null;
  contextWeight: PlayerInsightContextWeight | null;
  currentValue: number;
  description: string;
  evidence: string;
  metricName: string;
  sampleSize: number;
  title: string;
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
  heroDetails: PlayerHeroPerformance[];
  heroPerformance: HeroAnalyticsMetric[];
  insights: PlayerInsight[];
  matchHistory: AnalyticsMatchHistory[];
  metrics: PlayerAnalyticsMetric[];
  progress: PlayerProgressPoint[];
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

export interface OrganizerTournamentLookup {
  status: string;
  title: string;
  tournamentId: string;
}

export interface TeamLookup {
  name: string;
  tag: string | null;
  teamId: string;
}

export interface TeamPlayerLookup {
  displayName: string;
  nickname: string;
  profileId: string;
  teamId: string;
  teamName: string;
}

export interface PlayerComparisonCandidate {
  analyticsGamesCount: number;
  avatarUrl: string | null;
  displayName: string;
  hasAnalyticsData: boolean;
  label: string | null;
  nickname: string | null;
  opendotaAccountId: number | null;
  profileId: string;
  teamId: string | null;
  teamName: string | null;
}

export interface PlayerComparisonLookupResponse {
  ambiguous: boolean;
  candidates: PlayerComparisonCandidate[];
  exactMatch: boolean;
  query: string;
}

export interface HeroLookup {
  dotaHeroId: number | null;
  heroId: string;
  iconUrl: string | null;
  imageUrl: string | null;
  localizedName: string;
  name: string;
}

export interface AnalyticsComparisonFiltersResponse {
  accessScope: "protected" | "public" | string;
  from: string | null;
  heroId: string | null;
  limit: number;
  profileId: string | null;
  teamId: string | null;
  to: string | null;
  tournamentId: string | null;
}

export interface TeamComparisonResponse {
  filters: AnalyticsComparisonFiltersResponse;
  heroMetrics: HeroAnalyticsMetric[];
  recentMatches: AnalyticsMatchHistory[];
  teamA: TeamAnalyticsMetric | null;
  teamAId: string;
  teamB: TeamAnalyticsMetric | null;
  teamBId: string;
  teams: TeamAnalyticsMetric[];
}

export interface PlayerComparisonMetric {
  avgAssists: number;
  avgDeaths: number;
  avgDenies: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgHeroHealing: number;
  avgKills: number;
  avgLastHits: number;
  avgNetWorth: number;
  avgTowerDamage: number;
  avgXpm: number;
  displayName: string;
  gamesPlayed: number;
  kda: number;
  losses: number;
  profileId: string;
  winRate: number;
  wins: number;
}

export interface PlayerComparisonMetricDelta {
  avgAssists: number | null;
  avgDeaths: number | null;
  avgDenies: number | null;
  avgGpm: number | null;
  avgHeroDamage: number | null;
  avgHeroHealing: number | null;
  avgKills: number | null;
  avgLastHits: number | null;
  avgNetWorth: number | null;
  avgTowerDamage: number | null;
  avgXpm: number | null;
  gamesPlayed: number | null;
  kda: number | null;
  losses: number | null;
  winRate: number | null;
  wins: number | null;
}

export interface PlayerComparisonHeadline {
  delta: PlayerComparisonMetricDelta | null;
  profileA: PlayerComparisonMetric | null;
  profileB: PlayerComparisonMetric | null;
}

export interface PlayerComparisonHeroStats {
  avgAssists: number;
  avgDeaths: number;
  avgGpm: number;
  avgHeroDamage: number;
  avgHeroHealing: number;
  avgKills: number;
  avgTowerDamage: number;
  avgXpm: number;
  gamesPlayed: number;
  kda: number;
  losses: number;
  profileId: string;
  winRate: number;
  wins: number;
}

export interface PlayerComparisonHeroDelta {
  avgDeaths: number | null;
  avgGpm: number | null;
  avgHeroDamage: number | null;
  avgTowerDamage: number | null;
  avgXpm: number | null;
  gamesPlayed: number | null;
  kda: number | null;
  winRate: number | null;
}

export interface PlayerComparisonSharedHero {
  delta: PlayerComparisonHeroDelta | null;
  dotaHeroId: number | null;
  heroId: string | null;
  heroName: string | null;
  profileA: PlayerComparisonHeroStats | null;
  profileB: PlayerComparisonHeroStats | null;
}

export interface PlayerComparisonMatchPlayer {
  assists: number;
  deaths: number;
  denies: number | null;
  dotaHeroId: number | null;
  goldPerMin: number | null;
  heroDamage: number | null;
  heroHealing: number | null;
  heroId: string | null;
  heroName: string | null;
  kda: number;
  kills: number;
  lastHits: number | null;
  netWorth: number | null;
  profileId: string;
  teamId: string | null;
  teamName: string | null;
  teamSide: string | null;
  towerDamage: number | null;
  won: boolean | null;
  xpPerMin: number | null;
}

export interface PlayerComparisonMatch {
  dotaMatchId: string | null;
  matchGameId: string | null;
  matchId: string | null;
  playedAt: string | null;
  profileA: PlayerComparisonMatchPlayer | null;
  profileB: PlayerComparisonMatchPlayer | null;
  teamAId: string | null;
  teamAName: string | null;
  teamBId: string | null;
  teamBName: string | null;
  tournamentId: string | null;
  tournamentName: string | null;
  winnerSide: string | null;
  winnerTeamId: string | null;
}

export interface PlayerComparisonWarning {
  code: string;
  heroId: string | null;
  message: string;
  metricName: string;
  profileId: string | null;
  recommendedMinimum: number;
  sampleSize: number;
  severity: string;
}

export interface PlayerComparisonResponse {
  enrichedMatchHistory: PlayerComparisonMatch[];
  filters: AnalyticsComparisonFiltersResponse;
  headlineComparison: PlayerComparisonHeadline | null;
  playerA: PlayerAnalyticsMetric | null;
  playerB: PlayerAnalyticsMetric | null;
  players: PlayerAnalyticsMetric[];
  profileAId: string;
  profileAHeroDetails: PlayerHeroPerformance[];
  profileAHeroPerformance: HeroAnalyticsMetric[];
  profileBHeroDetails: PlayerHeroPerformance[];
  profileBHeroPerformance: HeroAnalyticsMetric[];
  profileBId: string;
  recentMatches: AnalyticsMatchHistory[];
  sharedHeroComparisons: PlayerComparisonSharedHero[];
  sharedHeroes: HeroAnalyticsMetric[];
  warnings: PlayerComparisonWarning[];
}

export interface CompareAnalyticsTeamsInput {
  filters?: AnalyticsFilters;
  teamAId: string;
  teamBId: string;
}

export interface CompareAnalyticsPlayersInput {
  filters?: AnalyticsFilters;
  profileAId: string;
  profileBId: string;
}

export interface LookupPlayerComparisonCandidatesInput {
  filters?: AnalyticsFilters;
  q?: string;
  query?: string;
  search?: string;
}

function playerComparisonSearchParam({ q, query }: LookupPlayerComparisonCandidatesInput) {
  if (query) {
    return "query";
  }

  if (q) {
    return "q";
  }

  return "search";
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

function nullableBoolean(value: unknown) {
  return typeof value === "boolean" ? value : null;
}

function playerInsightCategory(value: unknown): PlayerInsightCategory {
  if (value === "WARNING" || value === "POSITIVE" || value === "INFO") {
    return value;
  }

  return "INFO";
}

function contextWeightClassification(value: unknown): ContextWeightClassification {
  if (value === "NORMAL" || value === "ROUGH_GAME" || value === "STOMP_LOSS" || value === "LOW_CONFIDENCE") {
    return value;
  }

  return "LOW_CONFIDENCE";
}

function contextWeightReason(value: unknown): ContextWeightReason {
  if (
    value === "HIGH_DEATHS" ||
    value === "LOW_KDA" ||
    value === "LOW_OBJECTIVE_PRESSURE" ||
    value === "TEAM_SCORE_DISADVANTAGE" ||
    value === "STOMP_LOSS_CONTEXT" ||
    value === "LOW_NET_WORTH_OR_LEVEL" ||
    value === "SUPPORT_IMPACT_PROTECTED" ||
    value === "INSUFFICIENT_BASELINE"
  ) {
    return value;
  }

  return "INSUFFICIENT_BASELINE";
}

function setStringParam(params: URLSearchParams, key: string, value?: string | null) {
  const cleanValue = value?.trim();

  if (cleanValue) {
    params.set(key, cleanValue);
  }
}

function setLimitParam(params: URLSearchParams, value?: number | null) {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    params.set("limit", String(value));
  }
}

function queryString(filters?: AnalyticsFilters, options?: { omitTournamentId?: boolean }) {
  const params = new URLSearchParams();

  if (!options?.omitTournamentId) {
    setStringParam(params, "tournamentId", filters?.tournamentId);
  }

  setStringParam(params, "teamId", filters?.teamId);
  setStringParam(params, "profileId", filters?.profileId);
  setStringParam(params, "heroId", filters?.heroId);
  setStringParam(params, "from", filters?.from);
  setStringParam(params, "to", filters?.to);
  setLimitParam(params, filters?.limit);

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
    matchId: nullableText(item.matchId),
    playedAt: nullableText(item.playedAt),
    teamAId: nullableText(item.teamAId),
    teamAName: nullableText(item.teamAName),
    teamBId: nullableText(item.teamBId),
    teamBName: nullableText(item.teamBName),
    tournamentId: nullableText(item.tournamentId),
    tournamentName: nullableText(item.tournamentName),
    winnerTeamId: nullableText(item.winnerTeamId)
  };
}

function mapPlayerProgressPoint(value: unknown): PlayerProgressPoint {
  const item = isRecord(value) ? value : {};

  return {
    assists: numberValue(item.assists),
    deaths: numberValue(item.deaths),
    denies: nullableNumber(item.denies),
    direScore: nullableNumber(item.direScore),
    dotaHeroId: nullableNumber(item.dotaHeroId),
    dotaMatchId: nullableText(item.dotaMatchId),
    durationSeconds: nullableNumber(item.durationSeconds),
    goldPerMin: nullableNumber(item.goldPerMin),
    heroDamage: nullableNumber(item.heroDamage),
    heroHealing: nullableNumber(item.heroHealing),
    heroId: nullableText(item.heroId),
    heroName: nullableText(item.heroName),
    kda: numberValue(item.kda),
    kills: numberValue(item.kills),
    lastHits: nullableNumber(item.lastHits),
    level: nullableNumber(item.level),
    matchGameId: nullableText(item.matchGameId),
    matchId: nullableText(item.matchId),
    netWorth: nullableNumber(item.netWorth),
    playedAt: nullableText(item.playedAt),
    radiantScore: nullableNumber(item.radiantScore),
    teamSide: nullableText(item.teamSide),
    towerDamage: nullableNumber(item.towerDamage),
    won: nullableBoolean(item.won),
    winnerSide: nullableText(item.winnerSide),
    xpPerMin: nullableNumber(item.xpPerMin)
  };
}

function mapPlayerHeroPerformance(value: unknown): PlayerHeroPerformance {
  const item = isRecord(value) ? value : {};

  return {
    avgAssists: numberValue(item.avgAssists),
    avgDeaths: numberValue(item.avgDeaths),
    avgDenies: numberValue(item.avgDenies),
    avgGpm: numberValue(item.avgGpm),
    avgHeroDamage: numberValue(item.avgHeroDamage),
    avgHeroHealing: numberValue(item.avgHeroHealing),
    avgKda: numberValue(item.avgKda),
    avgKills: numberValue(item.avgKills),
    avgLastHits: numberValue(item.avgLastHits),
    avgTowerDamage: numberValue(item.avgTowerDamage),
    avgXpm: numberValue(item.avgXpm),
    bestDotaMatchId: nullableText(item.bestDotaMatchId),
    bestKda: numberValue(item.bestKda),
    bestMatchGameId: nullableText(item.bestMatchGameId),
    bestMatchId: nullableText(item.bestMatchId),
    bestPlayedAt: nullableText(item.bestPlayedAt),
    dotaHeroId: nullableNumber(item.dotaHeroId),
    heroId: nullableText(item.heroId),
    heroName: nullableText(item.heroName),
    losses: numberValue(item.losses),
    matches: numberValue(item.matches),
    recentDotaMatchId: nullableText(item.recentDotaMatchId),
    recentMatchGameId: nullableText(item.recentMatchGameId),
    recentMatchId: nullableText(item.recentMatchId),
    recentPlayedAt: nullableText(item.recentPlayedAt),
    winRate: numberValue(item.winRate),
    wins: numberValue(item.wins)
  };
}

function mapPlayerInsight(value: unknown): PlayerInsight {
  const item = isRecord(value) ? value : {};

  return {
    category: playerInsightCategory(item.category),
    comparisonValue: nullableNumber(item.comparisonValue),
    contextWeight: mapPlayerInsightContextWeight(item.contextWeight),
    currentValue: numberValue(item.currentValue),
    description: text(item.description, "Insight based on recent match data."),
    evidence: text(item.evidence, "No evidence details"),
    metricName: text(item.metricName, "metric"),
    sampleSize: numberValue(item.sampleSize),
    title: text(item.title, "Analytics insight")
  };
}

function mapPlayerInsightContextWeight(value: unknown): PlayerInsightContextWeight | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    classification: contextWeightClassification(value.classification),
    message: text(value.message, "No context weighting details"),
    reasons: (arrayPayload(value.reasons) ?? []).map(contextWeightReason),
    weight: nullableNumber(value.weight) ?? 1
  };
}

function mapOrganizerTournamentLookup(value: unknown): OrganizerTournamentLookup {
  const item = isRecord(value) ? value : {};

  return {
    status: text(item.status, "unknown"),
    title: text(item.title, "Unknown tournament"),
    tournamentId: text(item.tournamentId, "unknown-tournament")
  };
}

function mapTeamLookup(value: unknown): TeamLookup {
  const item = isRecord(value) ? value : {};

  return {
    name: text(item.name, "Unknown team"),
    tag: nullableText(item.tag),
    teamId: text(item.teamId, "unknown-team")
  };
}

function mapTeamPlayerLookup(value: unknown): TeamPlayerLookup {
  const item = isRecord(value) ? value : {};

  return {
    displayName: text(item.displayName, "Unknown player"),
    nickname: text(item.nickname, "Unknown player"),
    profileId: text(item.profileId, "unknown-player"),
    teamId: text(item.teamId, "unknown-team"),
    teamName: text(item.teamName, "Unknown team")
  };
}

function mapPlayerComparisonCandidate(value: unknown): PlayerComparisonCandidate {
  const item = isRecord(value) ? value : {};

  return {
    analyticsGamesCount: numberValue(item.analyticsGamesCount),
    avatarUrl: nullableText(item.avatarUrl),
    displayName: text(item.displayName, "Unknown player"),
    hasAnalyticsData: Boolean(item.hasAnalyticsData),
    label: nullableText(item.label),
    nickname: nullableText(item.nickname),
    opendotaAccountId: nullableNumber(item.opendotaAccountId),
    profileId: text(item.profileId, "unknown-player"),
    teamId: nullableText(item.teamId),
    teamName: nullableText(item.teamName)
  };
}

function mapPlayerComparisonLookupResponse(value: unknown): PlayerComparisonLookupResponse {
  const item = isRecord(value) ? value : {};

  return {
    ambiguous: Boolean(item.ambiguous),
    candidates: (arrayPayload(item.candidates) ?? []).map(mapPlayerComparisonCandidate),
    exactMatch: Boolean(item.exactMatch),
    query: text(item.query, "")
  };
}

function mapHeroLookup(value: unknown): HeroLookup {
  const item = isRecord(value) ? value : {};

  return {
    dotaHeroId: nullableNumber(item.dotaHeroId),
    heroId: text(item.heroId, "unknown-hero"),
    iconUrl: nullableText(item.iconUrl),
    imageUrl: nullableText(item.imageUrl),
    localizedName: text(item.localizedName, text(item.name, "Unknown hero")),
    name: text(item.name, "unknown_hero")
  };
}

function mapComparisonFilters(value: unknown): AnalyticsComparisonFiltersResponse {
  const item = isRecord(value) ? value : {};

  return {
    accessScope: text(item.accessScope, "public"),
    from: nullableText(item.from),
    heroId: nullableText(item.heroId),
    limit: numberValue(item.limit),
    profileId: nullableText(item.profileId),
    teamId: nullableText(item.teamId),
    to: nullableText(item.to),
    tournamentId: nullableText(item.tournamentId)
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
    heroDetails: (arrayPayload(item.heroDetails) ?? []).map(mapPlayerHeroPerformance),
    heroPerformance: (arrayPayload(item.heroPerformance) ?? []).map(mapHero),
    insights: (arrayPayload(item.insights) ?? []).map(mapPlayerInsight),
    matchHistory: (arrayPayload(item.matchHistory) ?? []).map(mapMatchHistory),
    metrics: (arrayPayload(item.metrics) ?? []).map(mapPlayer),
    progress: (arrayPayload(item.progress) ?? []).map(mapPlayerProgressPoint)
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

function mapTeamComparisonResponse(value: unknown): TeamComparisonResponse {
  const item = isRecord(value) ? value : {};

  return {
    filters: mapComparisonFilters(item.filters),
    heroMetrics: (arrayPayload(item.heroMetrics) ?? []).map(mapHero),
    recentMatches: (arrayPayload(item.recentMatches) ?? []).map(mapMatchHistory),
    teamA: isRecord(item.teamA) ? mapTeam(item.teamA) : null,
    teamAId: text(item.teamAId, "unknown-team-a"),
    teamB: isRecord(item.teamB) ? mapTeam(item.teamB) : null,
    teamBId: text(item.teamBId, "unknown-team-b"),
    teams: (arrayPayload(item.teams) ?? []).map(mapTeam)
  };
}

function mapPlayerComparisonMetric(value: unknown): PlayerComparisonMetric | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    avgAssists: numberValue(value.avgAssists),
    avgDeaths: numberValue(value.avgDeaths),
    avgDenies: numberValue(value.avgDenies),
    avgGpm: numberValue(value.avgGpm),
    avgHeroDamage: numberValue(value.avgHeroDamage),
    avgHeroHealing: numberValue(value.avgHeroHealing),
    avgKills: numberValue(value.avgKills),
    avgLastHits: numberValue(value.avgLastHits),
    avgNetWorth: numberValue(value.avgNetWorth),
    avgTowerDamage: numberValue(value.avgTowerDamage),
    avgXpm: numberValue(value.avgXpm),
    displayName: text(value.displayName, "Unknown player"),
    gamesPlayed: numberValue(value.gamesPlayed),
    kda: numberValue(value.kda),
    losses: numberValue(value.losses),
    profileId: text(value.profileId, "unknown-player"),
    winRate: numberValue(value.winRate),
    wins: numberValue(value.wins)
  };
}

function mapPlayerComparisonMetricDelta(value: unknown): PlayerComparisonMetricDelta | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    avgAssists: nullableNumber(value.avgAssists),
    avgDeaths: nullableNumber(value.avgDeaths),
    avgDenies: nullableNumber(value.avgDenies),
    avgGpm: nullableNumber(value.avgGpm),
    avgHeroDamage: nullableNumber(value.avgHeroDamage),
    avgHeroHealing: nullableNumber(value.avgHeroHealing),
    avgKills: nullableNumber(value.avgKills),
    avgLastHits: nullableNumber(value.avgLastHits),
    avgNetWorth: nullableNumber(value.avgNetWorth),
    avgTowerDamage: nullableNumber(value.avgTowerDamage),
    avgXpm: nullableNumber(value.avgXpm),
    gamesPlayed: nullableNumber(value.gamesPlayed),
    kda: nullableNumber(value.kda),
    losses: nullableNumber(value.losses),
    winRate: nullableNumber(value.winRate),
    wins: nullableNumber(value.wins)
  };
}

function mapPlayerComparisonHeadline(value: unknown): PlayerComparisonHeadline | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    delta: mapPlayerComparisonMetricDelta(value.delta),
    profileA: mapPlayerComparisonMetric(value.profileA),
    profileB: mapPlayerComparisonMetric(value.profileB)
  };
}

function mapPlayerComparisonHeroStats(value: unknown): PlayerComparisonHeroStats | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    avgAssists: numberValue(value.avgAssists),
    avgDeaths: numberValue(value.avgDeaths),
    avgGpm: numberValue(value.avgGpm),
    avgHeroDamage: numberValue(value.avgHeroDamage),
    avgHeroHealing: numberValue(value.avgHeroHealing),
    avgKills: numberValue(value.avgKills),
    avgTowerDamage: numberValue(value.avgTowerDamage),
    avgXpm: numberValue(value.avgXpm),
    gamesPlayed: numberValue(value.gamesPlayed),
    kda: numberValue(value.kda),
    losses: numberValue(value.losses),
    profileId: text(value.profileId, "unknown-player"),
    winRate: numberValue(value.winRate),
    wins: numberValue(value.wins)
  };
}

function mapPlayerComparisonHeroDelta(value: unknown): PlayerComparisonHeroDelta | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    avgDeaths: nullableNumber(value.avgDeaths),
    avgGpm: nullableNumber(value.avgGpm),
    avgHeroDamage: nullableNumber(value.avgHeroDamage),
    avgTowerDamage: nullableNumber(value.avgTowerDamage),
    avgXpm: nullableNumber(value.avgXpm),
    gamesPlayed: nullableNumber(value.gamesPlayed),
    kda: nullableNumber(value.kda),
    winRate: nullableNumber(value.winRate)
  };
}

function mapPlayerComparisonSharedHero(value: unknown): PlayerComparisonSharedHero {
  const item = isRecord(value) ? value : {};

  return {
    delta: mapPlayerComparisonHeroDelta(item.delta),
    dotaHeroId: nullableNumber(item.dotaHeroId),
    heroId: nullableText(item.heroId),
    heroName: nullableText(item.heroName),
    profileA: mapPlayerComparisonHeroStats(item.profileA),
    profileB: mapPlayerComparisonHeroStats(item.profileB)
  };
}

function mapPlayerComparisonMatchPlayer(value: unknown): PlayerComparisonMatchPlayer | null {
  if (!isRecord(value)) {
    return null;
  }

  return {
    assists: numberValue(value.assists),
    deaths: numberValue(value.deaths),
    denies: nullableNumber(value.denies),
    dotaHeroId: nullableNumber(value.dotaHeroId),
    goldPerMin: nullableNumber(value.goldPerMin),
    heroDamage: nullableNumber(value.heroDamage),
    heroHealing: nullableNumber(value.heroHealing),
    heroId: nullableText(value.heroId),
    heroName: nullableText(value.heroName),
    kda: numberValue(value.kda),
    kills: numberValue(value.kills),
    lastHits: nullableNumber(value.lastHits),
    netWorth: nullableNumber(value.netWorth),
    profileId: text(value.profileId, "unknown-player"),
    teamId: nullableText(value.teamId),
    teamName: nullableText(value.teamName),
    teamSide: nullableText(value.teamSide),
    towerDamage: nullableNumber(value.towerDamage),
    won: nullableBoolean(value.won),
    xpPerMin: nullableNumber(value.xpPerMin)
  };
}

function mapPlayerComparisonMatch(value: unknown): PlayerComparisonMatch {
  const item = isRecord(value) ? value : {};

  return {
    dotaMatchId: nullableText(item.dotaMatchId),
    matchGameId: nullableText(item.matchGameId),
    matchId: nullableText(item.matchId),
    playedAt: nullableText(item.playedAt),
    profileA: mapPlayerComparisonMatchPlayer(item.profileA),
    profileB: mapPlayerComparisonMatchPlayer(item.profileB),
    teamAId: nullableText(item.teamAId),
    teamAName: nullableText(item.teamAName),
    teamBId: nullableText(item.teamBId),
    teamBName: nullableText(item.teamBName),
    tournamentId: nullableText(item.tournamentId),
    tournamentName: nullableText(item.tournamentName),
    winnerSide: nullableText(item.winnerSide),
    winnerTeamId: nullableText(item.winnerTeamId)
  };
}

function mapPlayerComparisonWarning(value: unknown): PlayerComparisonWarning {
  const item = isRecord(value) ? value : {};

  return {
    code: text(item.code, "UNKNOWN_WARNING"),
    heroId: nullableText(item.heroId),
    message: text(item.message, "Comparison warning."),
    metricName: text(item.metricName, "comparison"),
    profileId: nullableText(item.profileId),
    recommendedMinimum: numberValue(item.recommendedMinimum),
    sampleSize: numberValue(item.sampleSize),
    severity: text(item.severity, "INFO")
  };
}

function mapPlayerComparisonResponse(value: unknown): PlayerComparisonResponse {
  const item = isRecord(value) ? value : {};

  return {
    enrichedMatchHistory: (arrayPayload(item.enrichedMatchHistory) ?? []).map(mapPlayerComparisonMatch),
    filters: mapComparisonFilters(item.filters),
    headlineComparison: mapPlayerComparisonHeadline(item.headlineComparison),
    playerA: isRecord(item.playerA) ? mapPlayer(item.playerA) : null,
    playerB: isRecord(item.playerB) ? mapPlayer(item.playerB) : null,
    players: (arrayPayload(item.players) ?? []).map(mapPlayer),
    profileAId: text(item.profileAId, "unknown-profile-a"),
    profileAHeroDetails: (arrayPayload(item.profileAHeroDetails) ?? []).map(mapPlayerHeroPerformance),
    profileAHeroPerformance: (arrayPayload(item.profileAHeroPerformance) ?? []).map(mapHero),
    profileBHeroDetails: (arrayPayload(item.profileBHeroDetails) ?? []).map(mapPlayerHeroPerformance),
    profileBHeroPerformance: (arrayPayload(item.profileBHeroPerformance) ?? []).map(mapHero),
    profileBId: text(item.profileBId, "unknown-profile-b"),
    recentMatches: (arrayPayload(item.recentMatches) ?? []).map(mapMatchHistory),
    sharedHeroComparisons: (arrayPayload(item.sharedHeroComparisons) ?? []).map(mapPlayerComparisonSharedHero),
    sharedHeroes: (arrayPayload(item.sharedHeroes) ?? []).map(mapHero),
    warnings: (arrayPayload(item.warnings) ?? []).map(mapPlayerComparisonWarning)
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

export async function getMyPlayerProgress(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/me/analytics/progress${queryString(filters)}`),
    "player progress"
  ).map(mapPlayerProgressPoint);
}

export async function getMyPlayerHeroPerformance(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/me/analytics/heroes${queryString(filters)}`),
    "player hero performance"
  ).map(mapPlayerHeroPerformance);
}

export async function getMyPlayerInsights(filters?: AnalyticsFilters) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/me/analytics/insights${queryString(filters)}`),
    "player insights"
  ).map(mapPlayerInsight);
}

export async function getMyPlayerAnalytics(filters?: AnalyticsFilters) {
  const [analytics, progress, heroDetails, insights] = await Promise.all([
    getApiAuthenticated<unknown>(`/me/analytics${queryString(filters)}`),
    getMyPlayerProgress(filters),
    getMyPlayerHeroPerformance(filters),
    getMyPlayerInsights(filters)
  ]);

  return {
    ...mapPlayerAnalyticsResponse(analytics),
    heroDetails,
    insights,
    progress
  };
}

export async function getMyTeamAnalytics(filters?: AnalyticsFilters) {
  return mapCurrentTeamAnalyticsResponse(await getApiAuthenticated<unknown>(`/me/team/analytics${queryString(filters)}`));
}

export async function getOrganizerAnalytics(filters?: AnalyticsFilters) {
  return mapOrganizerAnalyticsResponse(await getApiAuthenticated<unknown>(`/organizer/analytics${queryString(filters)}`));
}

export async function getOrganizerTournamentAnalytics(tournamentId: string, filters?: AnalyticsFilters) {
  return mapOrganizerTournamentAnalyticsResponse(
    await getApiAuthenticated<unknown>(
      `/organizer/tournaments/${tournamentId}/analytics${queryString(filters, { omitTournamentId: true })}`
    )
  );
}

export async function getOrganizerTournamentLookups(limit?: number) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/organizer/lookups/tournaments${queryString({ limit })}`),
    "organizer tournament lookups"
  ).map(mapOrganizerTournamentLookup);
}

export async function getMyTeamLookups(limit?: number) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/me/lookups/teams${queryString({ limit })}`),
    "team lookups"
  ).map(mapTeamLookup);
}

export async function getTeamPlayerLookups(teamId: string, limit?: number) {
  return analyticsArrayPayload(
    await getApiAuthenticated<unknown>(`/teams/${teamId}/lookups/players${queryString({ limit })}`),
    "team player lookups"
  ).map(mapTeamPlayerLookup);
}

export async function getHeroLookups(limit?: number) {
  return analyticsArrayPayload(
    await getApi<unknown>(`/lookups/heroes${queryString({ limit })}`),
    "hero lookups"
  ).map(mapHeroLookup);
}

export async function compareAnalyticsTeams({ filters, teamAId, teamBId }: CompareAnalyticsTeamsInput) {
  const params = new URLSearchParams();

  setStringParam(params, "teamAId", teamAId);
  setStringParam(params, "teamBId", teamBId);
  setStringParam(params, "tournamentId", filters?.tournamentId);
  setStringParam(params, "teamId", filters?.teamId);
  setStringParam(params, "profileId", filters?.profileId);
  setStringParam(params, "heroId", filters?.heroId);
  setStringParam(params, "from", filters?.from);
  setStringParam(params, "to", filters?.to);
  setLimitParam(params, filters?.limit);

  return mapTeamComparisonResponse(
    await getApiAuthenticated<unknown>(`/analytics/compare/teams?${params.toString()}`)
  );
}

export async function compareAnalyticsPlayers({ filters, profileAId, profileBId }: CompareAnalyticsPlayersInput) {
  const params = new URLSearchParams();

  setStringParam(params, "profileAId", profileAId);
  setStringParam(params, "profileBId", profileBId);
  setStringParam(params, "tournamentId", filters?.tournamentId);
  setStringParam(params, "teamId", filters?.teamId);
  setStringParam(params, "profileId", filters?.profileId);
  setStringParam(params, "heroId", filters?.heroId);
  setStringParam(params, "from", filters?.from);
  setStringParam(params, "to", filters?.to);
  setLimitParam(params, filters?.limit);

  return mapPlayerComparisonResponse(
    await getApiAuthenticated<unknown>(`/analytics/compare/players?${params.toString()}`)
  );
}

export async function lookupPlayerComparisonCandidates({ filters, q, query, search }: LookupPlayerComparisonCandidatesInput) {
  const params = new URLSearchParams();
  const searchValue = query ?? q ?? search;
  const searchParam = playerComparisonSearchParam({ q, query });

  setStringParam(params, searchParam, searchValue);
  setStringParam(params, "tournamentId", filters?.tournamentId);
  setStringParam(params, "teamId", filters?.teamId);
  setStringParam(params, "heroId", filters?.heroId);
  setStringParam(params, "from", filters?.from);
  setStringParam(params, "to", filters?.to);
  setLimitParam(params, filters?.limit);

  return mapPlayerComparisonLookupResponse(
    await getApiAuthenticated<unknown>(`/analytics/compare/players/candidates?${params.toString()}`)
  );
}

export async function refreshAnalyticsAdmin() {
  return mapRefresh(await postApiAuthenticated<unknown>("/admin/analytics/refresh", undefined));
}
