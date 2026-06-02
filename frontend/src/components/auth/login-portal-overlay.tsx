"use client";

import { gsap } from "gsap";
import { RadioTower, SkipForward, Swords } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

interface LoginPortalOverlayProps {
  destination: string;
  onComplete: (destination: string) => void;
  onSkip: (destination: string) => void;
  role?: string | null;
}

const SUCTION_RING_COUNT = 6;
const TUNNEL_RING_COUNT = 10;
const SIGNAL_PACKET_COUNT = 18;
const ENERGY_ARC_COUNT = 4;
const MIN_TELEPORT_MS = 5200;
const OVERLAY_SUPPRESSION_MS = 7600;

function suppressGlobalLoadingOverlay(durationMs = OVERLAY_SUPPRESSION_MS) {
  document.documentElement.dataset.portalHandoff = "active";

  window.setTimeout(() => {
    if (document.documentElement.dataset.portalHandoff === "active") {
      delete document.documentElement.dataset.portalHandoff;
    }
  }, durationMs);
}

function workspaceCopyForRole(role?: string | null) {
  if (role === "organizer") {
    return "Tournament workspace online";
  }

  if (role === "admin") {
    return "Admin channel secured";
  }

  if (role === "player" || role === "captain") {
    return "Player hub synchronized";
  }

  return "Workspace synchronized";
}

export function LoginPortalOverlay({
  destination,
  onComplete,
  onSkip,
  role
}: LoginPortalOverlayProps) {
  const overlayRef = useRef<HTMLElement>(null);
  const backdropRef = useRef<HTMLDivElement>(null);
  const gravityLinesRef = useRef<HTMLDivElement>(null);
  const singularityRef = useRef<HTMLDivElement>(null);
  const coreRef = useRef<HTMLElement>(null);
  const tunnelRef = useRef<HTMLDivElement>(null);
  const ignitionCopyRef = useRef<HTMLDivElement>(null);
  const gravityCopyRef = useRef<HTMLDivElement>(null);
  const routingCopyRef = useRef<HTMLDivElement>(null);
  const syncCopyRef = useRef<HTMLDivElement>(null);
  const exitCopyRef = useRef<HTMLDivElement>(null);
  const explosionRef = useRef<HTMLDivElement>(null);
  const timelineRef = useRef<gsap.core.Timeline | null>(null);
  const completionTimerRef = useRef<number | null>(null);
  const startedAtRef = useRef<number | null>(null);
  const prefersReducedMotionRef = useRef(false);
  const isFinishedRef = useRef(false);
  const [stage, setStage] = useState(1);

  const navigateToDestination = useCallback(() => {
    if (isFinishedRef.current) {
      return;
    }

    isFinishedRef.current = true;
    suppressGlobalLoadingOverlay(1200);
    onComplete(destination);
  }, [destination, onComplete]);

  const finishTeleport = useCallback(() => {
    if (isFinishedRef.current || completionTimerRef.current !== null) {
      return;
    }

    const startedAt = startedAtRef.current ?? performance.now();
    const elapsed = performance.now() - startedAt;
    const remaining = prefersReducedMotionRef.current ? 0 : Math.max(0, MIN_TELEPORT_MS - elapsed);

    if (remaining > 0) {
      completionTimerRef.current = window.setTimeout(() => {
        completionTimerRef.current = null;
        navigateToDestination();
      }, remaining);
      return;
    }

    navigateToDestination();
  }, [navigateToDestination]);

  const skipHandoff = useCallback(() => {
    if (isFinishedRef.current) {
      return;
    }

    if (completionTimerRef.current !== null) {
      window.clearTimeout(completionTimerRef.current);
      completionTimerRef.current = null;
    }

    timelineRef.current?.kill();
    isFinishedRef.current = true;
    suppressGlobalLoadingOverlay(1200);
    onSkip(destination);
  }, [destination, onSkip]);

  useEffect(() => {
    suppressGlobalLoadingOverlay();
    startedAtRef.current = performance.now();
    prefersReducedMotionRef.current = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const context = gsap.context(() => {
      const suctionRings = gsap.utils.toArray<HTMLElement>(".login-portal-suction-ring");
      const tunnelRings = gsap.utils.toArray<HTMLElement>(".login-portal-tunnel-ring");
      const packets = gsap.utils.toArray<HTMLElement>(".login-portal-signal-packet");
      const arcs = gsap.utils.toArray<HTMLElement>(".login-portal-energy-arc");
      const loginScene = Array.from(document.querySelectorAll<HTMLElement>(".auth-login-card, .auth-uplink-row"));
      const phaseCopy = [
        ignitionCopyRef.current,
        gravityCopyRef.current,
        routingCopyRef.current,
        syncCopyRef.current,
        exitCopyRef.current
      ];
      const timeline = gsap.timeline({
        onComplete: finishTeleport
      });

      timelineRef.current = timeline;

      if (prefersReducedMotionRef.current) {
        setStage(4);
        timeline
          .set(overlayRef.current, { autoAlpha: 1 })
          .set(exitCopyRef.current, { autoAlpha: 1, y: 0 })
          .to(backdropRef.current, { opacity: 1, duration: 0.12 })
          .to(explosionRef.current, { autoAlpha: 0.74, duration: 0.18 });
        return;
      }

      const packetRadius = Math.max(window.innerWidth, window.innerHeight) * 0.64;
      const packetOffset = (index: number, direction: "x" | "y") => {
        const angle = (index / SIGNAL_PACKET_COUNT) * Math.PI * 2;
        return (direction === "x" ? Math.cos(angle) : Math.sin(angle)) * packetRadius;
      };

      timeline
        .set(overlayRef.current, { autoAlpha: 1 })
        .set(phaseCopy, { autoAlpha: 0, y: 12 })
        .set(gravityLinesRef.current, { autoAlpha: 0, rotation: -16, scale: 1.28 })
        .set(singularityRef.current, { autoAlpha: 0, scale: 0.18 })
        .set(coreRef.current, { autoAlpha: 0, scale: 0.32 })
        .set(tunnelRef.current, { autoAlpha: 0, scale: 1.24 })
        .set(suctionRings, { autoAlpha: 0, scale: 1.92 })
        .set(tunnelRings, { autoAlpha: 0, scale: 4.8 })
        .set(arcs, { autoAlpha: 0, rotation: (index) => (index * 82) - 18, scale: 0.64 })
        .set(loginScene, { transformOrigin: "50% 50%" })
        .set(packets, {
          autoAlpha: 0,
          scale: 1.32,
          x: (index) => packetOffset(index, "x"),
          y: (index) => packetOffset(index, "y")
        })
        .set(explosionRef.current, { autoAlpha: 0, scale: 0.12 })

        // 01 / 04: establish the session before the singularity appears.
        .to(backdropRef.current, { opacity: 1, duration: 0.54, ease: "power1.out" }, 0)
        .to(ignitionCopyRef.current, { autoAlpha: 1, duration: 0.28, ease: "power1.out", y: 0 }, 0.14)
        .to(gravityLinesRef.current, { autoAlpha: 0.32, duration: 0.5, ease: "power1.out" }, 0.28)
        .to(ignitionCopyRef.current, { autoAlpha: 0, duration: 0.18, ease: "power1.in", y: -9 }, 0.68)

        // 02 / 04: open the gravity well and pull the login scene inward.
        .call(() => setStage(2), [], 0.8)
        .to(gravityCopyRef.current, { autoAlpha: 1, duration: 0.28, ease: "power1.out", y: 0 }, 0.82)
        .to(singularityRef.current, { autoAlpha: 1, duration: 0.64, ease: "back.out(1.45)", scale: 1 }, 0.82)
        .to(coreRef.current, { autoAlpha: 1, duration: 0.48, ease: "power2.out", scale: 1 }, 0.94)
        .to(suctionRings, { autoAlpha: 0.72, duration: 0.82, ease: "power2.in", scale: 0.42, stagger: 0.08 }, 0.98)
        .to(packets, { autoAlpha: 0.78, duration: 1.06, ease: "power2.in", scale: 0.12, stagger: 0.035, x: 0, y: 0 }, 1.0)
        .to(gravityLinesRef.current, { autoAlpha: 0.84, duration: 1.02, ease: "power2.in", rotation: 42, scale: 0.64 }, 1.0)
        .to(loginScene, { duration: 0.92, ease: "power2.in", opacity: 0.46, scale: 0.86 }, 1.0)
        .to(gravityCopyRef.current, { autoAlpha: 0, duration: 0.2, ease: "power1.in", y: -10 }, 1.82)

        // 03 / 04: collapse the tunnel around the operator signal.
        .call(() => setStage(3), [], 2.0)
        .to(routingCopyRef.current, { autoAlpha: 1, duration: 0.28, ease: "power1.out", y: 0 }, 2.02)
        .to(tunnelRef.current, { autoAlpha: 1, duration: 0.32, ease: "power1.out", scale: 1 }, 2.0)
        .to(tunnelRings, { autoAlpha: 0.76, duration: 1.34, ease: "power2.in", scale: 0.12, stagger: 0.1 }, 2.04)
        .to(packets, {
          autoAlpha: 0.82,
          duration: 1.34,
          ease: "power2.in",
          scale: 0.06,
          stagger: 0.025,
          x: 0,
          y: 0
        }, 2.1)
        .to(gravityLinesRef.current, { autoAlpha: 0.96, duration: 1.22, ease: "power1.in", rotation: 118, scale: 0.22 }, 2.12)
        .to(loginScene, { duration: 1.32, ease: "power3.in", opacity: 0, scale: 0.16 }, 2.14)
        .to(singularityRef.current, { duration: 1.3, ease: "sine.inOut", scale: 1.36 }, 2.18)
        .to(routingCopyRef.current, { autoAlpha: 0, duration: 0.2, ease: "power1.in", y: -10 }, 3.42)

        // 04 / 04: charge the singularity, then release the energy wash.
        .call(() => setStage(4), [], 3.6)
        .to(syncCopyRef.current, { autoAlpha: 1, duration: 0.3, ease: "power1.out", y: 0 }, 3.62)
        .to(arcs, { autoAlpha: 0.86, duration: 0.86, ease: "power2.out", rotation: (index) => (index * 82) + 88, scale: 1.26, stagger: 0.08 }, 3.64)
        .to(coreRef.current, { autoAlpha: 1, duration: 0.84, ease: "power2.in", scale: 1.78 }, 3.7)
        .to(singularityRef.current, { autoAlpha: 0.96, duration: 0.84, ease: "power2.in", scale: 1.72 }, 3.78)
        .to(syncCopyRef.current, { autoAlpha: 0, duration: 0.2, ease: "power1.in", y: -10 }, 4.58)
        .to(exitCopyRef.current, { autoAlpha: 1, duration: 0.24, ease: "power1.out", y: 0 }, 4.78)
        .to(explosionRef.current, { autoAlpha: 1, duration: 1.02, ease: "power3.inOut", scale: 10.8 }, 4.8)
        .to([gravityLinesRef.current, singularityRef.current, tunnelRef.current, coreRef.current], {
          autoAlpha: 0,
          duration: 0.68,
          ease: "power1.in"
        }, 4.94);
    }, overlayRef);

    return () => {
      if (completionTimerRef.current !== null) {
        window.clearTimeout(completionTimerRef.current);
        completionTimerRef.current = null;
      }

      timelineRef.current?.kill();
      timelineRef.current = null;
      context.revert();
    };
  }, [finishTeleport]);

  return (
    <section
      aria-label={`Opening DotaOps portal for ${destination}`}
      aria-live="polite"
      className="login-portal-handoff"
      data-role={role ?? "operator"}
      ref={overlayRef}
      role="status"
    >
      <div aria-hidden="true" className="login-portal-handoff-backdrop" ref={backdropRef} />

      <header className="login-portal-handoff-topbar">
        <span>
          <RadioTower size={15} />
          DotaOps secure uplink
        </span>
        <button className="login-portal-handoff-skip" onClick={skipHandoff} type="button">
          Skip
          <SkipForward size={15} />
        </button>
      </header>

      <div className="login-portal-handoff-stage" aria-label={`Portal stage ${stage} of 4`}>
        <strong>0{stage} / 04</strong>
        <span>Workspace transfer</span>
      </div>

      <div aria-hidden="true" className="login-portal-gravity-lines" ref={gravityLinesRef} />

      <div aria-hidden="true" className="login-portal-singularity" ref={singularityRef}>
        {Array.from({ length: SUCTION_RING_COUNT }, (_, index) => (
          <span className="login-portal-suction-ring" key={`suction-ring-${index}`} />
        ))}
        {Array.from({ length: ENERGY_ARC_COUNT }, (_, index) => (
          <span className="login-portal-energy-arc" key={`energy-arc-${index}`} />
        ))}
        <i className="login-portal-black-hole-core" ref={coreRef}>
          <Swords size={24} />
        </i>
      </div>

      <div aria-hidden="true" className="login-portal-tunnel" ref={tunnelRef}>
        {Array.from({ length: TUNNEL_RING_COUNT }, (_, index) => (
          <span className="login-portal-tunnel-ring" key={`tunnel-ring-${index}`} />
        ))}
      </div>

      <div aria-hidden="true" className="login-portal-signal-packets">
        {Array.from({ length: SIGNAL_PACKET_COUNT }, (_, index) => (
          <i className="login-portal-signal-packet" key={`signal-packet-${index}`} />
        ))}
      </div>

      <div className="login-portal-phase-copy" ref={ignitionCopyRef}>
        <small>Portal ignition</small>
        <strong>Session linked</strong>
      </div>
      <div className="login-portal-phase-copy" ref={gravityCopyRef}>
        <small>Singularity established</small>
        <strong>Gravity well opened</strong>
      </div>
      <div className="login-portal-phase-copy" ref={routingCopyRef}>
        <small>Data tunnel collapsing</small>
        <strong>Routing operator signal</strong>
      </div>
      <div className="login-portal-phase-copy" ref={syncCopyRef}>
        <small>Workspace channel established</small>
        <strong>{workspaceCopyForRole(role)}</strong>
      </div>
      <div className="login-portal-phase-copy login-portal-entering-copy" ref={exitCopyRef}>
        <small>Transfer complete</small>
        <strong>Entering workspace</strong>
      </div>

      <div aria-hidden="true" className="login-portal-energy-explosion" ref={explosionRef} />
    </section>
  );
}
