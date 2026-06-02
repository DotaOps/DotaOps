import {
  BarChart3,
  DatabaseZap,
  FileInput,
  Trophy,
  UserRound,
  UsersRound
} from "lucide-react";
import Link from "next/link";

import {
  AlertsPanel,
  RoleActionButton,
  RoleEmptyState,
  RoleKpiGrid,
  RolePanel
} from "@/components/dashboard/role-dashboard-primitives";
import type { MeOrganizerDashboard } from "@/lib/me-dashboard-data";
import type { DashboardAction, DashboardKpi } from "@/lib/role-dashboard-data";

export function OrganizerDashboardView({ dashboard }: { dashboard: MeOrganizerDashboard }) {
  const kpis: DashboardKpi[] = [
    { detail: "Managed tournaments", icon: Trophy, label: "Tournaments", tone: "red", value: String(dashboard.tournaments) },
    { detail: "Awaiting organizer review", icon: FileInput, label: "Pending Registrations", tone: "gold", value: String(dashboard.pendingRegistrations) },
    { detail: "Registration, published, or live", icon: UsersRound, label: "Active / Published", tone: "cyan", value: String(dashboard.activePublishedTournaments) },
    { detail: "Ready match game records", icon: DatabaseZap, label: "Match Data Ready", tone: "green", value: String(dashboard.processedMatchGames) },
    { detail: "OpenDota import jobs", icon: BarChart3, label: "Import Jobs", tone: "red", value: String(dashboard.importJobs) }
  ];
  const quickActions: DashboardAction[] = [
    { href: "/organizator", icon: Trophy, label: "Manage Tournaments", tone: "red" },
    { href: "/turnirji", icon: UsersRound, label: "View Public Tournaments", tone: "gold" },
    { href: "/analitika", icon: BarChart3, label: "Analytics", tone: "cyan" },
    { href: "/profile", icon: UserRound, label: "Profile", tone: "muted" }
  ];

  return (
    <div className="role-dashboard role-dashboard-organizer">
      <div className="role-dashboard-content">
        <section className="role-hero role-organizer-hero">
          <div className="role-organizer-hero-copy">
            <div className="role-hero-label-row">
              <span className="role-hero-kicker">Organizer workspace</span>
              <span>Live backend summary</span>
            </div>
            <h1>Tournament Operations</h1>
            <p>
              Review managed tournament counts, pending registrations, and imported match data.
              Detailed operational feeds will appear when the backend exposes those lists.
            </p>

            <div className="role-organizer-signals">
              <article>
                <span>Managed Tournaments</span>
                <strong>{dashboard.tournaments}</strong>
              </article>
              <article>
                <span>Pending Registrations</span>
                <strong>{dashboard.pendingRegistrations}</strong>
              </article>
              <article>
                <span>Active / Published</span>
                <strong>{dashboard.activePublishedTournaments}</strong>
              </article>
            </div>

            <div className="role-organizer-actions">
              <Link className="role-action-button role-action-primary" href="/organizator">
                Open Organizer Panel
              </Link>
              <Link className="role-action-button role-action-secondary" href="/turnirji">
                View Tournaments
              </Link>
            </div>
          </div>
        </section>

        <RoleKpiGrid columns="five" items={kpis} />

        <section className="role-organizer-grid">
          <div className="role-organizer-main">
            <RolePanel title="Operational Matrix" eyebrow="Live telemetry and match control center.">
              <RoleEmptyState
                detail="The dashboard endpoint currently provides aggregate match counts, not live match rows."
                title="No live match operations available."
              />
            </RolePanel>

            <RolePanel title="Participating Squads">
              <RoleEmptyState
                className="role-empty-state-wide"
                detail="The dashboard endpoint currently provides registration counts, not a team list."
                title="No participating squad list available."
              />
            </RolePanel>
          </div>

          <aside className="role-side-stack">
            <RolePanel title="Quick Actions">
              <div className="role-quick-actions role-organizer-actions-list">
                {quickActions.map((action) => (
                  <RoleActionButton action={action} key={action.label} />
                ))}
              </div>
            </RolePanel>

            <AlertsPanel alerts={[]} />

            <RolePanel title="Pipeline Status">
              <RoleEmptyState
                detail="Import job totals are available above. Per-job pipeline status is not included in this endpoint."
                title="No pipeline event list available."
              />
            </RolePanel>
          </aside>
        </section>
      </div>
    </div>
  );
}
