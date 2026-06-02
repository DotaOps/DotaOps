import { BarChart3, FileInput, ScrollText, Trophy, UserRound, UsersRound } from "lucide-react";
import Link from "next/link";

import {
  RoleActionButton,
  RoleEmptyState,
  RoleKpiGrid,
  RolePanel
} from "@/components/dashboard/role-dashboard-primitives";
import type { MeAdminDashboard } from "@/lib/me-dashboard-data";
import type { DashboardAction, DashboardKpi } from "@/lib/role-dashboard-data";

export function AdminDashboardView({ dashboard }: { dashboard: MeAdminDashboard }) {
  const kpis: DashboardKpi[] = [
    { detail: "Persisted DotaOps profiles", icon: UserRound, label: "Profiles", tone: "cyan", value: String(dashboard.profiles) },
    { detail: "Tournaments in system", icon: Trophy, label: "Tournaments", tone: "red", value: String(dashboard.tournaments) },
    { detail: "Awaiting review", icon: UsersRound, label: "Pending Registrations", tone: "gold", value: String(dashboard.pendingRegistrations) },
    { detail: "OpenDota pipeline jobs", icon: FileInput, label: "Import Jobs", tone: "green", value: String(dashboard.importJobs) }
  ];
  const quickActions: DashboardAction[] = [
    { href: "/admin/audit", icon: ScrollText, label: "Open Audit Log", tone: "red" },
    { href: "/organizator", icon: Trophy, label: "Organizer Workspace", tone: "gold" },
    { href: "/turnirji", icon: UsersRound, label: "View Tournaments", tone: "cyan" },
    { href: "/analitika", icon: BarChart3, label: "Analytics", tone: "muted" }
  ];

  return (
    <div className="role-dashboard role-dashboard-organizer">
      <div className="role-dashboard-content">
        <section className="role-hero role-organizer-hero">
          <div className="role-organizer-hero-copy">
            <div className="role-hero-label-row">
              <span className="role-hero-kicker">Admin workspace</span>
              <span>System overview</span>
            </div>
            <h1>Admin Overview</h1>
            <p>
              Review system-level profile, tournament, registration, and import job totals. Use the
              audit trail for administrative change history.
            </p>

            <div className="role-organizer-signals">
              <article>
                <span>Profiles</span>
                <strong>{dashboard.profiles}</strong>
              </article>
              <article>
                <span>Tournaments</span>
                <strong>{dashboard.tournaments}</strong>
              </article>
              <article>
                <span>Pending Registrations</span>
                <strong>{dashboard.pendingRegistrations}</strong>
              </article>
            </div>

            <div className="role-organizer-actions">
              <Link className="role-action-button role-action-primary" href="/admin/audit">
                Open Audit Log
              </Link>
              <Link className="role-action-button role-action-secondary" href="/organizator">
                Organizer Workspace
              </Link>
            </div>
          </div>
        </section>

        <RoleKpiGrid items={kpis} />

        <section className="role-organizer-grid">
          <div className="role-organizer-main">
            <RolePanel title="System Operations" eyebrow="Administrative summary">
              <RoleEmptyState
                detail="The dashboard endpoint currently provides safe aggregate counts only. Use Audit Log for detailed administrative history."
                title="Detailed system feed is not exposed here."
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
          </aside>
        </section>
      </div>
    </div>
  );
}
