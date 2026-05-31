"use client";

import { useCurrentUserProfile } from "@/components/current-user-profile-context";
import {
  DashboardLoadingSkeleton,
  type DashboardLoadingRole
} from "@/components/dashboard/dashboard-loading-skeleton";
import { OrganizerDashboardView } from "@/components/dashboard/organizer-dashboard-view";
import { PlayerDashboardView } from "@/components/dashboard/player-dashboard-view";
import { PublicDashboardGate } from "@/components/dashboard/public-dashboard-gate";
import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import type { ProductionDashboardRole } from "@/lib/dashboard-production-data";
import { useEffect, useState } from "react";

function roleFromProfile(role?: string | null): ProductionDashboardRole {
  if (role === "admin") {
    return "admin";
  }

  if (role === "organizer") {
    return "organizer";
  }

  return "player";
}

function loadingRole(role?: ProductionDashboardRole): DashboardLoadingRole {
  return role === "admin" || role === "organizer" ? "organizer" : "player";
}

interface DashboardViewer {
  avatarUrl: string | null;
  displayName: string;
  role: ProductionDashboardRole;
}

function viewerFromProfile(profile: CurrentUserProfile | null): DashboardViewer {
  return {
    avatarUrl: profile?.avatarUrl ?? null,
    displayName: profile?.displayName || profile?.nickname || "Profile",
    role: profile ? roleFromProfile(profile.role) : "public"
  };
}

export function RoleDashboard({ role }: { role?: ProductionDashboardRole }) {
  const shellProfile = useCurrentUserProfile();
  const [loadedViewer, setLoadedViewer] = useState<DashboardViewer | null>(null);
  const viewer = shellProfile !== undefined ? viewerFromProfile(shellProfile) : loadedViewer;

  useEffect(() => {
    if (shellProfile !== undefined) {
      return;
    }

    let isMounted = true;

    getCurrentUserProfile()
      .then((profile) => {
        if (isMounted) {
          setLoadedViewer(viewerFromProfile(profile));
        }
      })
      .catch(() => {
        if (isMounted) {
          setLoadedViewer({ avatarUrl: null, displayName: "Profile", role: "public" });
        }
      });

    return () => {
      isMounted = false;
    };
  }, [shellProfile]);

  if (!viewer) {
    return <DashboardLoadingSkeleton role={loadingRole(role)} />;
  }

  const actualRole = viewer.role;
  const resolvedRole = actualRole;

  if (resolvedRole === "player") {
    return <PlayerDashboardView />;
  }

  if (resolvedRole === "admin" || resolvedRole === "organizer") {
    return <OrganizerDashboardView role={resolvedRole} />;
  }

  if (resolvedRole === "public") {
    return <PublicDashboardGate />;
  }

  return <PlayerDashboardView />;
}
