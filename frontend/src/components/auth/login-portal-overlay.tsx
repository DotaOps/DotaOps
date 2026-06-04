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
const VORTEX_RING_COUNT = 4;
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
  const vortexRef = useRef<HTMLDivElement>(null);
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
      const vortexRings = gsap.utils.toArray<HTMLElement>(".login-portal-vortex-ring");
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
      const spiralPacketOffset = (
        index: number,
        direction: "x" | "y",
        radiusFactor: number,
        turnRadians: number
      ) => {
        const angle = ((index / SIGNAL_PACKET_COUNT) * Math.PI * 2) + turnRadians;
        const radius = packetRadius * radiusFactor;
        return (direction === "x" ? Math.cos(angle) : Math.sin(angle)) * radius;
      };

      timeline
        .set(overlayRef.current, { autoAlpha: 1 })
        .set(phaseCopy, { autoAlpha: 0, y: 12 })
        .set(gravityLinesRef.current, { autoAlpha: 0, rotation: -16, scale: 1.28 })
        .set(singularityRef.current, { autoAlpha: 0, scale: 0.18 })
        .set(coreRef.current, { autoAlpha: 0, scale: 0.32 })
        .set(vortexRef.current, { autoAlpha: 0, scale: 1.16 })
        .set(suctionRings, { autoAlpha: 0, scale: 1.92 })
        .set(vortexRings, { autoAlpha: 0, rotation: (index) => (index * 54) - 32, scale: 1.78 })
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
        .to(backdropRef.current, { opacity: 1, duration: 0.72, ease: "sine.out" }, 0)
        .to(ignitionCopyRef.current, { autoAlpha: 1, duration: 0.4, ease: "power2.out", y: 0 }, 0.12)
        .to(gravityLinesRef.current, { autoAlpha: 0.26, duration: 0.68, ease: "sine.out" }, 0.22)
        .to(ignitionCopyRef.current, { autoAlpha: 0, duration: 0.32, ease: "sine.inOut", y: -7 }, 0.62)

        // 02 / 04: open the gravity well and pull the login scene inward.
        .call(() => setStage(2), [], 0.8)
        .to(gravityCopyRef.current, { autoAlpha: 1, duration: 0.42, ease: "power2.out", y: 0 }, 0.74)
        .to(singularityRef.current, { autoAlpha: 1, duration: 0.92, ease: "power3.out", scale: 1 }, 0.78)
        .to(coreRef.current, { autoAlpha: 1, duration: 0.74, ease: "power2.inOut", scale: 1 }, 0.9)
        .to(suctionRings, { autoAlpha: 0.58, duration: 1.16, ease: "expo.inOut", scale: 0.42, stagger: 0.06 }, 0.88)
        .to(packets, {
          autoAlpha: 0.58,
          duration: 1.34,
          ease: "power3.inOut",
          rotation: (index) => (index * 20) + 116,
          scale: 0.2,
          stagger: 0.025,
          x: (index) => spiralPacketOffset(index, "x", 0.38, Math.PI * 0.72),
          y: (index) => spiralPacketOffset(index, "y", 0.38, Math.PI * 0.72)
        }, 0.92)
        .to(gravityLinesRef.current, { autoAlpha: 0.66, duration: 1.18, ease: "power2.inOut", rotation: 42, scale: 0.64 }, 0.92)
        .to(loginScene, { duration: 1.18, ease: "power2.inOut", filter: "blur(3px)", opacity: 0.44, scale: 0.86 }, 0.92)
        .to(gravityCopyRef.current, { autoAlpha: 0, duration: 0.34, ease: "sine.inOut", y: -8 }, 1.68)

        // 03 / 04: spiral the operator signal into the singularity.
        .call(() => setStage(3), [], 2.0)
        .to(routingCopyRef.current, { autoAlpha: 1, duration: 0.44, ease: "power2.out", y: 0 }, 1.88)
        .to(vortexRef.current, { autoAlpha: 1, duration: 0.68, ease: "sine.inOut", scale: 1 }, 1.76)
        .to(vortexRings, {
          autoAlpha: 0.54,
          duration: 1.68,
          ease: "power3.inOut",
          rotation: (index) => (index % 2 === 0 ? 248 : -214) + (index * 37),
          scale: (index) => 0.12 + (index * 0.022),
          stagger: 0.08
        }, 1.84)
        .to(packets, {
          autoAlpha: 0.54,
          duration: 1.64,
          ease: "power3.inOut",
          rotation: (index) => (index * 20) + 286,
          scale: 0.035,
          stagger: 0.022,
          x: (index) => spiralPacketOffset(index, "x", 0.035, Math.PI * 1.9),
          y: (index) => spiralPacketOffset(index, "y", 0.035, Math.PI * 1.9)
        }, 1.9)
        .to(suctionRings, { autoAlpha: 0.3, duration: 1.52, ease: "expo.inOut", rotation: 164, scale: 0.14, stagger: 0.035 }, 1.94)
        .to(gravityLinesRef.current, { autoAlpha: 0.7, duration: 1.52, ease: "power3.inOut", rotation: 178, scale: 0.2 }, 1.96)
        .to(loginScene, { duration: 1.46, ease: "power3.inOut", filter: "blur(10px)", opacity: 0, scale: 0.16 }, 2.02)
        .to(singularityRef.current, { duration: 1.46, ease: "sine.inOut", scale: 1.28 }, 2.04)
        .to(routingCopyRef.current, { autoAlpha: 0, duration: 0.36, ease: "sine.inOut", y: -8 }, 3.28)

        // 04 / 04: implode the singularity, then release the energy bloom.
        .call(() => setStage(4), [], 3.6)
        .to(syncCopyRef.current, { autoAlpha: 1, duration: 0.46, ease: "power2.out", y: 0 }, 3.44)
        .to(vortexRings, { autoAlpha: 0.2, duration: 0.72, ease: "expo.inOut", rotation: (index) => (index * 76) + 412, scale: 0.025 }, 3.42)
        .to(suctionRings, { autoAlpha: 0.12, duration: 0.7, ease: "expo.inOut", rotation: 244, scale: 0.055 }, 3.46)
        .to(arcs, { autoAlpha: 0.54, duration: 1.08, ease: "sine.inOut", rotation: (index) => (index * 82) + 128, scale: 1.1, stagger: 0.07 }, 3.48)
        .to(coreRef.current, { autoAlpha: 0.96, duration: 0.56, ease: "expo.inOut", scale: 0.62 }, 3.54)
        .to(singularityRef.current, { autoAlpha: 0.9, duration: 0.58, ease: "expo.inOut", scale: 0.86 }, 3.56)
        .to(coreRef.current, { duration: 0.68, ease: "power3.inOut", scale: 1.38 }, 4.1)
        .to(singularityRef.current, { duration: 0.7, ease: "power3.inOut", scale: 1.46 }, 4.12)
        .to(explosionRef.current, { autoAlpha: 0.24, duration: 0.44, ease: "sine.inOut", scale: 1.34 }, 4.34)
        .to(syncCopyRef.current, { autoAlpha: 0, duration: 0.38, ease: "sine.inOut", y: -8 }, 4.42)
        .to(exitCopyRef.current, { autoAlpha: 1, duration: 0.46, ease: "power2.out", y: 0 }, 4.6)
        .to(explosionRef.current, { autoAlpha: 0.68, duration: 0.68, ease: "sine.inOut", scale: 9.2 }, 4.76)
        .to(explosionRef.current, { autoAlpha: 0.48, duration: 0.4, ease: "sine.out", scale: 10.4 }, 5.42)
        .to([gravityLinesRef.current, singularityRef.current, vortexRef.current, coreRef.current], {
          autoAlpha: 0,
          duration: 0.86,
          ease: "power2.inOut"
        }, 4.76);
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

      <div aria-hidden="true" className="login-portal-vortex" ref={vortexRef}>
        {Array.from({ length: VORTEX_RING_COUNT }, (_, index) => (
          <span className="login-portal-vortex-ring" key={`vortex-ring-${index}`} />
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
        <small>Signal vortex converging</small>
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
