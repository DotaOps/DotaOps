import type { ApiResult } from "@/lib/types";

const PUBLIC_API_URL = process.env.NEXT_PUBLIC_API_URL;
const SERVER_API_URL = process.env.NEXT_SERVER_API_URL ?? process.env.INTERNAL_API_URL;

type ApiRequestInit = RequestInit & {
  next?: {
    revalidate?: number;
  };
};

interface AuthenticatedGetOptions {
  unwrap?: boolean;
}

export interface ApiFieldError {
  code?: string | null;
  field?: string | null;
  message?: string | null;
}

export class ApiRequestError extends Error {
  errors: ApiFieldError[];
  payload: unknown;
  status: number;

  constructor(message: string, status: number, payload?: unknown) {
    super(message);
    this.name = "ApiRequestError";
    this.errors = apiFieldErrors(payload);
    this.payload = payload;
    this.status = status;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function resolveApiUrl() {
  return typeof window === "undefined"
    ? SERVER_API_URL ?? PUBLIC_API_URL
    : PUBLIC_API_URL;
}

function unwrapBackendPayload(value: unknown) {
  const data = isRecord(value) && "data" in value ? value.data : value;

  if (isRecord(data) && "items" in data) {
    return data.items;
  }

  return data;
}

async function readJson(response: Response) {
  const text = await response.text();

  if (!text) {
    return null;
  }

  return JSON.parse(text) as unknown;
}

function apiErrorMessage(value: unknown) {
  if (isRecord(value) && typeof value.message === "string") {
    return value.message;
  }

  if (isRecord(value) && isRecord(value.error) && typeof value.error.message === "string") {
    return value.error.message;
  }

  return null;
}

function apiFieldErrors(value: unknown): ApiFieldError[] {
  if (!isRecord(value) || !Array.isArray(value.errors)) {
    return [];
  }

  return value.errors
    .filter(isRecord)
    .map((error) => ({
      code: typeof error.code === "string" ? error.code : null,
      field: typeof error.field === "string" ? error.field : null,
      message: typeof error.message === "string" ? error.message : null
    }));
}

function debugAuthFailure(path: string, response: Response, accessToken: string) {
  if (response.status !== 401 && response.status !== 403) {
    return;
  }

  console.warn("Authenticated API request failed.", {
    authorizationHeaderSent: Boolean(accessToken),
    path,
    status: response.status,
    tokenLength: accessToken.length
  });
}

async function freshAccessToken() {
  if (typeof window === "undefined") {
    throw new ApiRequestError("Login session expired. Please log in again.", 401);
  }

  const { getSupabaseBrowserClient } = await import("@/lib/supabase");
  const supabase = getSupabaseBrowserClient();

  if (!supabase) {
    throw new ApiRequestError("Supabase frontend environment variables are missing.", 401);
  }

  const { data, error } = await supabase.auth.getSession();

  if (error) {
    throw error;
  }

  if (!data.session?.access_token) {
    throw new ApiRequestError("Login session expired. Please log in again.", 401);
  }

  return data.session.access_token;
}

async function resolveAccessToken(accessToken?: string) {
  return accessToken ?? freshAccessToken();
}

function hasCompatibleShape<T>(value: unknown, fallback: T): value is T {
  if (Array.isArray(fallback)) {
    if (!Array.isArray(value)) {
      return false;
    }

    const fallbackSample = fallback[0];

    if (!isRecord(fallbackSample) || value.length === 0) {
      return true;
    }

    return value.every((item) => {
      if (!isRecord(item)) {
        return false;
      }

      return Object.keys(fallbackSample).every((key) => key in item);
    });
  }

  if (fallback === null) {
    return value === null || isRecord(value);
  }

  if (isRecord(fallback)) {
    if (!isRecord(value)) {
      return false;
    }

    return Object.keys(fallback).every((key) => key in value);
  }

  return typeof value === typeof fallback;
}

async function requestApi(path: string, init?: ApiRequestInit) {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const response = await fetch(`${apiUrl}${path}`, {
    ...init,
    cache: init?.cache ?? "no-store",
    headers: {
      "Content-Type": "application/json",
      ...init?.headers
    },
    method: "GET"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Backend request failed.",
      response.status,
      rawPayload
    );
  }

  return rawPayload;
}

export async function getApi<T>(path: string, init?: ApiRequestInit): Promise<T> {
  return unwrapBackendPayload(await requestApi(path, init)) as T;
}

export async function getPagedApi<T>(path: string, init?: ApiRequestInit): Promise<T> {
  const payload = await requestApi(path, init);

  return (isRecord(payload) && "data" in payload ? payload.data : payload) as T;
}

export async function postApi<T>(path: string, body: unknown): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const response = await fetch(`${apiUrl}${path}`, {
    body: JSON.stringify(body),
    cache: "no-store",
    credentials: "include",
    headers: {
      "Content-Type": "application/json"
    },
    method: "POST"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    throw new Error(apiErrorMessage(rawPayload) ?? "Backend request failed.");
  }

  return unwrapBackendPayload(rawPayload) as T;
}

export async function patchApiAuthenticated<T>(
  path: string,
  body: unknown,
  accessToken?: string
): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const resolvedToken = await resolveAccessToken(accessToken);
  const response = await fetch(`${apiUrl}${path}`, {
    body: JSON.stringify(body),
    cache: "no-store",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${resolvedToken}`,
      "Content-Type": "application/json"
    },
    method: "PATCH"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    debugAuthFailure(path, response, resolvedToken);
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Authenticated backend request failed.",
      response.status,
      rawPayload
    );
  }

  return unwrapBackendPayload(rawPayload) as T;
}

export async function getApiAuthenticated<T>(
  path: string,
  accessToken?: string,
  options?: AuthenticatedGetOptions
): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const resolvedToken = await resolveAccessToken(accessToken);
  const response = await fetch(`${apiUrl}${path}`, {
    cache: "no-store",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${resolvedToken}`,
      "Content-Type": "application/json"
    },
    method: "GET"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    debugAuthFailure(path, response, resolvedToken);
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Authenticated backend request failed.",
      response.status,
      rawPayload
    );
  }

  return (options?.unwrap === false ? rawPayload : unwrapBackendPayload(rawPayload)) as T;
}

export async function postApiAuthenticated<T>(
  path: string,
  body: unknown,
  accessToken?: string
): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const resolvedToken = await resolveAccessToken(accessToken);
  const response = await fetch(`${apiUrl}${path}`, {
    body: JSON.stringify(body),
    cache: "no-store",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${resolvedToken}`,
      "Content-Type": "application/json"
    },
    method: "POST"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    debugAuthFailure(path, response, resolvedToken);
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Authenticated backend request failed.",
      response.status,
      rawPayload
    );
  }

  return unwrapBackendPayload(rawPayload) as T;
}

export async function deleteApiAuthenticated<T>(
  path: string,
  accessToken?: string
): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const resolvedToken = await resolveAccessToken(accessToken);
  const response = await fetch(`${apiUrl}${path}`, {
    cache: "no-store",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${resolvedToken}`,
      "Content-Type": "application/json"
    },
    method: "DELETE"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    debugAuthFailure(path, response, resolvedToken);
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Authenticated backend request failed.",
      response.status,
      rawPayload
    );
  }

  return unwrapBackendPayload(rawPayload) as T;
}

export async function postFormApiAuthenticated<T>(
  path: string,
  body: FormData,
  accessToken?: string
): Promise<T> {
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    throw new Error("Backend API URL is not configured.");
  }

  const resolvedToken = await resolveAccessToken(accessToken);
  const response = await fetch(`${apiUrl}${path}`, {
    body,
    cache: "no-store",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${resolvedToken}`
    },
    method: "POST"
  });
  const rawPayload = await readJson(response);

  if (!response.ok) {
    debugAuthFailure(path, response, resolvedToken);
    throw new ApiRequestError(
      apiErrorMessage(rawPayload) ?? "Authenticated backend request failed.",
      response.status,
      rawPayload
    );
  }

  return unwrapBackendPayload(rawPayload) as T;
}

export async function fetchApi<T>(
  path: string,
  fallback: T,
  init?: RequestInit
): Promise<ApiResult<T>> {
  // Legacy helper for development-only fallback callers. Production feature data should use
  // the strict getApi/getPagedApi wrappers so missing backend data surfaces as an error.
  const apiUrl = resolveApiUrl();

  if (!apiUrl) {
    return { data: fallback, source: "mock" };
  }

  try {
    const response = await fetch(`${apiUrl}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers
      },
      next: { revalidate: 30 }
    });

    if (!response.ok) {
      return { data: fallback, source: "mock" };
    }

    const payload = unwrapBackendPayload(await readJson(response));

    if (!hasCompatibleShape(payload, fallback)) {
      return { data: fallback, source: "mock" };
    }

    return { data: payload, source: "api" };
  } catch {
    return { data: fallback, source: "mock" };
  }
}
