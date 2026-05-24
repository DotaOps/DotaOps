import {
  getApiAuthenticated,
  postApiAuthenticated
} from "@/lib/api";

export type MatchImportStatus = "queued" | "processing" | "ready" | "error";

export interface MatchImportRequest {
  dotaMatchId: string;
}

export interface MatchImportEvent {
  createdAt: string | null;
  errorCode: string | null;
  eventType: MatchImportStatus;
  id: string;
  message: string | null;
}

export interface MatchImportJob {
  completedAt: string | null;
  createdAt: string | null;
  dotaMatchId: string;
  errorCode: string | null;
  errorMessage: string | null;
  events: MatchImportEvent[];
  id: string;
  matchGameId: string | null;
  matchId: string | null;
  startedAt: string | null;
  status: MatchImportStatus;
  updatedAt: string | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function text(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function nullableText(value: unknown) {
  return typeof value === "string" && value.trim() ? value : null;
}

function status(value: unknown): MatchImportStatus {
  const normalized = text(value).toLowerCase();

  if (
    normalized === "queued" ||
    normalized === "processing" ||
    normalized === "ready" ||
    normalized === "error"
  ) {
    return normalized;
  }

  return "error";
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

  return [];
}

function fallbackId(prefix: string) {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }

  return `${prefix}-${Math.random().toString(36).slice(2)}`;
}

function mapEvent(value: unknown): MatchImportEvent {
  if (!isRecord(value)) {
    return {
      createdAt: null,
      errorCode: null,
      eventType: "error",
      id: fallbackId("import-event"),
      message: "Invalid import event response."
    };
  }

  return {
    createdAt: nullableText(value.createdAt),
    errorCode: nullableText(value.errorCode),
    eventType: status(value.eventType ?? value.status),
    id: text(value.id, fallbackId("import-event")),
    message: nullableText(value.message)
  };
}

function mapJob(value: unknown): MatchImportJob {
  if (!isRecord(value)) {
    return {
      completedAt: null,
      createdAt: null,
      dotaMatchId: "",
      errorCode: "INVALID_PROVIDER_RESPONSE",
      errorMessage: "Backend returned an invalid import job response.",
      events: [],
      id: "",
      matchGameId: null,
      matchId: null,
      startedAt: null,
      status: "error",
      updatedAt: null
    };
  }

  return {
    completedAt: nullableText(value.completedAt),
    createdAt: nullableText(value.createdAt),
    dotaMatchId: text(value.dotaMatchId),
    errorCode: nullableText(value.errorCode),
    errorMessage: nullableText(value.errorMessage),
    events: arrayPayload(value.events).map(mapEvent),
    id: text(value.id),
    matchGameId: nullableText(value.matchGameId),
    matchId: nullableText(value.matchId),
    startedAt: nullableText(value.startedAt),
    status: status(value.status),
    updatedAt: nullableText(value.updatedAt)
  };
}

export async function createMatchImport(input: MatchImportRequest) {
  return mapJob(
    await postApiAuthenticated<unknown>("/match-imports", {
      dotaMatchId: input.dotaMatchId
    })
  );
}

export async function getMatchImportJob(id: string) {
  return mapJob(await getApiAuthenticated<unknown>(`/match-imports/${id}`));
}

export async function getMatchImportByDotaMatchId(dotaMatchId: string) {
  return mapJob(
    await getApiAuthenticated<unknown>(
      `/match-imports/by-match/${encodeURIComponent(dotaMatchId)}`
    )
  );
}

export async function getMatchImportEvents(id: string) {
  return arrayPayload(
    await getApiAuthenticated<unknown>(`/match-imports/${id}/events`)
  ).map(mapEvent);
}

export async function retryMatchImportJob(id: string) {
  return mapJob(await postApiAuthenticated<unknown>(`/match-imports/${id}/retry`, {}));
}
