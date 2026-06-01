import { getApiAuthenticated } from "@/lib/api";

export type AdminAuditAction = "delete" | "insert" | "update";

export interface AdminAuditFilters {
  action?: AdminAuditAction;
  actor?: string;
  from?: string;
  page?: number;
  recordId?: string;
  size?: number;
  table?: string;
  to?: string;
}

export interface AdminAuditActor {
  nickname: string | null;
  profileId: string | null;
}

export interface AdminAuditLogItem {
  action: AdminAuditAction;
  actor: AdminAuditActor;
  changedFields: string[];
  createdAt: string;
  id: string;
  recordId: string | null;
  summary: string;
  table: string;
}

export interface AdminAuditPage {
  items: AdminAuditLogItem[];
  page: {
    hasNext: boolean;
    hasPrevious: boolean;
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function stringOrNull(value: unknown) {
  return typeof value === "string" ? value : null;
}

function numberOr(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function booleanOr(value: unknown, fallback: boolean) {
  return typeof value === "boolean" ? value : fallback;
}

function mapAction(value: unknown): AdminAuditAction {
  if (value === "insert" || value === "update" || value === "delete") {
    return value;
  }

  throw new Error("Backend returned an unsupported audit action.");
}

function mapItem(value: unknown): AdminAuditLogItem {
  if (!isRecord(value)) {
    throw new Error("Backend returned an invalid audit log item.");
  }

  const actor = isRecord(value.actor) ? value.actor : {};
  const id = stringOrNull(value.id);
  const createdAt = stringOrNull(value.createdAt);
  const summary = stringOrNull(value.summary);
  const table = stringOrNull(value.table);

  if (!id || !createdAt || !summary || !table) {
    throw new Error("Backend returned an incomplete audit log item.");
  }

  return {
    action: mapAction(value.action),
    actor: {
      nickname: stringOrNull(actor.nickname),
      profileId: stringOrNull(actor.profileId)
    },
    changedFields: Array.isArray(value.changedFields)
      ? value.changedFields.filter((field): field is string => typeof field === "string")
      : [],
    createdAt,
    id,
    recordId: stringOrNull(value.recordId),
    summary,
    table
  };
}

function mapPage(value: unknown): AdminAuditPage {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new Error("Backend returned an invalid audit log page.");
  }

  const meta = isRecord(value.page) ? value.page : {};

  return {
    items: value.items.map(mapItem),
    page: {
      hasNext: booleanOr(meta.hasNext, false),
      hasPrevious: booleanOr(meta.hasPrevious, false),
      page: numberOr(meta.page, 0),
      size: numberOr(meta.size, 20),
      totalElements: numberOr(meta.totalElements, 0),
      totalPages: numberOr(meta.totalPages, 0)
    }
  };
}

function queryString(filters: AdminAuditFilters) {
  const params = new URLSearchParams();

  if (filters.table) params.set("table", filters.table);
  if (filters.recordId) params.set("recordId", filters.recordId);
  if (filters.actor) params.set("actor", filters.actor);
  if (filters.action) params.set("action", filters.action);
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  params.set("page", String(filters.page ?? 0));
  params.set("size", String(filters.size ?? 20));

  return params.toString();
}

export async function listAdminAuditLogs(filters: AdminAuditFilters): Promise<AdminAuditPage> {
  const response = await getApiAuthenticated<unknown>(
    `/admin/audit-logs?${queryString(filters)}`,
    undefined,
    { unwrap: false }
  );

  if (!isRecord(response) || !("data" in response)) {
    throw new Error("Backend returned an invalid audit response.");
  }

  return mapPage(response.data);
}
