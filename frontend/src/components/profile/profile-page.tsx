"use client";

import {
  Camera,
  Gamepad2,
  Info,
  LogOut,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck,
  UserRound
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";

import { RouteLoadingSkeleton } from "@/components/route-loading-skeleton";
import {
  getCurrentUserProfile,
  signOutCurrentUser,
  startSteamProfileLink,
  updateCurrentUserProfile,
  uploadCurrentUserAvatar,
  type CurrentUserProfile,
  type ProfileRole
} from "@/lib/auth";
import { classNames } from "@/lib/utils";

interface ProfileFormState {
  bio: string;
  countryCode: string;
  displayName: string;
  nickname: string;
}

const roleStats: Record<
  "player" | "captain" | "organizer",
  Array<{ label: string; value: string; accent?: boolean }>
> = {
  captain: [
    { label: "Matches tracked", value: "1,248" },
    { label: "Team members", value: "05" },
    { label: "Tournaments joined", value: "14" },
    { accent: true, label: "Roster status", value: "Ready" }
  ],
  organizer: [
    { label: "Hosted tournaments", value: "14" },
    { label: "Registered teams", value: "96" },
    { label: "Live matches", value: "07" },
    { accent: true, label: "Analytics ready", value: "99.9%" }
  ],
  player: [
    { label: "Matches tracked", value: "1,248" },
    { label: "Teams joined", value: "14" },
    { label: "Tournaments played", value: "03" },
    { accent: true, label: "Analytics ready", value: "99.9%" }
  ]
};

function normalizeRole(role?: ProfileRole | null): "player" | "captain" | "organizer" {
  if (role === "captain" || role === "organizer") {
    return role;
  }

  return "player";
}

function roleLabel(role: ProfileRole) {
  if (role === "captain") {
    return "Team Captain";
  }

  if (role === "organizer") {
    return "Organizer";
  }

  if (role === "admin") {
    return "Admin";
  }

  return "Player";
}

function formStateFromProfile(profile: CurrentUserProfile): ProfileFormState {
  return {
    bio: profile.bio ?? "",
    countryCode: profile.countryCode ?? "",
    displayName: profile.displayName ?? "",
    nickname: profile.nickname
  };
}

function formatRegion(countryCode: string | null) {
  if (!countryCode) {
    return "EU WEST";
  }

  return countryCode.toUpperCase();
}

function formatLastUpdate(value: string | null) {
  if (!value) {
    return "Not synced yet";
  }

  return new Intl.DateTimeFormat("en", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  }).format(new Date(value));
}

export function ProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<CurrentUserProfile | null>(null);
  const [form, setForm] = useState<ProfileFormState>({
    bio: "",
    countryCode: "",
    displayName: "",
    nickname: ""
  });
  const [initialForm, setInitialForm] = useState<ProfileFormState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [isLinkingSteam, setIsLinkingSteam] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);
  const [avatarStatus, setAvatarStatus] = useState<string | null>(null);
  const avatarInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    let isMounted = true;

    getCurrentUserProfile()
      .then((loadedProfile) => {
        if (!isMounted) {
          return;
        }

        setProfile(loadedProfile);

        if (loadedProfile) {
          const nextForm = formStateFromProfile(loadedProfile);
          setForm(nextForm);
          setInitialForm(nextForm);

          if (new URLSearchParams(window.location.search).get("steamLogin") === "success") {
            setNotice("Steam account connected successfully.");
          }
        }
      })
      .catch((caught) => {
        if (isMounted) {
          setError(caught instanceof Error ? caught.message : "Profile could not be loaded.");
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    return () => {
      if (avatarPreviewUrl?.startsWith("blob:")) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
    };
  }, [avatarPreviewUrl]);

  const normalizedRole = normalizeRole(profile?.role);
  const stats = useMemo(() => roleStats[normalizedRole], [normalizedRole]);
  const steamConnected = Boolean(profile?.steamId);
  const opendotaStatus = profile?.opendotaProfileSyncedAt
    ? "Ready"
    : profile?.opendotaAccountId
      ? "Pending"
      : "Pending";
  const displayedAvatarUrl = avatarPreviewUrl ?? profile?.avatarUrl ?? null;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setIsSaving(true);

    try {
      const result = await updateCurrentUserProfile(form);
      const updatedProfile = result.profile;

      if (updatedProfile) {
        setProfile(updatedProfile);
        const nextForm = formStateFromProfile(updatedProfile);
        setForm(nextForm);
        setInitialForm(nextForm);
      }

      setNotice(result.message ?? "Profile saved successfully.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Profile update failed.");
    } finally {
      setIsSaving(false);
    }
  }

  function resetProfile() {
    if (initialForm) {
      setForm(initialForm);
      resetAvatarPreview();
      setNotice("Profile form reset.");
      setError(null);
    }
  }

  async function signOut() {
    setError(null);
    setNotice(null);
    setIsSigningOut(true);

    try {
      await signOutCurrentUser();
      router.push("/login");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Logout failed.");
      setIsSigningOut(false);
    }
  }

  async function reconnectSteam() {
    setError(null);
    setNotice(null);
    setIsLinkingSteam(true);

    try {
      window.location.assign(await startSteamProfileLink());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Steam connection could not be started.");
      setIsLinkingSteam(false);
    }
  }

  function openAvatarPicker() {
    setError(null);
    setNotice(null);
    avatarInputRef.current?.click();
  }

  async function handleAvatarFile(file: File | undefined) {
    if (!file) {
      return;
    }

    setError(null);
    setNotice(null);

    if (!file.type.startsWith("image/")) {
      setError("Avatar file must be an image.");
      return;
    }

    const maxSize = 5 * 1024 * 1024;

    if (file.size > maxSize) {
      setError("Avatar image must be 5MB or smaller.");
      return;
    }

    const localPreviewUrl = URL.createObjectURL(file);
    setAvatarPreviewUrl((current) => {
      if (current?.startsWith("blob:")) {
        URL.revokeObjectURL(current);
      }

      return localPreviewUrl;
    });
    setAvatarStatus("Uploading avatar preview...");
    setIsUploadingAvatar(true);

    try {
      const result = await uploadCurrentUserAvatar(file);

      if (result.avatarUrl) {
        setProfile((current) => (current ? { ...current, avatarUrl: result.avatarUrl } : current));
        setAvatarPreviewUrl((current) => {
          if (current?.startsWith("blob:")) {
            URL.revokeObjectURL(current);
          }

          return result.avatarUrl;
        });
      }

      setAvatarStatus(result.persisted ? "Avatar uploaded." : "Local avatar preview active.");
      setNotice(result.message);
    } catch (caught) {
      setAvatarStatus("Local avatar preview active.");
      setError(caught instanceof Error ? caught.message : "Avatar upload failed.");
    } finally {
      setIsUploadingAvatar(false);

      if (avatarInputRef.current) {
        avatarInputRef.current.value = "";
      }
    }
  }

  function resetAvatarPreview() {
    setAvatarPreviewUrl((current) => {
      if (current?.startsWith("blob:")) {
        URL.revokeObjectURL(current);
      }

      return null;
    });
    setAvatarStatus(null);
  }

  function handleOpenDotaSync() {
    setError(null);
    setNotice("OpenDota sync endpoint is not available yet.");
  }

  if (isLoading) {
    return <RouteLoadingSkeleton />;
  }

  if (!profile) {
    return (
      <section className="profile-state-card ops-panel">
        <h1>Login required</h1>
        <p>Profile access requires an active DotaOps account session.</p>
        <Link className="button button-primary ops-button-primary" href="/login">
          Login
        </Link>
      </section>
    );
  }

  return (
    <div className="profile-page">
      <section className="profile-hero ops-panel">
        <div className="profile-avatar-block">
          <div className="profile-avatar-frame">
            {displayedAvatarUrl ? (
              <div
                aria-label={`${profile.nickname} avatar`}
                className="profile-avatar-image"
                role="img"
                style={{ backgroundImage: `url(${displayedAvatarUrl})` }}
              />
            ) : (
              <div className="profile-avatar-placeholder">
                <UserRound size={78} />
              </div>
            )}
            <button
              aria-label="Change profile image"
              disabled={isUploadingAvatar}
              onClick={openAvatarPicker}
              title="Upload avatar image"
              type="button"
            >
              <Camera size={18} />
            </button>
            <input
              accept="image/*"
              hidden
              onChange={(event) => handleAvatarFile(event.target.files?.[0])}
              ref={avatarInputRef}
              type="file"
            />
          </div>
          {avatarStatus ? <p className="profile-avatar-status">{avatarStatus}</p> : null}
        </div>

        <div className="profile-hero-copy">
          <div className="profile-identity-line">
            <h1>{profile.nickname}</h1>
            <span className="profile-active-badge">
              <i />
              Profile active
            </span>
          </div>
          <div className="profile-hero-meta">
            <div>
              <span>Display Name</span>
              <strong>{profile.displayName || profile.nickname}</strong>
            </div>
            <div>
              <span>Region</span>
              <strong>{formatRegion(profile.countryCode)}</strong>
            </div>
            <div>
              <span>Operational Role</span>
              <strong className="profile-role-accent">{roleLabel(profile.role)}</strong>
            </div>
          </div>
        </div>
      </section>

      <section className="profile-stat-grid" aria-label="Profile statistics">
        {stats.map((stat) => (
          <article className={classNames("profile-stat-card ops-panel", stat.accent && "is-accent")} key={stat.label}>
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
          </article>
        ))}
      </section>

      <div className="profile-content-grid">
        <form className="profile-details-card ops-panel" id="profile-details-form" onSubmit={onSubmit}>
          <div className="profile-section-heading">
            <h2>Account Details</h2>
            <p>Manage your terminal identity and operational metadata.</p>
          </div>

          <div className="profile-form-grid">
            <label>
              <span>Nickname</span>
              <input
                onChange={(event) => setForm((current) => ({ ...current, nickname: event.target.value }))}
                required
                value={form.nickname}
              />
            </label>
            <label>
              <span>Display Name</span>
              <input
                onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
                value={form.displayName}
              />
            </label>
            <label>
              <span>Email Address (Read-only)</span>
              <input readOnly value={profile.email ?? "No email attached"} />
            </label>
            <label>
              <span>Country / Region</span>
              <input
                maxLength={24}
                onChange={(event) => setForm((current) => ({ ...current, countryCode: event.target.value }))}
                placeholder="EU"
                value={form.countryCode}
              />
            </label>
            <label className="profile-form-wide">
              <span>Steam Profile Link / Steam ID</span>
              <input
                readOnly
                value={
                  profile.steamId
                    ? `https://steamcommunity.com/profiles/${profile.steamId}`
                    : "Steam identity is not connected"
                }
              />
            </label>
            <label className="profile-form-wide">
              <span>Short Bio</span>
              <textarea
                onChange={(event) => setForm((current) => ({ ...current, bio: event.target.value }))}
                rows={5}
                value={form.bio}
              />
            </label>
          </div>

          <div className="profile-role-note">
            <Info size={20} />
            <p>
              <strong>Operational role: {roleLabel(profile.role)}</strong>
              <span>Role changes require administrative approval and are not editable from this profile screen.</span>
            </p>
          </div>

          {error ? <p className="profile-message profile-error">{error}</p> : null}
          {notice ? <p className="profile-message profile-success">{notice}</p> : null}

          <div className="profile-mobile-actions">
            <button className="button button-primary ops-button-primary" disabled={isSaving} type="submit">
              <Save size={18} />
              {isSaving ? "Saving..." : "Save Changes"}
            </button>
            <button className="button button-secondary" onClick={resetProfile} type="button">
              <RotateCcw size={18} />
              Reset Profile
            </button>
          </div>
        </form>

        <aside className="profile-side-rail">
          <section className="profile-rail-card ops-panel profile-connected-accounts-card">
            <h2>Connected Accounts</h2>
            <article className="profile-connected-card">
              <Gamepad2 size={30} />
              <div>
                <strong>Steam Profile: {steamConnected ? "Connected" : "Not Connected"}</strong>
                <span>{steamConnected ? profile.steamId : "No SteamID64 linked"}</span>
              </div>
              <button disabled={isLinkingSteam} onClick={reconnectSteam} type="button">
                {isLinkingSteam ? "Connecting..." : steamConnected ? "Reconnect Steam" : "Connect Steam"}
              </button>
            </article>
            <article className="profile-connected-card">
              <RefreshCw size={28} />
              <div>
                <strong>OpenDota Sync: {opendotaStatus}</strong>
                <span>{profile.opendotaAccountId ? `Synced ID: ${profile.opendotaAccountId}` : "Waiting for Steam link"}</span>
              </div>
              <button
                onClick={handleOpenDotaSync}
                type="button"
              >
                Sync now
              </button>
            </article>
          </section>

          <section className="profile-rail-card ops-panel profile-actions-card">
            <h2>Profile Actions</h2>
            <button className="profile-save-button" disabled={isSaving} form="profile-details-form" type="submit">
              <Save size={18} />
              {isSaving ? "Saving..." : "Save Changes"}
            </button>
            <button onClick={resetProfile} type="button">
              <RotateCcw size={18} />
              Reset Profile
            </button>
            <button className="profile-logout-button" disabled={isSigningOut} onClick={signOut} type="button">
              <LogOut size={18} />
              {isSigningOut ? "Terminating..." : "Terminate Session / Log Out"}
            </button>
          </section>

          <section className="profile-security-card ops-panel">
            <ShieldCheck size={24} />
            <div>
              <h2>Security Status</h2>
              <p>Account Status: Protected</p>
              <p>Session: Active</p>
              <p>Auth Type: Password Enabled</p>
              <p>Last Update: {formatLastUpdate(profile.updatedAt)}</p>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
