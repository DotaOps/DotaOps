import {
  type AnalyticsMatchHistory,
  type PlayerHeroPerformance
} from "@/lib/analytics-data";

type MatchTeamsLike = Readonly<
  Pick<
    AnalyticsMatchHistory,
    "teamAId" | "teamAName" | "teamBId" | "teamBName" | "winnerTeamId"
  >
>;

export function countMetricValue(value: number | null | undefined) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("en-US")
    : "No data";
}

export function safeMetricNumber(value: number | null | undefined, digits = 1) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toFixed(digits)
    : "No data";
}

export function formatAnalyticsDateTime(value: string | null) {
  if (!value) {
    return "Played time unavailable";
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });
}

export function formatAnalyticsDuration(value: number | null) {
  if (!value || value <= 0) {
    return "No data";
  }

  const minutes = Math.floor(value / 60);
  const seconds = Math.round(value % 60);

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function formatAnalyticsEnum(value: string | null | undefined) {
  if (!value) {
    return "No data";
  }

  return value
    .replace(/([a-z])([A-Z])/g, "$1_$2")
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function matchTeamName(name: string | null, fallback: string) {
  return name?.trim() || fallback;
}

export function matchTeamsLabel(match: MatchTeamsLike) {
  return `${matchTeamName(match.teamAName, "Team A unavailable")} vs ${matchTeamName(match.teamBName, "Team B unavailable")}`;
}

export function matchWinnerLabel(match: MatchTeamsLike) {
  if (!match.winnerTeamId) {
    return "Winner unavailable";
  }

  if (match.teamAId === match.winnerTeamId) {
    return `${matchTeamName(match.teamAName, "Team A")} won`;
  }

  if (match.teamBId === match.winnerTeamId) {
    return `${matchTeamName(match.teamBName, "Team B")} won`;
  }

  return `Winner: ${match.winnerTeamId}`;
}

export function playerHeroReferenceLabel(
  hero: Pick<PlayerHeroPerformance, "recentDotaMatchId" | "recentMatchId">
) {
  return hero.recentDotaMatchId
    ? `Dota ${hero.recentDotaMatchId}`
    : hero.recentMatchId ?? "Recent match unavailable";
}
