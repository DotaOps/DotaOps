import { fetchApi } from "@/lib/api";
import {
  getPublicHeroMetrics,
  getPublicTeamMetrics
} from "@/lib/analytics-data";
import {
  matches,
  roadmap,
  teams
} from "@/lib/mock-data";
import {
  getPublicTournamentBySlug,
  getPublicTournaments
} from "@/lib/tournament-data";
import type { HeroMetric, Match, Player, RoadmapItem, Team } from "@/lib/types";

export interface AnalyticsData {
  heroMetrics: HeroMetric[];
  teams: Team[];
}

interface BackendTeamResponse {
  id: string;
  name: string;
  tag?: string | null;
  slug: string;
  captainProfileId?: string | null;
  captainNickname?: string | null;
  region?: string | null;
  logoUrl?: string | null;
  description?: string | null;
}

interface BackendTeamMemberResponse {
  id: string;
  profileId: string;
  nickname: string;
  displayName?: string | null;
  role: string;
  active: boolean;
}

const fallbackForm: Team["lastFive"] = ["W", "L", "W", "L", "W"];

function roleLabel(role: string) {
  return role
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

function toPlayer(member: BackendTeamMemberResponse): Player {
  return {
    favoriteHero: "N/A",
    id: member.profileId,
    kda: 0,
    nickname: member.displayName || member.nickname,
    role: roleLabel(member.role),
    winRate: 0
  };
}

function toTeam(team: BackendTeamResponse, members: BackendTeamMemberResponse[]): Team {
  const demoFallback = teams.find(
    (fallbackTeam) => fallbackTeam.id === team.id || fallbackTeam.name === team.name
  );
  const roster = members.filter((member) => member.active).map(toPlayer);

  return {
    captain: team.captainNickname ?? demoFallback?.captain ?? "Unassigned",
    favoriteHeroes: demoFallback?.favoriteHeroes ?? [],
    id: team.id,
    kda: demoFallback?.kda ?? 0,
    lastFive: demoFallback?.lastFive ?? fallbackForm,
    name: team.name,
    region: team.region ?? demoFallback?.region ?? "N/A",
    roster: roster.length > 0 ? roster : demoFallback?.roster ?? [],
    winRate: demoFallback?.winRate ?? 0
  };
}

export async function getTournaments() {
  return getPublicTournaments();
}

export async function getTournamentBySlug(slug: string) {
  return getPublicTournamentBySlug(slug);
}

export async function getTeams() {
  const result = await fetchApi<BackendTeamResponse[]>("/teams", []);

  if (result.source !== "api") {
    return teams;
  }

  return Promise.all(
    result.data.map(async (team) => {
      const membersResult = await fetchApi<BackendTeamMemberResponse[]>(
        `/teams/${team.id}/members`,
        []
      );

      return toTeam(team, membersResult.source === "api" ? membersResult.data : []);
    })
  );
}

export async function getMatches() {
  const result = await fetchApi<Match[]>("/matches", matches);

  return result.data;
}

export async function getAnalytics() {
  const [heroMetrics, teamMetrics] = await Promise.all([
    getPublicHeroMetrics({ limit: 50 }),
    getPublicTeamMetrics({ limit: 50 })
  ]);

  return {
    heroMetrics: heroMetrics.map((hero) => ({
      avgKda: hero.kda,
      hero: hero.localizedName,
      pickRate: hero.gamesPlayed,
      winRate: hero.winRate
    })),
    teams: teamMetrics.map((team) => ({
      captain: "Unavailable",
      favoriteHeroes: [],
      id: team.teamId,
      kda: team.avgKda,
      lastFive: [],
      name: team.teamName,
      region: team.tournamentName ?? "Public analytics",
      roster: [],
      winRate: team.winRate
    }))
  } satisfies AnalyticsData;
}

export async function getRoadmap() {
  const result = await fetchApi<RoadmapItem[]>("/roadmap", roadmap);

  return result.data;
}
