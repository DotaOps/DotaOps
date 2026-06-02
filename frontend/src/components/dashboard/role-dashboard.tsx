"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { useCurrentUserProfile } from "@/components/current-user-profile-context";
import { AdminDashboardView } from "@/components/dashboard/admin-dashboard-view";
import {
  DashboardLoadingSkeleton,
  type DashboardLoadingRole
} from "@/components/dashboard/dashboard-loading-skeleton";
import { OrganizerDashboardView } from "@/components/dashboard/organizer-dashboard-view";
import { PlayerDashboardView } from "@/components/dashboard/player-dashboard-view";
import { PublicDashboardGate } from "@/components/dashboard/public-dashboard-gate";
import { ApiRequestError } from "@/lib/api";
import { getCurrentUserProfile } from "@/lib/auth";
import {
  getMeDashboard,
  type MeDashboard,
  type MeDashboardRole
} from "@/lib/me-dashboard-data";

type DashboardRoleHint = MeDashboardRole | "public";

function roleFromProfile(role?: string | null): DashboardRoleHint {
  if (role === "admin") {
    return "admin";
  }

  if (role === "organizer") {
    return "organizer";
  }

  return role ? "player" : "public";
}

function loadingRole(role?: DashboardRoleHint): DashboardLoadingRole {
  return role === "admin" || role === "organizer" ? "organizer" : "player";
}

export function RoleDashboard({ role }: { role?: DashboardRoleHint }) {
  const shellProfile = useCurrentUserProfile();
  const router = useRouter();
  const [loadedViewerRole, setLoadedViewerRole] = useState<DashboardRoleHint | null>(null);
  const [dashboard, setDashboard] = useState<MeDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const requestId = useRef(0);
  const viewerRole = shellProfile !== undefined ? roleFromProfile(shellProfile?.role) : loadedViewerRole;

  useEffect(() => {
    if (shellProfile !== undefined) {
      return;
    }

    let isMounted = true;

    getCurrentUserProfile()
      .then((profile) => {
        if (isMounted) {
          setLoadedViewerRole(roleFromProfile(profile?.role));
        }
      })
      .catch(() => {
        if (isMounted) {
          setLoadedViewerRole("public");
        }
      });

    return () => {
      isMounted = false;
    };
  }, [shellProfile]);

  const loadDashboard = useCallback(async () => {
    const nextRequestId = requestId.current + 1;

    requestId.current = nextRequestId;
    setIsLoading(true);
    setError(null);

    try {
      const nextDashboard = await getMeDashboard();

      if (requestId.current === nextRequestId) {
        setDashboard(nextDashboard);
      }
    } catch (caught) {
      if (requestId.current === nextRequestId) {
        if (caught instanceof ApiRequestError && caught.status === 401) {
          router.replace("/login?next=%2Fdashboard");
          return;
        }

        setDashboard(null);
        setError(caught instanceof Error ? caught.message : "Dashboard data could not be loaded.");
      }
    } finally {
      if (requestId.current === nextRequestId) {
        setIsLoading(false);
      }
    }
  }, [router]);

  useEffect(() => {
    if (!viewerRole || viewerRole === "public") {
      return;
    }

    const timeout = window.setTimeout(() => {
      void loadDashboard();
    }, 0);

    return () => {
      window.clearTimeout(timeout);
    };
  }, [loadDashboard, viewerRole]);

  if (!viewerRole || isLoading) {
    return <DashboardLoadingSkeleton role={loadingRole(dashboard?.role ?? role ?? viewerRole ?? undefined)} />;
  }

  if (viewerRole === "public") {
    return <PublicDashboardGate />;
  }

  if (error) {
    return <DashboardState detail={error} onRetry={loadDashboard} title="Dashboard unavailable" />;
  }

  if (!dashboard) {
    return (
      <DashboardState
        detail="The backend did not return a dashboard payload for this account."
        onRetry={loadDashboard}
        title="No dashboard data available"
      />
    );
  }

  if (dashboard.role === "player" && dashboard.player) {
    return (
      <PlayerDashboardView
        capabilities={dashboard.capabilities}
        dashboard={dashboard.player}
      />
    );
  }

  if (dashboard.role === "organizer" && dashboard.organizer) {
    return <OrganizerDashboardView dashboard={dashboard.organizer} />;
  }

  if (dashboard.role === "admin" && dashboard.admin) {
    return <AdminDashboardView dashboard={dashboard.admin} />;
  }

  return (
    <DashboardState
      detail={`The backend returned role "${dashboard.role}" without its expected dashboard payload.`}
      onRetry={loadDashboard}
      title="Dashboard payload incomplete"
    />
  );
}

function DashboardState({
  detail,
  onRetry,
  title
}: {
  detail: string;
  onRetry: () => void;
  title: string;
}) {
  return (
    <div className="role-dashboard role-dashboard-public">
      <div className="role-public-shell">
        <section className="role-public-card">
          <p className="role-hero-kicker">Dashboard data</p>
          <h1>{title}</h1>
          <p>{detail}</p>
          <div className="role-public-actions">
            <button className="role-action-button role-action-primary" onClick={onRetry} type="button">
              Retry
            </button>
            <Link className="role-action-button role-action-secondary" href="/profile">
              Open Profile
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
