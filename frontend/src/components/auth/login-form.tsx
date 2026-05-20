"use client";

import { Info, KeyRound, LogIn, Mail, RadioTower } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";

import { getCurrentUserProfile, loginWithEmailPassword } from "@/lib/auth";

async function waitForAuthenticatedProfile(timeoutMs = 1500) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    try {
      const profile = await getCurrentUserProfile();

      if (profile) {
        return profile;
      }
    } catch {
      // Retry until the session is visible or the timeout is reached.
    }

    await new Promise((resolve) => window.setTimeout(resolve, 50));
  }

  return null;
}

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setIsLoading(true);

    try {
      const result = await loginWithEmailPassword({ email, password });
      await waitForAuthenticatedProfile();
      setNotice(result.message ?? "Dashboard uplink prepared.");
      router.replace(result.dashboardPath);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Login failed.");
      setIsLoading(false);
    }
  }

  return (
    <main className="auth-login-main">
      <section className="auth-card auth-login-card">
        <div className="auth-card-line" />
        <div className="auth-card-heading">
          <h1>Login to DotaOps</h1>
          <p>Enter the operations hub and continue your tournament workflow.</p>
        </div>

        <form className="auth-form" onSubmit={onSubmit}>
          <label>
            <span>Operator Email</span>
            <div className="auth-input-wrap">
              <Mail size={20} />
              <input
                autoComplete="email"
                onChange={(event) => setEmail(event.target.value)}
                placeholder="operator@dotaops.com"
                required
                type="email"
                value={email}
              />
            </div>
          </label>

          <label>
            <span className="auth-label-row">
              Access Key
              <small>Forgot password?</small>
            </span>
            <div className="auth-input-wrap">
              <KeyRound size={20} />
              <input
                autoComplete="current-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="............"
                required
                type="password"
                value={password}
              />
            </div>
          </label>

          <label className="auth-checkbox">
            <input
              checked={remember}
              onChange={(event) => setRemember(event.target.checked)}
              type="checkbox"
            />
            <span>Remember this terminal session</span>
          </label>

          {error ? <p className="auth-message auth-error">{error}</p> : null}
          {notice ? <p className="auth-message auth-success">{notice}</p> : null}

          <button className="auth-submit" disabled={isLoading} type="submit">
            <LogIn size={20} />
            {isLoading ? "Connecting..." : "Login"}
          </button>
        </form>

        <div className="auth-card-secondary">
          <p>
            New operator? <Link href="/register">Create account</Link>
          </p>
          <div>
            <Info size={18} />
            <span>
              Public visitors can still <Link href="/turnirji">view tournaments</Link> without
              logging in.
            </span>
          </div>
        </div>
      </section>

      <div className="auth-uplink-row">
        <span>
          <i />
          <i />
          <i />
          Dashboard uplink prepared
        </span>
        <span>
          Latency: 24ms <RadioTower size={14} />
        </span>
      </div>

      {isLoading ? <div className="auth-loading-overlay">Preparing tactical dashboard...</div> : null}
    </main>
  );
}
