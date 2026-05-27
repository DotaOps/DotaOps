"use client";

import { CaptainDashboardView } from "@/components/dashboard/captain-dashboard-view";
import { useCurrentUserProfile } from "@/components/current-user-profile-context";
import {
  DashboardLoadingSkeleton,
  type DashboardLoadingRole
} from "@/components/dashboard/dashboard-loading-skeleton";
import { OrganizerDashboardView } from "@/components/dashboard/organizer-dashboard-view";
import { PlayerDashboardView } from "@/components/dashboard/player-dashboard-view";
import { PublicDashboardGate } from "@/components/dashboard/public-dashboard-gate";
import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import type { DashboardRole } from "@/lib/role-dashboard-data";
import { isOrganizerRole } from "@/lib/route-access";
import { useEffect, useState } from "react";

function roleFromProfile(role?: string | null): DashboardRole {
  if (isOrganizerRole(role)) {
    return "organizer";
  }

  if (role === "captain") {
    return "captain";
  }

  return "player";
}

function loadingRole(role?: DashboardRole): DashboardLoadingRole {
  return role === "captain" || role === "organizer" ? role : "player";
}

interface DashboardViewer {
  avatarUrl: string | null;
  displayName: string;
  role: DashboardRole;
}

function viewerFromProfile(profile: CurrentUserProfile | null): DashboardViewer {
  return {
    avatarUrl: profile?.avatarUrl ?? null,
    displayName: profile?.displayName || profile?.nickname || "Profile",
    role: profile ? roleFromProfile(profile.role) : "public"
  };
}

export function RoleDashboard({ role }: { role?: DashboardRole }) {
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
  const resolvedRole = role === "organizer" && actualRole !== "organizer"
    ? actualRole
    : role === "public"
      ? actualRole
      : role ?? actualRole;

  if (resolvedRole === "player") {
    return <PlayerDashboardView />;
  }

  if (resolvedRole === "organizer") {
    return <OrganizerDashboardView />;
  }

  if (resolvedRole === "public") {
    return <PublicDashboardGate />;
  }

  return <CaptainDashboardView />;
}
