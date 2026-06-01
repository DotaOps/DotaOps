import type { Tournament } from "@/lib/types";

export type TournamentFilter = "all" | "registration" | "upcoming" | "live" | "finished";

export function tournamentReferenceTime() {
  return Date.now();
}

export function tournamentPhase(
  tournament: Tournament,
  now = Date.now()
): Exclude<TournamentFilter, "all"> {
  const startsAt = tournament.startsAt ? new Date(tournament.startsAt).getTime() : null;
  const endsAt = tournament.endsAt ? new Date(tournament.endsAt).getTime() : null;

  if (tournament.status === "live") return "live";
  if (tournament.status === "finished") return "finished";
  if (tournament.status === "registration") return "registration";

  if (startsAt && startsAt > now) return "upcoming";
  if (startsAt && startsAt <= now && (!endsAt || endsAt >= now)) return "live";
  if (endsAt && endsAt < now) return "finished";

  return "upcoming";
}
