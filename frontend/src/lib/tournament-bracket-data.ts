import {
  getApi,
  getApiAuthenticated,
  patchApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";

export type BracketSlotSourceType = "manual" | "seed" | "bye" | "winner" | "loser" | "tbd" | "unknown";
export type BracketMatchStatus = string;

export interface BracketResultPayload {
  scoreA: number;
  scoreB: number;
  winnerTeamId: string;
}

export interface BracketSlot {
  isBye: boolean;
  label: string;
  seedNumber: number | null;
  slotNumber: number;
  sourceMatchId: string | null;
  sourceType: BracketSlotSourceType;
  state: "team" | "bye" | "source" | "tbd";
  teamId: string | null;
  teamName: string | null;
}

export interface BracketMatch {
  bestOf: number;
  bracketPosition: number | null;
  matchCode: string;
  matchId: string;
  roundName: string | null;
  roundNumber: number;
  scheduledAt: string | null;
  scoreA: number;
  scoreB: number;
  slots: BracketSlot[];
  stageName: string | null;
  status: BracketMatchStatus;
  winnerTeamId: string | null;
  winnerTeamName: string | null;
}

export interface BracketRound {
  matches: BracketMatch[];
  roundName: string | null;
  roundNumber: number;
}

export interface TournamentBracket {
  bracketSize: number;
  bracketType: string;
  matches: BracketMatch[];
  rounds: BracketRound[];
  source: "public" | "organizer";
  stageName: string;
  tournamentId: string;
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

function normalizeSourceType(value: unknown): BracketSlotSourceType {
  const sourceType = text(value, "tbd").toLowerCase().replace(/_/g, "-");

  if (sourceType === "manual" || sourceType === "seed" || sourceType === "bye" || sourceType === "winner" || sourceType === "loser") {
    return sourceType;
  }

  if (sourceType === "tbd" || sourceType === "pending") {
    return "tbd";
  }

  return "unknown";
}

function shortMatchId(matchId: string | null) {
  return matchId ? matchId.slice(0, 8).toUpperCase() : "TBD";
}

function sourceLabel(sourceType: BracketSlotSourceType, sourceMatchId: string | null) {
  if (sourceType === "winner") {
    return `Winner of Match ${shortMatchId(sourceMatchId)}`;
  }

  if (sourceType === "loser") {
    return `Loser of Match ${shortMatchId(sourceMatchId)}`;
  }

  if (sourceType === "seed") {
    return "Seeded slot";
  }

  if (sourceType === "manual") {
    return "Manual slot";
  }

  return "Awaiting slot";
}

function mapSlot(value: unknown, fallbackSlotNumber: number): BracketSlot {
  if (!isRecord(value)) {
    return {
      isBye: false,
      label: "TBD",
      seedNumber: null,
      slotNumber: fallbackSlotNumber,
      sourceMatchId: null,
      sourceType: "tbd",
      state: "tbd",
      teamId: null,
      teamName: null
    };
  }

  const teamRecord = isRecord(value.team) ? value.team : null;
  const teamId = nullableText(value.teamId ?? teamRecord?.id);
  const teamName = nullableText(value.teamName ?? teamRecord?.name);
  const seedNumber = nullableNumber(value.seedNumber ?? teamRecord?.seedNumber);
  const sourceType = normalizeSourceType(value.sourceType);
  const sourceMatchId = nullableText(value.sourceMatchId);
  const isBye = value.bye === true || sourceType === "bye";
  const state = teamName ? "team" : isBye ? "bye" : sourceType === "winner" || sourceType === "loser" ? "source" : "tbd";

  return {
    isBye,
    label: teamName ?? (isBye ? "BYE" : state === "source" ? sourceLabel(sourceType, sourceMatchId) : "TBD"),
    seedNumber,
    slotNumber: numberValue(value.slotNumber ?? value.slot, fallbackSlotNumber),
    sourceMatchId,
    sourceType,
    state,
    teamId,
    teamName
  };
}

function fallbackTeamSlot(match: Record<string, unknown>, side: "A" | "B", slotNumber: number): BracketSlot {
  const team = isRecord(match[`team${side}`]) ? match[`team${side}`] as Record<string, unknown> : null;
  const teamId = nullableText(match[`team${side}Id`] ?? team?.id);
  const teamName = nullableText(match[`team${side}Name`] ?? team?.name);

  return {
    isBye: false,
    label: teamName ?? "TBD",
    seedNumber: nullableNumber(team?.seedNumber),
    slotNumber,
    sourceMatchId: null,
    sourceType: teamName ? "manual" : "tbd",
    state: teamName ? "team" : "tbd",
    teamId,
    teamName
  };
}

function mapMatch(value: unknown): BracketMatch {
  if (!isRecord(value)) {
    throw new Error("Bracket API returned an unexpected match shape.");
  }

  const matchId = text(value.matchId ?? value.id);
  const roundNumber = numberValue(value.roundNumber, 1);
  const bracketPosition = nullableNumber(value.bracketPosition);
  const slots = arrayPayload(value.slots).map((slot, index) => mapSlot(slot, index + 1));
  const normalizedSlots = slots.length > 0
    ? slots
    : [fallbackTeamSlot(value, "A", 1), fallbackTeamSlot(value, "B", 2)];

  return {
    bestOf: numberValue(value.bestOf, 1),
    bracketPosition,
    matchCode: bracketPosition ? `R${roundNumber}-M${bracketPosition}` : `R${roundNumber}-${shortMatchId(matchId)}`,
    matchId,
    roundName: nullableText(value.roundName),
    roundNumber,
    scheduledAt: nullableText(value.scheduledAt),
    scoreA: numberValue(value.scoreA),
    scoreB: numberValue(value.scoreB),
    slots: normalizedSlots,
    stageName: nullableText(value.stageName),
    status: text(value.status, "pending"),
    winnerTeamId: nullableText(value.winnerTeamId),
    winnerTeamName: nullableText(value.winnerTeamName ?? (isRecord(value.winnerTeam) ? value.winnerTeam.name : null))
  };
}

function roundFromMatches(roundNumber: number, matches: BracketMatch[]): BracketRound {
  const sortedMatches = [...matches].sort((left, right) =>
    (left.bracketPosition ?? 9999) - (right.bracketPosition ?? 9999) || left.matchId.localeCompare(right.matchId)
  );

  return {
    matches: sortedMatches,
    roundName: sortedMatches[0]?.roundName ?? null,
    roundNumber
  };
}

function roundsFromMatches(matches: BracketMatch[]) {
  const byRound = matches.reduce<Map<number, BracketMatch[]>>((accumulator, match) => {
    const rows = accumulator.get(match.roundNumber) ?? [];
    rows.push(match);
    accumulator.set(match.roundNumber, rows);
    return accumulator;
  }, new Map());

  return [...byRound.entries()]
    .sort(([left], [right]) => left - right)
    .map(([roundNumber, roundMatches]) => roundFromMatches(roundNumber, roundMatches));
}

function mapPublicBracket(value: unknown): TournamentBracket {
  if (!isRecord(value)) {
    throw new Error("Public bracket API returned an unexpected bracket shape.");
  }

  const rounds = arrayPayload(value.rounds).map((round) => {
    if (!isRecord(round)) {
      throw new Error("Public bracket API returned an unexpected round shape.");
    }

    const matches = arrayPayload(round.matches).map(mapMatch);

    return {
      matches,
      roundName: nullableText(round.roundName) ?? matches[0]?.roundName ?? null,
      roundNumber: numberValue(round.roundNumber, matches[0]?.roundNumber ?? 1)
    };
  });

  const matches = rounds.flatMap((round) => round.matches);

  return {
    bracketSize: numberValue(value.bracketSize),
    bracketType: text(value.bracketType, "single_elimination"),
    matches,
    rounds,
    source: "public",
    stageName: text(value.stageName, "Playoffs"),
    tournamentId: text(value.tournamentId)
  };
}

function mapOrganizerBracket(value: unknown): TournamentBracket {
  if (!isRecord(value)) {
    throw new Error("Organizer bracket API returned an unexpected bracket shape.");
  }

  const matches = arrayPayload(value.matches).map(mapMatch);

  return {
    bracketSize: numberValue(value.bracketSize),
    bracketType: text(value.bracketType, "single_elimination"),
    matches,
    rounds: roundsFromMatches(matches),
    source: "organizer",
    stageName: text(value.stageName, "Playoffs"),
    tournamentId: text(value.tournamentId)
  };
}

function stageQuery(stageName: string) {
  return `stageName=${encodeURIComponent(stageName)}`;
}

export async function getPublicTournamentBracket(
  tournamentId: string,
  stageName = "Playoffs"
) {
  return mapPublicBracket(
    await getApi<unknown>(`/public/tournaments/${tournamentId}/bracket?${stageQuery(stageName)}`)
  );
}

export async function getOrganizerTournamentBracket(
  tournamentId: string,
  stageName = "Playoffs"
) {
  return mapOrganizerBracket(
    await getApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/bracket?${stageQuery(stageName)}`)
  );
}

export async function generateOrganizerTournamentBracket(
  tournamentId: string,
  options: { stageName?: string } = {}
) {
  return mapOrganizerBracket(
    await postApiAuthenticated<unknown>(`/organizer/tournaments/${tournamentId}/bracket/generate`, {
      stageName: options.stageName ?? "Playoffs"
    })
  );
}

export async function submitOrganizerMatchResult(
  matchId: string,
  payload: BracketResultPayload
) {
  await patchApiAuthenticated<unknown>(`/organizer/matches/${matchId}/result`, payload);
}
