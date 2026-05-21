"use client";

import {
  BarChart3,
  Bell,
  Brackets,
  LayoutDashboard,
  LogIn,
  Plus,
  RadioTower,
  Shield,
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

import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import { isOrganizerRole, routeAccessForPath } from "@/lib/route-access";
import { classNames } from "@/lib/utils";

const navItems: Array<{
  href: string;
  icon: LucideIcon;
  label: string;
  organizerOnly?: boolean;
}> = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/turnirji", label: "Tournaments", icon: Trophy },
  { href: "/organizator", label: "Organizer", icon: Brackets, organizerOnly: true },
  { href: "/ekipe", label: "My Team", icon: UsersRound },
  { href: "/analitika", label: "Analytics", icon: BarChart3 },
  { href: "/profile", label: "Profile", icon: UserRound }
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

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "mock data";
  const isRoleDashboard = pathname.startsWith("/dashboard");
  const access = routeAccessForPath(pathname);
  const [profile, setProfile] = useState<CurrentUserProfile | null>(null);
  const [isCheckingAuth, setIsCheckingAuth] = useState(access !== "public");
  const canUseOrganizer = isOrganizerRole(profile?.role);
  const isPublicContentGuest = access === "public-content" && !profile;

  useEffect(() => {
    let isMounted = true;

    if (access === "public") {
      return () => {
        isMounted = false;
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
    if (isCheckingAuth || profile || access === "public" || access === "public-content") {
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
  }, [access, isCheckingAuth, pathname, profile, router]);

  const primaryAction = useMemo(() => {
    if (isPublicContentGuest) {
      return { href: "/login", icon: LogIn, label: "Login" };
    }

    if (isOrganizerRole(profile?.role)) {
      return { href: "/organizator", icon: Plus, label: "New Tournament" };
    }

    if (profile?.role === "captain") {
      return { href: "/turnirji", icon: Trophy, label: "Join Tournament" };
    }

    return { href: "/turnirji", icon: Trophy, label: "View Tournaments" };
  }, [isPublicContentGuest, profile?.role]);
  const PrimaryActionIcon = primaryAction.icon;
  const visibleNavItems = useMemo(() => {
    if (isPublicContentGuest) {
      return publicContentNavItems;
    }

    return navItems.filter((item) => !item.organizerOnly || canUseOrganizer);
  }, [canUseOrganizer, isPublicContentGuest]);
  const profileHref = isPublicContentGuest ? "/login" : "/profile";
  const profileLabel = isPublicContentGuest ? "PUBLIC OPS" : profile?.nickname ?? "SOLO_TACTICIAN";
  const profileRoleLabel = isPublicContentGuest ? "Visitor" : profile?.role ?? "Profile";

  if (access === "public") {
    return <>{children}</>;
  }

  if (isCheckingAuth && access !== "public-content") {
    return (
      <RouteState
        detail="Checking your DotaOps session before opening this private workspace."
        title="Loading session"
      />
    );
  }

  if (!isCheckingAuth && !profile && access !== "public-content") {
    return (
      <RouteState
        action={<Link className="button ops-button-primary" href="/login">Login</Link>}
        detail="This page requires an authenticated DotaOps account."
        title="Login required"
      />
    );
  }

  if (access === "organizer" && !canUseOrganizer) {
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

        <div className="sidebar-panel ops-card">
          <Shield size={18} />
          <div>
            <span>API connection</span>
            <strong>{apiUrl}</strong>
          </div>
        </div>
      </aside>

      <div className="main-area">
        {isRoleDashboard ? null : (
          <header className="topbar ops-panel">
            <div className="topbar-actions" aria-label="Application status and actions">
              <div className="topbar-status-segment">
                <RadioTower size={16} />
                <span>OPS STATUS</span>
                <strong>ONLINE</strong>
              </div>

              <div className="topbar-status-segment topbar-rank-segment">
                <Shield size={16} />
                <span>RANK</span>
                <strong>IMMORTAL</strong>
              </div>

              <button className="topbar-icon-button" type="button" aria-label="Notifications">
                <Bell size={17} />
              </button>

              <Link className="topbar-profile" href={profileHref} aria-label="User profile">
                <span className="topbar-avatar" aria-hidden="true">
                  {!isPublicContentGuest && profile?.avatarUrl ? (
                    <span
                      className="topbar-avatar-image"
                      style={{ backgroundImage: `url(${profile.avatarUrl})` }}
                    />
                  ) : (
                    <UserRound size={16} />
                  )}
                </span>
                <span>
                  <strong>{profileLabel}</strong>
                  <small>{profileRoleLabel}</small>
                </span>
              </Link>

              <Link className="button button-primary ops-button-primary topbar-primary-action" href={primaryAction.href}>
                <PrimaryActionIcon size={18} />
                <span>{primaryAction.label}</span>
              </Link>
            </div>
          </header>
        )}

        <main className={classNames("page", isRoleDashboard && "dashboard-page")}>
          {children}
        </main>
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
