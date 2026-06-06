import { getPublicAnalyticsSnapshot } from "@/lib/analytics-data";
import { getPublicTournaments } from "@/lib/tournament-data";
import type {
  HeroAnalyticsMetric,
  TeamAnalyticsMetric,
  TournamentAnalyticsMetric
} from "@/lib/analytics-data";
import type { Tournament } from "@/lib/types";

export interface PublicHomepageData {
  activeOrPublishedCount: number;
  analyzedMatchesCount: number | null;
  averageDurationSeconds: number | null;
  featuredTournaments: Tournament[];
  hasAnalyticsData: boolean;
  publicTournamentCount: number;
  registrationOpenCount: number;
  topHeroPreview: HeroAnalyticsMetric | null;
  topTeamPreview: TeamAnalyticsMetric | null;
  upcomingTournaments: Tournament[];
}

function timestamp(value: string | null | undefined) {
  if (!value) {
    return Number.POSITIVE_INFINITY;
  }

  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : Number.POSITIVE_INFINITY;
}

function isRegistrationOpen(tournament: Tournament, now: number) {
  if (tournament.status === "registration") {
    return true;
  }

  const opensAt = tournament.registrationOpensAt ? Date.parse(tournament.registrationOpensAt) : null;
  const closesAt = tournament.registrationClosesAt ? Date.parse(tournament.registrationClosesAt) : null;

  if (opensAt === null && closesAt === null) {
    return false;
  }

  const opens = opensAt === null || opensAt <= now;
  const closes = closesAt === null || closesAt >= now;

  return opens && closes;
}

function statusPriority(status: Tournament["status"]) {
  switch (status) {
    case "live":
      return 0;
    case "registration":
      return 1;
    case "published":
      return 2;
    case "finished":
      return 3;
    default:
      return 4;
  }
}

function sortTournamentsForLanding(tournaments: Tournament[]) {
  return [...tournaments].sort((left, right) => {
    const priorityDiff = statusPriority(left.status) - statusPriority(right.status);

    if (priorityDiff !== 0) {
      return priorityDiff;
    }

    const startDiff = timestamp(left.startsAt) - timestamp(right.startsAt);

    if (Number.isFinite(startDiff) && startDiff !== 0) {
      return startDiff;
    }

    return `${left.title}-${left.id}`.localeCompare(`${right.title}-${right.id}`);
  });
}

function averageDuration(tournaments: TournamentAnalyticsMetric[]) {
  const durations = tournaments
    .map((tournament) => tournament.avgDurationSeconds)
    .filter((value): value is number => typeof value === "number" && Number.isFinite(value));

  if (durations.length === 0) {
    return null;
  }

  return Math.round(durations.reduce((total, value) => total + value, 0) / durations.length);
}

export async function getPublicHomepageData(): Promise<PublicHomepageData> {
  const [tournaments, analytics] = await Promise.all([
    getPublicTournaments().catch(() => []),
    getPublicAnalyticsSnapshot({ limit: 10 }).catch(() => ({
      heroes: [],
      players: [],
      teams: [],
      tournaments: []
    }))
  ]);

  const sortedTournaments = sortTournamentsForLanding(tournaments);
  const referenceTime = Date.now();
  const activeOrPublishedCount = tournaments.filter((tournament) =>
    ["registration", "published", "live"].includes(tournament.status)
  ).length;
  const registrationOpenCount = tournaments.filter((tournament) => isRegistrationOpen(tournament, referenceTime)).length;
  const analyzedMatchesCount =
    analytics.tournaments.length > 0
      ? analytics.tournaments.reduce((total, tournament) => total + tournament.gamesPlayed, 0)
      : null;
  const hasAnalyticsData =
    analytics.heroes.length > 0 ||
    analytics.players.length > 0 ||
    analytics.teams.length > 0 ||
    analytics.tournaments.length > 0;

  return {
    activeOrPublishedCount,
    analyzedMatchesCount,
    averageDurationSeconds: averageDuration(analytics.tournaments),
    featuredTournaments: sortedTournaments.slice(0, 6),
    hasAnalyticsData,
    publicTournamentCount: tournaments.length,
    registrationOpenCount,
    topHeroPreview: analytics.heroes[0] ?? null,
    topTeamPreview: analytics.teams[0] ?? null,
    upcomingTournaments: sortedTournaments
      .filter((tournament) => tournament.status !== "finished" && tournament.status !== "archived")
      .slice(0, 4)
  };
}
