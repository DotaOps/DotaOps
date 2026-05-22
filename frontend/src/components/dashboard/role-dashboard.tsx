"use client";

import { CaptainDashboardView } from "@/components/dashboard/captain-dashboard-view";
import {
  DashboardLoadingSkeleton,
  type DashboardLoadingRole
} from "@/components/dashboard/dashboard-loading-skeleton";
import { OrganizerDashboardView } from "@/components/dashboard/organizer-dashboard-view";
import { PlayerDashboardView } from "@/components/dashboard/player-dashboard-view";
import { PublicDashboardGate } from "@/components/dashboard/public-dashboard-gate";
import { getCurrentUserProfile } from "@/lib/auth";
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

export function RoleDashboard({ role }: { role?: DashboardRole }) {
  const [actualRole, setActualRole] = useState<DashboardRole | null>(null);

  useEffect(() => {
    let isMounted = true;

    getCurrentUserProfile()
      .then((profile) => {
        if (isMounted) {
          setActualRole(profile ? roleFromProfile(profile.role) : "public");
        }
      })
      .catch(() => {
        if (isMounted) {
          setActualRole("public");
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  if (!actualRole) {
    return <DashboardLoadingSkeleton role={loadingRole(role)} />;
  }

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
