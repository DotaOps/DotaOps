"use client";

import { ArrowDown, ArrowRight, RadioTower, ShieldCheck, SkipForward, Swords } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";

import { getCurrentUserProfile, hasAuthenticatedSession, type ProfileRole } from "@/lib/auth";
import { safeLocalRedirectPath } from "@/lib/route-access";

type PortalState = "checking" | "error" | "ready";
type PortalStage = 1 | 2 | 3;

const PORTAL_AUTO_EXIT_DELAY_MS = 760;
const PORTAL_EXIT_ANIMATION_MS = 640;
const PORTAL_STEPS = [
  "AUTH SESSION VERIFIED",
  "OPERATOR PROFILE SYNCHRONIZED",
  "WORKSPACE CHANNEL ESTABLISHED"
];

function finalStatusForRole(role: ProfileRole | null) {
  if (role === "admin") {
    return "Admin channel secured";
  }

  if (role === "organizer") {
    return "Tournament workspace online";
  }

  if (role === "player" || role === "captain") {
    return "Player hub synchronized";
  }

  return "Workspace synchronized";
}

export function PortalEntryExperience({ nextPath }: { nextPath?: string }) {
  const destination = useMemo(() => safeLocalRedirectPath(nextPath, "/dashboard"), [nextPath]);
  const [isReducedMotion, setIsReducedMotion] = useState(false);
  const [portalState, setPortalState] = useState<PortalState>("checking");
  const [profileRole, setProfileRole] = useState<ProfileRole | null>(null);
  const [progress, setProgress] = useState(0);
  const [stage, setStage] = useState<PortalStage>(1);
  const [isExiting, setIsExiting] = useState(false);
  const portalRootRef = useRef<HTMLElement | null>(null);
  const autoExitTimerRef = useRef<number | null>(null);
  const redirectTimerRef = useRef<number | null>(null);

  const beginWorkspaceEntry = useCallback(() => {
    if (isExiting || redirectTimerRef.current) {
      return;
    }

    setIsExiting(true);
    redirectTimerRef.current = window.setTimeout(
      () => window.location.replace(destination),
      isReducedMotion ? 90 : PORTAL_EXIT_ANIMATION_MS
    );
  }, [destination, isExiting, isReducedMotion]);

  const scrollToStage = useCallback((targetStage: PortalStage) => {
    const root = portalRootRef.current;

    if (!root) {
      return;
    }

    const targetProgress = targetStage === 1 ? 0 : targetStage === 2 ? 0.48 : 1;
    const scrollDistance = root.offsetHeight - window.innerHeight;

    window.scrollTo({
      behavior: "smooth",
      top: root.offsetTop + scrollDistance * targetProgress
    });
  }, []);

  useEffect(() => {
    let isMounted = true;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    async function preparePortal() {
      try {
        const hasSession = await hasAuthenticatedSession();

        if (!hasSession) {
          window.location.replace(`/login?next=${encodeURIComponent(destination)}`);
          return;
        }

        if (!isMounted) {
          return;
        }

        setIsReducedMotion(reducedMotion);

        let role: ProfileRole | null = null;

        try {
          role = (await getCurrentUserProfile())?.role ?? null;
        } catch {
          // The session is sufficient for entry. Profile copy falls back to a generic status.
        }

        if (!isMounted) {
          return;
        }

        setProfileRole(role);
        setPortalState("ready");

        if (reducedMotion) {
          setStage(3);
          setProgress(1);
          return;
        }
      } catch {
        if (isMounted) {
          setProgress(1);
          setStage(3);
          setPortalState("error");
        }
      }
    }

    void preparePortal();

    return () => {
      isMounted = false;
    };
  }, [destination]);

  useEffect(() => {
    return () => {
      if (autoExitTimerRef.current) {
        window.clearTimeout(autoExitTimerRef.current);
      }

      if (redirectTimerRef.current) {
        window.clearTimeout(redirectTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (portalState !== "ready" || isReducedMotion) {
      return;
    }

    let animationFrame: number | null = null;

    function paintScrollProgress() {
      animationFrame = null;
      const root = portalRootRef.current;

      if (!root) {
        return;
      }

      const scrollDistance = Math.max(root.offsetHeight - window.innerHeight, 1);
      const nextProgress = Math.min(Math.max((window.scrollY - root.offsetTop) / scrollDistance, 0), 1);
      const nextStage: PortalStage = nextProgress < 0.28 ? 1 : nextProgress < 0.78 ? 2 : 3;

      setProgress(nextProgress);
      setStage(nextStage);
    }

    function requestScrollUpdate() {
      if (animationFrame === null) {
        animationFrame = window.requestAnimationFrame(paintScrollProgress);
      }
    }

    paintScrollProgress();
    window.addEventListener("scroll", requestScrollUpdate, { passive: true });
    window.addEventListener("resize", requestScrollUpdate);

    return () => {
      window.removeEventListener("scroll", requestScrollUpdate);
      window.removeEventListener("resize", requestScrollUpdate);

      if (animationFrame !== null) {
        window.cancelAnimationFrame(animationFrame);
      }
    };
  }, [isReducedMotion, portalState]);

  useEffect(() => {
    if (portalState !== "ready" || isReducedMotion || stage !== 3 || isExiting) {
      if (autoExitTimerRef.current) {
        window.clearTimeout(autoExitTimerRef.current);
        autoExitTimerRef.current = null;
      }

      return;
    }

    autoExitTimerRef.current = window.setTimeout(beginWorkspaceEntry, PORTAL_AUTO_EXIT_DELAY_MS);

    return () => {
      if (autoExitTimerRef.current) {
        window.clearTimeout(autoExitTimerRef.current);
        autoExitTimerRef.current = null;
      }
    };
  }, [beginWorkspaceEntry, isExiting, isReducedMotion, portalState, stage]);

  const finalStatus = finalStatusForRole(profileRole);
  const visualProgress = isReducedMotion ? 1 : progress;
  const portalScale = 1 + visualProgress * 4.2;
  const tunnelOpacity = Math.min(Math.max((visualProgress - 0.18) * 2.4, 0), 1);
  const exitOpacity = Math.min(Math.max((visualProgress - 0.76) * 4.6, 0), 1);
  const entranceOpacity = Math.min(Math.max((0.34 - visualProgress) * 6, 0), 1);
  const tunnelCopyOpacity = Math.min(Math.max((visualProgress - 0.34) * 7, 0), 1) * Math.min(Math.max((0.79 - visualProgress) * 7, 0), 1);
  const radarOpacity = stage === 3 ? Math.max(exitOpacity, 0.35) : 1;
  const exitPortalScale = portalScale + (isExiting ? 6 : 0);
  const progressPercent = Math.round(visualProgress * 100);

  return (
    <main
      className={`portal-entry-scroll portal-entry-stage-${stage}${isExiting ? " is-exiting" : ""}`}
      ref={portalRootRef}
      style={{ "--portal-progress": visualProgress } as CSSProperties}
    >
      <section aria-live="polite" className="portal-entry-sticky">
        <div aria-hidden="true" className="portal-entry-backdrop">
          <div className="portal-entry-grid" />
          <div className="portal-entry-scanline" />
          <div className="portal-entry-tunnel" style={{ opacity: tunnelOpacity }}>
            {Array.from({ length: 10 }, (_, index) => <span key={index} />)}
          </div>
          <div className="portal-entry-packets" style={{ opacity: tunnelOpacity }}>
            {Array.from({ length: 16 }, (_, index) => <i key={index} />)}
          </div>
        </div>

        <header className="portal-entry-topbar">
          <span className="portal-entry-brand">
            <RadioTower size={15} />
            DotaOps secure uplink
          </span>
          <button className="portal-entry-skip" onClick={beginWorkspaceEntry} type="button">
            <SkipForward size={15} />
            Skip transition
          </button>
        </header>

        <div className="portal-entry-stage-indicator">
          <strong>0{stage}</strong>
          <span>/ 03</span>
          <i>
            <b />
          </i>
        </div>

        <div className="portal-entry-viewport">
          <div
            aria-hidden="true"
            className="portal-entry-radar"
            style={{ opacity: radarOpacity, transform: `translate(-50%, -50%) scale(${exitPortalScale})` }}
          >
            <span />
            <span />
            <span />
            <div className="portal-entry-core">
              <Swords size={34} />
            </div>
          </div>

          <div className="portal-entry-bracket portal-entry-bracket-left" aria-hidden="true">
            <span>AUTH://LINK</span>
            <span>DATA://STREAM</span>
            <span>NODE://WORKSPACE</span>
          </div>
          <div className="portal-entry-bracket portal-entry-bracket-right" aria-hidden="true">
            <span>PACKETS {progressPercent.toString().padStart(3, "0")}%</span>
            <span>SIGNAL STABLE</span>
            <span>OPS://ONLINE</span>
          </div>

          <div className="portal-entry-stage-copy portal-entry-ignition-copy" style={{ opacity: entranceOpacity }}>
            <p>Stage 01 / Portal ignition</p>
            <h1>Session linked</h1>
            <span>Authenticated workspace channel is standing by.</span>
            <button onClick={() => scrollToStage(2)} type="button">
              Enter portal
              <ArrowDown size={16} />
            </button>
          </div>

          <div className="portal-entry-stage-copy portal-entry-tunnel-copy" style={{ opacity: tunnelCopyOpacity }}>
            <p>Stage 02 / Data tunnel</p>
            <h2>Routing operator signal</h2>
            <span>Match telemetry and workspace permissions are synchronizing.</span>
            <button onClick={() => scrollToStage(3)} type="button">
              Continue
              <ArrowDown size={16} />
            </button>
          </div>

          <div className="portal-entry-stage-copy portal-entry-exit-copy" style={{ opacity: exitOpacity }}>
            <p>Stage 03 / Exit portal</p>
            <h2>{portalState === "error" ? "Workspace entry ready" : finalStatus}</h2>
            <span>
              {portalState === "error"
                ? "The visual transition could not finish. Continue to your protected workspace."
                : "DotaOps operations channel established."}
            </span>
            <button onClick={beginWorkspaceEntry} type="button">
              Enter dashboard
              <ArrowRight size={16} />
            </button>
          </div>
        </div>

        <div aria-hidden="true" className="portal-entry-exit-wash">
          <span />
          <strong>Entering workspace</strong>
        </div>

        <footer className="portal-entry-footer">
          <span className={`portal-entry-session portal-entry-session-${portalState}`}>
            <ShieldCheck size={15} />
            {portalState === "checking"
              ? "Verifying session"
              : portalState === "error"
                ? "Fallback channel ready"
                : PORTAL_STEPS[Math.min(stage - 1, PORTAL_STEPS.length - 1)]}
          </span>
          <span>{isReducedMotion ? "Reduced motion mode" : "Scroll to traverse portal"}</span>
        </footer>
      </section>
    </main>
  );
}
