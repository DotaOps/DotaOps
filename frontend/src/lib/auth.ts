"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase";
import {
  ApiRequestError,
  getApiAuthenticated,
  patchApiAuthenticated,
  postApiAuthenticated,
  postFormApiAuthenticated
} from "@/lib/api";

const PROFILE_SELECT_COLUMNS = [
  "id",
  "avatar_url",
  "bio",
  "country_code",
  "created_at",
  "display_name",
  "nickname",
  "opendota_account_id",
  "opendota_profile_synced_at",
  "role",
  "steam_id",
  "steam_profile_synced_at",
  "updated_at"
].join(",");

export type RequestedAuthRole = "player" | "captain" | "organizer";
export type ProfileRole = RequestedAuthRole | "visitor" | "admin";

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput {
  nickname: string;
  displayName: string;
  email: string;
  password: string;
  requestedRole: RequestedAuthRole;
  countryCode?: string;
  steamIdOrProfile?: string;
  bio?: string;
}

export interface AuthResult {
  dashboardPath: string;
  message?: string;
  requiresEmailConfirmation?: boolean;
  role?: ProfileRole | null;
}

export class RegistrationRateLimitError extends Error {
  retryAfterSeconds: number;

  constructor(retryAfterSeconds = 60) {
    super("Too many registration attempts. Please wait a few minutes before trying again.");
    this.name = "RegistrationRateLimitError";
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export interface CurrentUserProfile {
  avatarUrl: string | null;
  bio: string | null;
  countryCode: string | null;
  createdAt: string | null;
  displayName: string | null;
  email: string | null;
  nickname: string;
  opendotaAccountId: number | null;
  opendotaProfileSyncedAt: string | null;
  profileId: string | null;
  role: ProfileRole;
  steamId: string | null;
  steamProfileSyncedAt: string | null;
  updatedAt: string | null;
}

export interface ProfileUpdateInput {
  bio?: string;
  countryCode?: string;
  displayName?: string;
  nickname?: string;
}

export interface AvatarUploadResult {
  avatarUrl: string | null;
  message: string;
  persisted: boolean;
}

export interface ProfileSaveResult {
  message?: string;
  profile: CurrentUserProfile;
}

interface SteamLinkStartResponse {
  redirectUrl?: string | null;
}

interface ProfileRow {
  id?: string | null;
  avatar_url?: string | null;
  bio?: string | null;
  country_code?: string | null;
  created_at?: string | null;
  display_name?: string | null;
  nickname?: string | null;
  opendota_account_id?: number | null;
  opendota_profile_synced_at?: string | null;
  role: ProfileRole | null;
  steam_id?: string | null;
  steam_profile_synced_at?: string | null;
  updated_at?: string | null;
}

interface BackendProfileResponse {
  id?: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
  countryCode?: string | null;
  createdAt?: string | null;
  displayName?: string | null;
  nickname?: string | null;
  opendotaAccountId?: number | null;
  opendotaProfileSyncedAt?: string | null;
  role?: ProfileRole | null;
  steamId?: string | null;
  steamProfileSyncedAt?: string | null;
  updatedAt?: string | null;
}

function requireSupabaseClient() {
  const supabase = getSupabaseBrowserClient();

  if (!supabase) {
    throw new Error("Supabase frontend environment variables are missing.");
  }

  return supabase;
}

export function dashboardPathForRole(role?: string | null) {
  if (role === "captain" || role === "organizer" || role === "player") {
    return `/dashboard?role=${role}`;
  }

  return "/dashboard?role=player";
}

function normalizeCountryCode(value?: string) {
  const trimmed = value?.trim().toUpperCase();

  if (!trimmed) {
    return null;
  }

  return trimmed.slice(0, 2);
}

function normalizeEmail(value: string) {
  return value.trim().toLowerCase();
}

function normalizeOptionalText(value?: string) {
  const trimmed = value?.trim();

  return trimmed ? trimmed : null;
}

function normalizeRequiredText(value: string, fallback: string) {
  const trimmed = value.trim();

  return trimmed || fallback;
}

function profileRoleForRegistration(role: RequestedAuthRole) {
  return role === "organizer" ? "organizer" : "player";
}

function createProfilePayload(input: RegisterInput) {
  return {
    bio: normalizeOptionalText(input.bio),
    country_code: normalizeCountryCode(input.countryCode),
    desired_role: profileRoleForRegistration(input.requestedRole),
    display_name: normalizeOptionalText(input.displayName),
    nickname: normalizeRequiredText(input.nickname, "player")
  };
}

function editableProfilePayload(input: ProfileUpdateInput) {
  return {
    bio: input.bio?.trim() || null,
    country_code: normalizeCountryCode(input.countryCode),
    display_name: input.displayName?.trim() || null,
    nickname: input.nickname?.trim() || "player"
  };
}

function backendProfilePayload(input: ProfileUpdateInput) {
  return {
    bio: input.bio?.trim() || null,
    country_code: normalizeCountryCode(input.countryCode),
    display_name: input.displayName?.trim() || null,
    nickname: input.nickname?.trim() || "player"
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function isPolicyError(value: unknown) {
  if (!isRecord(value)) {
    return false;
  }

  const code = typeof value.code === "string" ? value.code : "";
  const status = typeof value.status === "number" ? value.status : null;
  const message = typeof value.message === "string" ? value.message.toLowerCase() : "";

  return (
    status === 403 ||
    code === "42501" ||
    message.includes("row-level security") ||
    message.includes("permission denied") ||
    message.includes("violates row-level security")
  );
}

function isRegistrationRateLimitError(value: unknown) {
  if (!isRecord(value)) {
    return false;
  }

  const code = typeof value.code === "string" ? value.code.toLowerCase() : "";
  const message = typeof value.message === "string" ? value.message.toLowerCase() : "";
  const status = typeof value.status === "number" ? value.status : null;

  return (
    status === 429 ||
    code.includes("rate") ||
    message.includes("rate limit") ||
    message.includes("too many")
  );
}

export async function getCurrentProfileRole(authUserId: string) {
  const supabase = requireSupabaseClient();
  const { data, error } = await supabase
    .from("profiles")
    .select("role")
    .eq("auth_user_id", authUserId)
    .maybeSingle();

  if (error) {
    throw error;
  }

  const profile = data as ProfileRow | null;

  return profile?.role ?? null;
}

function profileFromRow(row: ProfileRow | null, email: string | null): CurrentUserProfile | null {
  if (!row?.nickname) {
    return null;
  }

  return {
    avatarUrl: row.avatar_url ?? null,
    bio: row.bio ?? null,
    countryCode: row.country_code ?? null,
    createdAt: row.created_at ?? null,
    displayName: row.display_name ?? null,
    email,
    nickname: row.nickname,
    opendotaAccountId: row.opendota_account_id ?? null,
    opendotaProfileSyncedAt: row.opendota_profile_synced_at ?? null,
    profileId: row.id ?? null,
    role: row.role ?? "player",
    steamId: row.steam_id ?? null,
    steamProfileSyncedAt: row.steam_profile_synced_at ?? null,
    updatedAt: row.updated_at ?? null
  };
}

function profileFromBackend(
  profile: BackendProfileResponse | null,
  email: string | null
): CurrentUserProfile | null {
  if (!profile?.nickname) {
    return null;
  }

  return {
    avatarUrl: profile.avatarUrl ?? null,
    bio: profile.bio ?? null,
    countryCode: profile.countryCode ?? null,
    createdAt: profile.createdAt ?? null,
    displayName: profile.displayName ?? null,
    email,
    nickname: profile.nickname,
    opendotaAccountId: profile.opendotaAccountId ?? null,
    opendotaProfileSyncedAt: profile.opendotaProfileSyncedAt ?? null,
    profileId: profile.id ?? null,
    role: profile.role ?? "player",
    steamId: profile.steamId ?? null,
    steamProfileSyncedAt: profile.steamProfileSyncedAt ?? null,
    updatedAt: profile.updatedAt ?? null
  };
}

export async function getCurrentUserProfile(): Promise<CurrentUserProfile | null> {
  const supabase = requireSupabaseClient();
  const [{ data: userData, error: userError }, { data: sessionData }] = await Promise.all([
    supabase.auth.getUser(),
    supabase.auth.getSession()
  ]);

  if (userError) {
    throw userError;
  }

  const user = userData.user;

  if (!user) {
    return null;
  }

  if (sessionData.session?.access_token) {
    try {
      const backendProfile = profileFromBackend(
        await getApiAuthenticated<BackendProfileResponse>(
          "/me/profile",
          sessionData.session.access_token
        ),
        user.email ?? null
      );

      if (backendProfile) {
        return backendProfile;
      }
    } catch (error) {
      if (!(error instanceof ApiRequestError && (error.status === 401 || error.status === 403 || error.status === 404))) {
        console.warn("Backend current profile API unavailable; falling back to Supabase profile row.", error);
      }
    }
  }

  const { data, error } = await supabase
    .from("profiles")
    .select(PROFILE_SELECT_COLUMNS)
    .eq("auth_user_id", user.id)
    .maybeSingle();

  if (error) {
    throw error;
  }

  const profile = profileFromRow(data as ProfileRow | null, user.email ?? null);

  if (profile) {
    return profile;
  }

  const metadata = user.user_metadata ?? {};
  const requestedRole = metadata.desired_role ?? metadata.requested_role;

  return {
    avatarUrl: null,
    bio: null,
    countryCode: null,
    createdAt: null,
    displayName: typeof metadata.display_name === "string" ? metadata.display_name : null,
    email: user.email ?? null,
    nickname:
      (typeof metadata.nickname === "string" && metadata.nickname) ||
      user.email?.split("@")[0] ||
      "player",
    opendotaAccountId: null,
    opendotaProfileSyncedAt: null,
    profileId: null,
    role:
      requestedRole === "captain" || requestedRole === "organizer" || requestedRole === "player"
        ? requestedRole
        : "player",
    steamId: null,
    steamProfileSyncedAt: null,
    updatedAt: null
  };
}

async function updateProfileViaBackend(
  input: ProfileUpdateInput,
  accessToken: string,
  email: string | null
) {
  const updatedProfile = profileFromBackend(
    await patchApiAuthenticated<BackendProfileResponse>(
      "/me/profile",
      backendProfilePayload(input),
      accessToken
    ),
    email
  );

  if (!updatedProfile) {
    throw new Error("Backend profile update returned no profile data.");
  }

  return updatedProfile;
}

async function updateProfileViaSupabase(
  input: ProfileUpdateInput,
  authUserId: string,
  email: string | null
) {
  const supabase = requireSupabaseClient();
  const { data, error } = await supabase
    .from("profiles")
    .update(editableProfilePayload(input))
    .eq("auth_user_id", authUserId)
    .select(PROFILE_SELECT_COLUMNS)
    .maybeSingle();

  if (error) {
    throw error;
  }

  if (!data) {
    throw new Error("Profile row was not found. Backend profile creation/update endpoint is required.");
  }

  const updatedProfile = profileFromRow(data as unknown as ProfileRow | null, email);

  if (!updatedProfile) {
    throw new Error("Profile update returned no profile data.");
  }

  return updatedProfile;
}

function profileSaveErrorMessage(backendError: unknown, supabaseError: unknown) {
  const backendMessage = backendError instanceof Error ? backendError.message : null;
  const supabaseMessage = supabaseError instanceof Error ? supabaseError.message : null;

  if (isPolicyError(supabaseError)) {
    return [
      "Profile update could not be saved.",
      "Supabase RLS blocked direct profile updates.",
      "Backend PATCH /api/me/profile must accept the current session and update nickname, displayName, countryCode and bio.",
      backendMessage ? `Backend response: ${backendMessage}` : null
    ]
      .filter(Boolean)
      .join(" ");
  }

  return [
    "Profile update could not be saved.",
    backendMessage ? `Backend response: ${backendMessage}` : null,
    supabaseMessage ? `Supabase response: ${supabaseMessage}` : null
  ]
    .filter(Boolean)
    .join(" ");
}

function backendFallbackMessage(error: unknown) {
  if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
    return "Profile saved through Supabase fallback. Backend PATCH /api/me/profile rejected the bearer token.";
  }

  if (error instanceof ApiRequestError && error.status === 404) {
    return "Profile saved through Supabase fallback. Backend PATCH /api/me/profile is not available.";
  }

  return "Profile saved through Supabase fallback.";
}

export async function updateCurrentUserProfile(input: ProfileUpdateInput): Promise<ProfileSaveResult> {
  const supabase = requireSupabaseClient();
  const [{ data: userData, error: userError }, { data: sessionData }] = await Promise.all([
    supabase.auth.getUser(),
    supabase.auth.getSession()
  ]);

  if (userError) {
    throw userError;
  }

  const user = userData.user;

  if (!user) {
    throw new Error("Login required before profile updates.");
  }

  if (!sessionData.session?.access_token) {
    throw new Error("Login session expired. Please log in again.");
  }

  let backendError: unknown = null;

  try {
    return {
      profile: await updateProfileViaBackend(
        input,
        sessionData.session.access_token,
        user.email ?? null
      )
    };
  } catch (caught) {
    backendError = caught;
  }

  try {
    return {
      message: backendFallbackMessage(backendError),
      profile: await updateProfileViaSupabase(input, user.id, user.email ?? null)
    };
  } catch (supabaseError) {
    throw new Error(profileSaveErrorMessage(backendError, supabaseError));
  }
}

export async function uploadCurrentUserAvatar(file: File): Promise<AvatarUploadResult> {
  const supabase = requireSupabaseClient();
  const { data } = await supabase.auth.getSession();

  if (!data.session?.access_token) {
    return {
      avatarUrl: null,
      message: "Avatar preview updated locally. Backend upload endpoint is not available yet.",
      persisted: false
    };
  }

  const formData = new FormData();
  formData.append("avatar", file);

  let payload: unknown;

  try {
    payload = await postFormApiAuthenticated<unknown>(
      "/me/avatar",
      formData,
      data.session.access_token
    );
  } catch (caught) {
    if (caught instanceof ApiRequestError && caught.status === 404) {
      return {
        avatarUrl: null,
        message: "Avatar preview updated locally. Backend upload endpoint is not available yet.",
        persisted: false
      };
    }

    if (caught instanceof ApiRequestError && (caught.status === 401 || caught.status === 403)) {
      return {
        avatarUrl: null,
        message: "Avatar preview updated locally. Backend avatar upload rejected authentication.",
        persisted: false
      };
    }

    if (caught instanceof Error && caught.message === "Backend API URL is not configured.") {
      return {
        avatarUrl: null,
        message: "Avatar preview updated locally. Backend upload endpoint is not available yet.",
        persisted: false
      };
    }

    throw caught;
  }

  const avatarUrl =
    isRecord(payload) && typeof payload.avatarUrl === "string"
      ? payload.avatarUrl
      : isRecord(payload) && typeof payload.avatar_url === "string"
        ? payload.avatar_url
        : null;

  if (!avatarUrl) {
    return {
      avatarUrl: null,
      message: "Avatar preview updated locally. Backend upload did not return avatar_url.",
      persisted: false
    };
  }

  return {
    avatarUrl,
    message: "Avatar uploaded successfully.",
    persisted: true
  };
}

export async function startSteamProfileLink(): Promise<string> {
  const supabase = requireSupabaseClient();
  const { data, error } = await supabase.auth.getSession();

  if (error) {
    throw error;
  }

  if (!data.session?.access_token) {
    throw new Error("Login session expired. Please log in again.");
  }

  const result = await postApiAuthenticated<SteamLinkStartResponse>(
    "/auth/steam/link",
    {},
    data.session.access_token
  );

  if (!result.redirectUrl) {
    throw new Error("Steam connection could not be started.");
  }

  return result.redirectUrl;
}

export async function signOutCurrentUser() {
  const supabase = requireSupabaseClient();
  const { error } = await supabase.auth.signOut();

  if (error) {
    throw error;
  }
}

export async function hasAuthenticatedSession() {
  const supabase = requireSupabaseClient();
  const { data, error } = await supabase.auth.getSession();

  if (error) {
    throw error;
  }

  return Boolean(data.session?.access_token);
}

export async function loginWithEmailPassword(input: LoginInput): Promise<AuthResult> {
  const supabase = requireSupabaseClient();
  const { data, error } = await supabase.auth.signInWithPassword({
    email: normalizeEmail(input.email),
    password: input.password
  });

  if (error) {
    throw error;
  }

  const { data: sessionData } = await supabase.auth.getSession();

  if (!sessionData.session?.access_token) {
    throw new Error("Login session was not established. Please try again.");
  }

  const authUserId = data.user?.id;

  if (!authUserId) {
    return {
      dashboardPath: "/dashboard?role=player",
      message: "Login completed, but no auth user id was returned."
    };
  }

  const role = await getCurrentProfileRole(authUserId);

  return {
    dashboardPath: dashboardPathForRole(role),
    message: role ? undefined : "No profile role was found; using the player dashboard fallback.",
    role
  };
}

export async function registerWithEmailPassword(input: RegisterInput): Promise<AuthResult> {
  const supabase = requireSupabaseClient();
  const email = normalizeEmail(input.email);
  const nickname = normalizeRequiredText(input.nickname, "player");
  const displayName = normalizeOptionalText(input.displayName);
  const safeProfileRole = profileRoleForRegistration(input.requestedRole);
  const { data, error } = await supabase.auth.signUp({
    email,
    password: input.password,
    options: {
      data: {
        bio: normalizeOptionalText(input.bio),
        country_code: normalizeCountryCode(input.countryCode),
        display_name: displayName,
        desired_role: safeProfileRole,
        nickname,
        requested_role: input.requestedRole,
        steam_id: normalizeOptionalText(input.steamIdOrProfile),
        steam_id_or_profile: normalizeOptionalText(input.steamIdOrProfile)
      }
    }
  });

  if (error) {
    if (isRegistrationRateLimitError(error)) {
      throw new RegistrationRateLimitError();
    }

    throw new Error(registrationErrorMessage(error.message, email));
  }

  if (!data.user?.id || !data.session) {
    return {
      dashboardPath: "/login",
      message: "Check your email to confirm your account. After confirmation, log in to finish profile setup.",
      requiresEmailConfirmation: true
    };
  }

  const profilePayload = {
    auth_user_id: data.user.id,
    bio: normalizeOptionalText(input.bio),
    country_code: normalizeCountryCode(input.countryCode),
    display_name: displayName,
    nickname
  };

  let profileCreated = false;
  let profileSetupError: unknown = null;

  try {
    await postApiAuthenticated<BackendProfileResponse>(
      "/me/profile",
      createProfilePayload(input),
      data.session.access_token
    );
    profileCreated = true;
  } catch (caught) {
    profileSetupError = caught;

    try {
      const { error: profileError } = await supabase
        .from("profiles")
        .upsert(profilePayload, { onConflict: "auth_user_id" });

      if (profileError) {
        throw profileError;
      }

      profileCreated = true;
    } catch (fallbackError) {
      profileSetupError = fallbackError;
    }
  }

  await supabase.auth.signOut();

  if (!profileCreated) {
    return {
      dashboardPath: "/login",
      message: [
        "Account created, but profile setup could not be completed.",
        "Log in and open Profile to finish setup.",
        profileSetupError instanceof Error ? `Profile setup response: ${profileSetupError.message}` : null
      ]
        .filter(Boolean)
        .join(" ")
    };
  }

  return {
    dashboardPath: "/login",
    message:
      input.requestedRole === "player"
        ? "Account created. Log in to enter your dashboard."
        : input.requestedRole === "captain"
          ? "Account created. Team captain access is assigned through team ownership after login."
          : "Account created. Log in to enter your organizer dashboard."
  };
}

function registrationErrorMessage(message: string, email: string) {
  const normalized = message.toLowerCase();

  if (normalized.includes("invalid") && normalized.includes("email")) {
    return `Supabase rejected "${email}" as an invalid email address. Check for hidden spaces or use a normal inbox address.`;
  }

  if (normalized.includes("already") || normalized.includes("registered")) {
    return "An account with this email may already exist. Try logging in instead.";
  }

  if (normalized.includes("password")) {
    return `Password was rejected by Supabase: ${message}`;
  }

  if (normalized.includes("rate") || normalized.includes("too many")) {
    return "Too many registration attempts. Wait a moment, then try again.";
  }

  return message || "Supabase signup failed.";
}
