import {
  getApi,
  getApiAuthenticated,
  patchApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";

export type TournamentMatchStatus = "ready" | "scheduled" | "live" | "finished" | "cancelled" | string;

export interface MatchTeam {
  id: string | null;
  isBye: boolean;
  isTbd: boolean;
  name: string;
  seedNumber: number | null;
  sourceLabel: string | null;
}

export interface MatchSlot {
  isBye: boolean;
  label: string;
  seedNumber: number | null;
  slotNumber: number;
  sourceMatchId: string | null;
  sourceType: string;
  team: MatchTeam | null;
}

export interface TournamentMatch {
  bestOf: number;
  bracketPosition: number | null;
  cancellationReason: string | null;
  cancelledAt: string | null;
  displayCode: string;
  finishedAt: string | null;
  groupId: string | null;
  groupName: string | null;
  id: string;
  roundName: string | null;
  roundNumber: number;
  scheduledAt: string | null;
  scoreA: number;
  scoreB: number;
  slots: MatchSlot[];
  stageName: string | null;
  startedAt: string | null;
  status: TournamentMatchStatus;
  teamA: MatchTeam;
  teamB: MatchTeam;
  tournamentId: string;
  updatedAt: string | null;
  winnerTeamId: string | null;
  winnerTeamName: string | null;
}

export interface MatchResultPayload {
  scoreA: number;
  scoreB: number;
  winnerTeamId: string;
}

export interface MatchSchedulePayload {
  scheduledAt: string;
}

export interface MatchCancelPayload {
  reason?: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
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

function shortMatchId(matchId: string) {
  return matchId ? matchId.slice(0, 8).toUpperCase() : "UNKNOWN";
}

function sourceLabel(sourceType: string, sourceMatchId: string | null) {
  const normalized = sourceType.toLowerCase();

  if (normalized === "winner") {
    return `Winner of Match ${sourceMatchId ? shortMatchId(sourceMatchId) : "TBD"}`;
  }

  if (normalized === "loser") {
    return `Loser of Match ${sourceMatchId ? shortMatchId(sourceMatchId) : "TBD"}`;
  }

  if (normalized === "seed") {
    return "Seeded slot";
  }

  if (normalized === "bye") {
    return "BYE";
  }

  return "TBD";
}

function emptyTeam(label = "TBD"): MatchTeam {
  return {
    id: null,
    isBye: label === "BYE",
    isTbd: label !== "BYE",
    name: label,
    seedNumber: null,
    sourceLabel: null
  };
}

function teamFromValue(value: unknown, fallbackName = "TBD"): MatchTeam {
  if (!isRecord(value)) {
    return emptyTeam(fallbackName);
  }

  const name = nullableText(value.name) ?? fallbackName;

  return {
    id: nullableText(value.id),
    isBye: false,
    isTbd: !nullableText(value.id),
    name,
    seedNumber: nullableNumber(value.seedNumber),
    sourceLabel: null
  };
}

function teamFromFlat(value: Record<string, unknown>, side: "A" | "B"): MatchTeam {
  const rawTeam = value[`team${side}`];
  const teamRecord: Record<string, unknown> | null = isRecord(rawTeam) ? rawTeam : null;
  const id = nullableText(value[`team${side}Id`] ?? teamRecord?.id);
  const name = nullableText(value[`team${side}Name`] ?? teamRecord?.name);

  return {
    id,
    isBye: false,
    isTbd: !id,
    name: name ?? "TBD",
    seedNumber: nullableNumber(teamRecord?.seedNumber),
    sourceLabel: null
  };
}

function mapSlot(value: unknown, fallbackSlotNumber: number): MatchSlot {
  if (!isRecord(value)) {
    return {
      isBye: false,
      label: "TBD",
      seedNumber: null,
      slotNumber: fallbackSlotNumber,
      sourceMatchId: null,
      sourceType: "tbd",
      team: null
    };
  }

  const sourceType = text(value.sourceType, "tbd").toLowerCase();
  const sourceMatchId = nullableText(value.sourceMatchId);
  const isBye = value.bye === true || sourceType === "bye";
  const teamRecord = isRecord(value.team) ? value.team : null;
  const team = teamFromValue(teamRecord, isBye ? "BYE" : "TBD");
  const hasTeam = Boolean(team.id);
  const label = hasTeam ? team.name : sourceLabel(sourceType, sourceMatchId);

  return {
    isBye,
    label,
    seedNumber: nullableNumber(value.seedNumber ?? teamRecord?.seedNumber),
    slotNumber: numberValue(value.slotNumber ?? value.slot, fallbackSlotNumber),
    sourceMatchId,
    sourceType,
    team: hasTeam ? team : null
  };
}

function teamFromSlotOrFlat(value: Record<string, unknown>, side: "A" | "B", slots: MatchSlot[]) {
  const flatTeam = teamFromFlat(value, side);

  if (flatTeam.id) {
    return flatTeam;
  }

  const slot = slots.find((item) => item.slotNumber === (side === "A" ? 1 : 2));

  if (!slot) {
    return flatTeam;
  }

  if (slot.team) {
    return {
      ...slot.team,
      seedNumber: slot.seedNumber ?? slot.team.seedNumber
    };
  }

  return {
    id: null,
    isBye: slot.isBye,
    isTbd: !slot.isBye,
    name: slot.label,
    seedNumber: slot.seedNumber,
    sourceLabel: slot.sourceType === "winner" || slot.sourceType === "loser" ? slot.label : null
  };
}

export function mapTournamentMatch(value: unknown): TournamentMatch {
  if (!isRecord(value)) {
    throw new Error("Match API returned an unexpected match shape.");
  }

  const id = text(value.id ?? value.matchId);

  if (!id) {
    throw new Error("Match API returned a match without an id.");
  }

  const slots = arrayPayload(value.slots).map((slot, index) => mapSlot(slot, index + 1));
  const roundNumber = numberValue(value.roundNumber, 0);
  const bracketPosition = nullableNumber(value.bracketPosition);
  const teamA = teamFromSlotOrFlat(value, "A", slots);
  const teamB = teamFromSlotOrFlat(value, "B", slots);

  return {
    bestOf: numberValue(value.bestOf, 1),
    bracketPosition,
    cancellationReason: nullableText(value.cancellationReason),
    cancelledAt: nullableText(value.cancelledAt),
    displayCode: bracketPosition ? `R${roundNumber || 1}-M${bracketPosition}` : shortMatchId(id),
    finishedAt: nullableText(value.finishedAt),
    groupId: nullableText(value.groupId),
    groupName: nullableText(value.groupName),
    id,
    roundName: nullableText(value.roundName),
    roundNumber,
    scheduledAt: nullableText(value.scheduledAt ?? value.startTime),
    scoreA: numberValue(value.scoreA),
    scoreB: numberValue(value.scoreB),
    slots,
    stageName: nullableText(value.stageName),
    startedAt: nullableText(value.startedAt),
    status: text(value.status, "ready").toLowerCase(),
    teamA,
    teamB,
    tournamentId: text(value.tournamentId),
    updatedAt: nullableText(value.updatedAt),
    winnerTeamId: nullableText(value.winnerTeamId),
    winnerTeamName: nullableText(value.winnerTeamName ?? (isRecord(value.winnerTeam) ? value.winnerTeam.name : null))
  };
}

function mapTournamentMatches(value: unknown) {
  return arrayPayload(value).map(mapTournamentMatch);
}

export async function getPublicTournamentMatches(tournamentId: string) {
  return mapTournamentMatches(
    await getApi<unknown>(`/public/tournaments/${tournamentId}/matches`)
  );
}

export async function getOrganizerTournamentMatches(tournamentId: string) {
  return mapTournamentMatches(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/matches`)
  );
}

export async function scheduleOrganizerMatch(
  matchId: string,
  payload: MatchSchedulePayload
) {
  return mapTournamentMatch(
    await patchApiAuthenticated<unknown>(`/organizer/matches/${matchId}/schedule`, payload)
  );
}

export async function startOrganizerMatch(matchId: string) {
  return mapTournamentMatch(
    await postApiAuthenticated<unknown>(`/organizer/matches/${matchId}/start`, {})
  );
}

export async function cancelOrganizerMatch(
  matchId: string,
  payload: MatchCancelPayload = {}
) {
  return mapTournamentMatch(
    await postApiAuthenticated<unknown>(`/organizer/matches/${matchId}/cancel`, payload)
  );
}

export async function finishOrganizerMatch(matchId: string) {
  return mapTournamentMatch(
    await postApiAuthenticated<unknown>(`/organizer/matches/${matchId}/finish`, {})
  );
}

export async function submitOrganizerMatchResult(
  matchId: string,
  payload: MatchResultPayload
) {
  return mapTournamentMatch(
    await patchApiAuthenticated<unknown>(`/organizer/matches/${matchId}/result`, payload)
  );
}
