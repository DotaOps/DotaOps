"use client";

import { BarChart3, Gamepad2, Shield, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";

import {
  registerWithEmailPassword,
  RegistrationRateLimitError,
  type RequestedAuthRole
} from "@/lib/auth";
import { classNames } from "@/lib/utils";

const roleOptions: Array<{
  role: RequestedAuthRole;
  label: string;
  detail: string;
  icon: LucideIcon;
}> = [
  {
    detail: "Create or join teams. Team captain status is assigned after creating or managing a team.",
    icon: Gamepad2,
    label: "Player",
    role: "player"
  },
  {
    detail: "Create and manage tournaments.",
    icon: Trophy,
    label: "Organizer",
    role: "organizer"
  }
];

type RegisterField =
  | "nickname"
  | "displayName"
  | "email"
  | "password"
  | "confirmPassword"
  | "countryCode"
  | "bio";

type RegisterErrors = Partial<Record<RegisterField, string>>;

function normalizedEmail(value: string) {
  return value.trim().toLowerCase();
}

function validateRegisterForm(input: {
  bio: string;
  confirmPassword: string;
  countryCode: string;
  displayName: string;
  email: string;
  nickname: string;
  password: string;
}) {
  const errors: RegisterErrors = {};
  const email = normalizedEmail(input.email);
  const nickname = input.nickname.trim();
  const displayName = input.displayName.trim();
  const countryCode = input.countryCode.trim();

  if (!nickname) {
    errors.nickname = "Nickname is required.";
  } else if (nickname.length < 2 || nickname.length > 40) {
    errors.nickname = "Nickname must be 2-40 characters.";
  }

  if (!displayName) {
    errors.displayName = "Display name is required.";
  } else if (displayName.length > 80) {
    errors.displayName = "Display name must be 80 characters or fewer.";
  }

  if (!email) {
    errors.email = "Email is required.";
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = "Enter a valid email address.";
  }

  if (!input.password) {
    errors.password = "Password is required.";
  } else if (input.password.length < 6) {
    errors.password = "Password must be at least 6 characters.";
  }

  if (input.confirmPassword !== input.password) {
    errors.confirmPassword = "Passwords do not match.";
  }

  if (countryCode && !/^[A-Za-z]{2}$/.test(countryCode)) {
    errors.countryCode = "Use a 2-letter country or region code.";
  }

  if (input.bio.trim().length > 500) {
    errors.bio = "Short bio must be 500 characters or fewer.";
  }

  return errors;
}

export function RegisterForm() {
  const router = useRouter();
  const [nickname, setNickname] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [countryCode, setCountryCode] = useState("");
  const [steamIdOrProfile, setSteamIdOrProfile] = useState("");
  const [bio, setBio] = useState("");
  const [requestedRole, setRequestedRole] = useState<RequestedAuthRole>("player");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<RegisterErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);

  useEffect(() => {
    if (cooldownSeconds <= 0) {
      return;
    }

    const timer = window.setInterval(() => {
      setCooldownSeconds((current) => Math.max(0, current - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [cooldownSeconds]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setNotice(null);

    const validationErrors = validateRegisterForm({
      bio,
      confirmPassword,
      countryCode,
      displayName,
      email,
      nickname,
      password
    });

    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      setError("Check the highlighted fields before registering.");
      return;
    }

    if (cooldownSeconds > 0) {
      setError("Too many registration attempts. Please wait a few minutes before trying again.");
      return;
    }

    setIsLoading(true);

    try {
      const result = await registerWithEmailPassword({
        bio: bio.trim(),
        countryCode: countryCode.trim(),
        displayName: displayName.trim(),
        email: normalizedEmail(email),
        nickname: nickname.trim(),
        password,
        requestedRole,
        steamIdOrProfile: steamIdOrProfile.trim()
      });
      setNotice(result.message ?? "Account created.");
      setIsLoading(false);

      if (!result.requiresEmailConfirmation) {
        window.setTimeout(() => {
          router.push(result.dashboardPath);
        }, 900);
      }
    } catch (caught) {
      if (caught instanceof RegistrationRateLimitError) {
        setCooldownSeconds(caught.retryAfterSeconds);
      }

      setError(caught instanceof Error ? caught.message : "Registration failed.");
      setIsLoading(false);
    }
  }

  return (
    <main className="auth-register-main">
      <section className="auth-register-copy">
        <span>Dominate the Draft</span>
        <h1>
          Create your <strong>DotaOps account</strong>
        </h1>
        <p>
          Join the elite analytical engine for professional teams and tournament organizers. Access
          tactical prediction models and full-spectrum match telemetry.
        </p>
        <div className="auth-register-benefits">
          <article>
            <BarChart3 size={30} />
            <h2>Real-time Data</h2>
            <p>Synchronized telemetry from all active match clusters.</p>
          </article>
          <article>
            <Shield size={30} />
            <h2>Secure Core</h2>
            <p>Enterprise-grade encryption for team roster and strategy locks.</p>
          </article>
        </div>
      </section>

      <section className="auth-card auth-register-card">
        <form className="auth-form auth-register-form" onSubmit={onSubmit}>
          <div className="auth-form-grid">
            <label>
              <span>Username / Nickname</span>
              <input
                aria-invalid={Boolean(fieldErrors.nickname)}
                onChange={(event) => setNickname(event.target.value)}
                placeholder="Terminal ID"
                required
                value={nickname}
              />
              {fieldErrors.nickname ? (
                <small className="auth-field-error">{fieldErrors.nickname}</small>
              ) : null}
            </label>
            <label>
              <span>Display Name</span>
              <input
                aria-invalid={Boolean(fieldErrors.displayName)}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="Operative Name"
                required
                value={displayName}
              />
              {fieldErrors.displayName ? (
                <small className="auth-field-error">{fieldErrors.displayName}</small>
              ) : null}
            </label>
            <label className="auth-form-wide">
              <span>Email Address</span>
              <input
                aria-invalid={Boolean(fieldErrors.email)}
                autoComplete="email"
                onChange={(event) => setEmail(event.target.value)}
                placeholder="hq@network.ops"
                required
                type="email"
                value={email}
              />
              {fieldErrors.email ? (
                <small className="auth-field-error">{fieldErrors.email}</small>
              ) : null}
            </label>
            <label>
              <span>Password</span>
              <input
                aria-invalid={Boolean(fieldErrors.password)}
                autoComplete="new-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="............"
                required
                type="password"
                value={password}
              />
              {fieldErrors.password ? (
                <small className="auth-field-error">{fieldErrors.password}</small>
              ) : null}
            </label>
            <label>
              <span>Confirm Password</span>
              <input
                aria-invalid={Boolean(fieldErrors.confirmPassword)}
                autoComplete="new-password"
                onChange={(event) => setConfirmPassword(event.target.value)}
                placeholder="............"
                required
                type="password"
                value={confirmPassword}
              />
              {fieldErrors.confirmPassword ? (
                <small className="auth-field-error">{fieldErrors.confirmPassword}</small>
              ) : null}
            </label>
          </div>

          <div className="auth-optional-section">
            <span>Optional Profile Details</span>
            <div className="auth-form-grid">
              <label>
                <span>Country / Region</span>
                <input
                  aria-invalid={Boolean(fieldErrors.countryCode)}
                  maxLength={24}
                  onChange={(event) => setCountryCode(event.target.value)}
                  placeholder="Sector"
                  value={countryCode}
                />
                {fieldErrors.countryCode ? (
                  <small className="auth-field-error">{fieldErrors.countryCode}</small>
                ) : null}
              </label>
              <label>
                <span>Steam ID / Profile Link</span>
                <input
                  onChange={(event) => setSteamIdOrProfile(event.target.value)}
                  placeholder="steamcommunity.com/id/..."
                  value={steamIdOrProfile}
                />
              </label>
              <label className="auth-form-wide">
                <span>Short Bio</span>
                <textarea
                  aria-invalid={Boolean(fieldErrors.bio)}
                  onChange={(event) => setBio(event.target.value)}
                  placeholder="Brief operative history..."
                  rows={4}
                  value={bio}
                />
                {fieldErrors.bio ? (
                  <small className="auth-field-error">{fieldErrors.bio}</small>
                ) : null}
              </label>
            </div>
          </div>

          <div className="auth-role-section">
            <span>Operational Role</span>
            <div className="auth-role-grid">
              {roleOptions.map((option) => {
                const Icon = option.icon;

                return (
                  <button
                    className={classNames(
                      "auth-role-option",
                      requestedRole === option.role && "is-selected"
                    )}
                    key={option.role}
                    onClick={() => setRequestedRole(option.role)}
                    type="button"
                  >
                    <Icon size={26} />
                    <strong>{option.label}</strong>
                    <small>{option.detail}</small>
                  </button>
                );
              })}
            </div>
            <p>
              Note: team captain access follows team ownership. Organizer access can be enabled on
              the same login when the email already belongs to your DotaOps account.
            </p>
          </div>

          {error ? <p className="auth-message auth-error">{error}</p> : null}
          {cooldownSeconds > 0 ? (
            <p className="auth-message auth-cooldown" aria-live="polite">
              Registration is temporarily locked. Try again in {cooldownSeconds}s.
            </p>
          ) : null}
          {notice ? <p className="auth-message auth-success">{notice}</p> : null}

          <button
            className="auth-submit auth-register-submit"
            disabled={isLoading || cooldownSeconds > 0}
            type="submit"
          >
            {isLoading
              ? "Registering..."
              : cooldownSeconds > 0
                ? `Retry in ${cooldownSeconds}s`
                : "Register"}
          </button>

          <p className="auth-switch-link">
            Already have an account? <Link href="/login">Login</Link>
          </p>
        </form>
      </section>
    </main>
  );
}
