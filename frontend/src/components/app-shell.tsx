"use client";

import {
  BarChart3,
  Brackets,
  LayoutDashboard,
  LogIn,
  ScrollText,
  Swords,
  Trophy,
  UserPlus,
  UserRound,
  UsersRound
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { CurrentUserProfileProvider } from "@/components/current-user-profile-context";
import { UserAvatar } from "@/components/user-avatar";
import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import { isAdminRole, isOrganizerRole, routeAccessForPath } from "@/lib/route-access";
import { classNames } from "@/lib/utils";
import {
  DashboardLoadingSkeleton,
  type DashboardLoadingRole
} from "@/components/dashboard/dashboard-loading-skeleton";
import { RouteLoadingSkeleton } from "@/components/route-loading-skeleton";
import { WorkspaceLoadingSkeleton } from "@/components/workspace-loading-skeleton";

const navItems: Array<{
  href: string;
  icon: LucideIcon;
  label: string;
  hideForOrganizer?: boolean;
  adminOnly?: boolean;
  organizerOnly?: boolean;
}> = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/turnirji", label: "Tournaments", icon: Trophy },
  { href: "/organizator", label: "Organizer", icon: Brackets, organizerOnly: true },
  { href: "/ekipe", label: "My Team", icon: UsersRound, hideForOrganizer: true },
  { href: "/analitika", label: "Analytics", icon: BarChart3 },
  { href: "/profile", label: "Profile", icon: UserRound },
  { href: "/admin/audit", label: "Audit Log", icon: ScrollText, adminOnly: true }
];

const publicContentNavItems: Array<{
  href: string;
  icon: LucideIcon;
  label: string;
}> = [
  { href: "/turnirji", label: "Tournaments", icon: Trophy },
  { href: "/login", label: "Login", icon: LogIn },
  { href: "/register", label: "Register", icon: UserPlus }
];

function dashboardLoadingRole(role?: string | null): DashboardLoadingRole {
  if (isOrganizerRole(role)) {
    return "organizer";
  }

  return "player";
}

function formatRoleLabel(role?: string | null) {
  if (!role) {
    return "Visitor";
  }

  return role.replace(/_/g, " ");
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const isRoleDashboard = pathname.startsWith("/dashboard");
  const access = routeAccessForPath(pathname);
  const isOrganizerWorkspace = access === "organizer";
  const [profile, setProfile] = useState<CurrentUserProfile | null>(null);
  const [isCheckingAuth, setIsCheckingAuth] = useState(
    access !== "public" && access !== "transition"
  );
  const [checkedAuthPathname, setCheckedAuthPathname] = useState<string | null>(
    access === "public" || access === "transition" ? pathname : null
  );
  const canUseOrganizer = isOrganizerRole(profile?.role);
  const canUseAdmin = isAdminRole(profile?.role);
  const isPublicContentGuest = access === "public-content" && !profile;
  const isPrivateAuthCheckPending =
    access !== "public" &&
    access !== "public-content" &&
    access !== "transition" &&
    (isCheckingAuth || checkedAuthPathname !== pathname);
  const shouldUseOrganizerLoader = isOrganizerWorkspace && isPrivateAuthCheckPending;

  useEffect(() => {
    let isMounted = true;

    if (access === "public" || access === "transition") {
      const timeout = window.setTimeout(() => {
        if (isMounted) {
          setCheckedAuthPathname(pathname);
        }
      }, 0);

      return () => {
        isMounted = false;
        window.clearTimeout(timeout);
      };
    }

    const timeout = window.setTimeout(() => {
      if (!isMounted) {
        return;
      }

      setIsCheckingAuth(true);

      getCurrentUserProfile()
        .then((loadedProfile) => {
          if (isMounted) {
            setProfile(loadedProfile);
          }
        })
        .catch(() => {
          if (isMounted) {
            setProfile(null);
          }
        })
        .finally(() => {
          if (isMounted) {
            setCheckedAuthPathname(pathname);
            setIsCheckingAuth(false);
          }
        });
    }, 0);

    return () => {
      isMounted = false;
      window.clearTimeout(timeout);
    };
  }, [access, pathname]);

  useEffect(() => {
    if (
      isPrivateAuthCheckPending ||
      profile ||
      access === "public" ||
      access === "public-content" ||
      access === "transition"
    ) {
      return;
    }

    try {
      const ts = localStorage.getItem("dotaops:just_signed_in");

      if (ts) {
        const t = Number(ts);

        if (!Number.isNaN(t) && Date.now() - t < 3000) {
          // Recently signed in; wait a bit for session visibility instead of redirecting.
          return;
        }

        // Remove stale flag
        localStorage.removeItem("dotaops:just_signed_in");
      }
    } catch {
      // localStorage unavailable — fall back to normal redirect
    }

    router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [access, isPrivateAuthCheckPending, pathname, profile, router]);

  const visibleNavItems = useMemo(() => {
    if (isPublicContentGuest) {
      return publicContentNavItems;
    }

    return navItems.filter(
      (item) =>
        (!item.organizerOnly || canUseOrganizer || shouldUseOrganizerLoader) &&
        (!item.hideForOrganizer || profile?.role !== "organizer") &&
        (!item.adminOnly || canUseAdmin)
    );
  }, [canUseAdmin, canUseOrganizer, isPublicContentGuest, profile?.role, shouldUseOrganizerLoader]);
  const hasAuthenticatedProfile = Boolean(profile);
  const profileDisplayName = profile?.displayName || profile?.nickname || "Profile";
  const sidebarProfileHref = hasAuthenticatedProfile ? "/profile" : "/login";
  const sidebarProfileLabel = hasAuthenticatedProfile ? profileDisplayName : "Public visitor";
  const sidebarProfileMeta = hasAuthenticatedProfile
    ? formatRoleLabel(profile?.role)
    : "Browse public tournaments";
  const sidebarProfileSyncState = profile?.steamId
    ? "Steam linked"
    : hasAuthenticatedProfile
      ? "Steam/Dota profile pending"
      : "Login to unlock workspace";
  const shouldShowPageSkeleton = isPrivateAuthCheckPending && Boolean(profile) && !shouldUseOrganizerLoader;
  const pageDashboardLoadingRole = dashboardLoadingRole(profile?.role);

  if (access === "public" || access === "transition") {
    return <>{children}</>;
  }

  if (isPrivateAuthCheckPending && !profile && !shouldUseOrganizerLoader) {
    return <WorkspaceLoadingSkeleton dashboard={isRoleDashboard} />;
  }

  if (!isPrivateAuthCheckPending && !profile && access !== "public-content") {
    return (
      <RouteState
        action={<Link className="button ops-button-primary" href="/login">Login</Link>}
        detail="This page requires an authenticated DotaOps account."
        title="Login required"
      />
    );
  }

  if (!isPrivateAuthCheckPending && isOrganizerWorkspace && !canUseOrganizer) {
    return (
      <RouteState
        action={
          <>
            <Link className="button ops-button-primary" href="/dashboard">Back to Dashboard</Link>
            <Link className="button ops-button-secondary" href="/turnirji">View Tournaments</Link>
          </>
        }
        detail="This section is only available to tournament organizers and admins."
        title="Organizer access required"
      />
    );
  }

  if (access === "admin" && !canUseAdmin) {
    return (
      <RouteState
        action={
          <>
            <Link className="button ops-button-primary" href="/dashboard">Back to Dashboard</Link>
            <Link className="button ops-button-secondary" href="/turnirji">View Tournaments</Link>
          </>
        }
        detail="This section is only available to DotaOps administrators."
        title="Admin access required"
      />
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar ops-panel ops-scanline">
        <Link href="/" className="brand" aria-label="DotaOps home">
          <span className="brand-mark" aria-hidden="true">
            <Swords size={22} />
          </span>
          <span>
            <strong>DotaOps</strong>
            <small>Dota 2 tournaments and analytics</small>
          </span>
        </Link>

        <nav className="nav-list" aria-label="Main navigation">
          {visibleNavItems.map((item) => {
            const isActive =
              item.href === "/dashboard"
                ? pathname === "/" || pathname.startsWith(item.href)
                : pathname.startsWith(item.href);
            const Icon = item.icon;

            return (
              <Link
                className={classNames("nav-link", isActive && "is-active")}
                href={item.href}
                key={item.href}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <Link className="sidebar-user-card ops-card" href={sidebarProfileHref}>
          <UserAvatar avatarUrl={profile?.avatarUrl} className="sidebar-user-avatar" size={18} />
          <span className="sidebar-user-copy">
            <span className="sidebar-user-kicker">
              {hasAuthenticatedProfile ? "Signed in" : "Public session"}
            </span>
            <strong>{sidebarProfileLabel}</strong>
            <span className="sidebar-user-meta">{sidebarProfileMeta}</span>
          </span>
          <span className="sidebar-user-badge">{sidebarProfileSyncState}</span>
        </Link>
      </aside>

      <div className="main-area">
        <CurrentUserProfileProvider profile={profile}>
          <main className={classNames("page", isRoleDashboard && "dashboard-page")}>
            {shouldShowPageSkeleton ? (
              isRoleDashboard ? (
                <DashboardLoadingSkeleton role={pageDashboardLoadingRole} />
              ) : (
                <RouteLoadingSkeleton />
              )
            ) : children}
          </main>
        </CurrentUserProfileProvider>
      </div>
    </div>
  );
}

function RouteState({
  action,
  detail,
  title
}: {
  action?: ReactNode;
  detail: string;
  title: string;
}) {
  return (
    <main className="route-access-state">
      <section className="route-access-panel ops-panel">
        <p className="ops-label">DotaOps access control</p>
        <h1>{title}</h1>
        <p>{detail}</p>
        {action ? <div>{action}</div> : null}
      </section>
    </main>
  );
}
