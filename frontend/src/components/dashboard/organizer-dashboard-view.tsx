import Link from "next/link";

import {
  AlertsPanel,
  PlayerAvatar,
  RoleActionButton,
  RoleEmptyState,
  RoleKpiGrid,
  RolePanel,
  StatusChip
} from "@/components/dashboard/role-dashboard-primitives";
import { getOperationsDashboardData } from "@/lib/dashboard-production-data";

export function OrganizerDashboardView({ role = "organizer" }: { role?: "admin" | "organizer" }) {
  const data = getOperationsDashboardData(role);

  return (
    <div className="role-dashboard role-dashboard-organizer">
      <div className="role-dashboard-content">
        <section className="role-hero role-organizer-hero">
          <div className="role-organizer-hero-copy">
            <div className="role-hero-label-row">
              <span className="role-hero-kicker">{data.hero.stage}</span>
              <span>Workspace overview</span>
            </div>
            <h1>{data.hero.title}</h1>
            <p>{data.hero.description}</p>

            <div className="role-organizer-signals">
              <article>
                <span>Registered Teams</span>
                <strong>{data.hero.registeredTeams}</strong>
              </article>
              <article>
                <span>Match Integrity</span>
                <strong>{data.hero.integrity}</strong>
              </article>
              <article>
                <span>Tournament Status</span>
                <strong>{data.hero.status}</strong>
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

        <RoleKpiGrid columns="five" items={data.kpis} />

        <section className="role-organizer-grid">
          <div className="role-organizer-main">
            <RolePanel
              title="Operational Matrix"
              eyebrow="Live telemetry and match control center."
            >
              <div className="role-table-wrap">
                <table className="role-data-table">
                  <thead>
                    <tr>
                      <th>Match ID</th>
                      <th>Series</th>
                      <th>Competitors</th>
                      <th>Progress</th>
                      <th>Integrity / Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.matrix.length === 0 ? (
                      <tr>
                        <td colSpan={5}>
                          <RoleEmptyState
                            detail="Match activity will appear after tournaments and match imports are active."
                            title="No live match operations yet."
                          />
                        </td>
                      </tr>
                    ) : data.matrix.map((row) => (
                      <tr key={row.matchId}>
                        <td>{row.matchId}</td>
                        <td>{row.series}</td>
                        <td>
                          <strong>{row.competitors}</strong>
                        </td>
                        <td>{row.progress}</td>
                        <td>
                          <StatusChip tone={row.status === "stable" ? "cyan" : row.status === "queued" ? "gold" : "muted"}>
                            {row.status}
                          </StatusChip>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </RolePanel>

            <RolePanel title="Participating Squads">
              <div className="role-squad-grid">
                {data.squads.length === 0 ? (
                  <RoleEmptyState
                    className="role-empty-state-wide"
                    detail="Registered teams will appear when tournament data is available."
                    title="No participating squads to show yet."
                  />
                ) : data.squads.map((squad) => (
                  <article key={squad.name}>
                    <PlayerAvatar
                      player={{
                        avatarCode: squad.avatarCode,
                        hero: "",
                        id: squad.name,
                        name: squad.name,
                        role: ""
                      }}
                    />
                    <strong>{squad.name}</strong>
                    <StatusChip
                      tone={
                        squad.status === "approved"
                          ? "cyan"
                          : squad.status === "locked"
                            ? "gold"
                            : "red"
                      }
                    >
                      {squad.status}
                    </StatusChip>
                    <button type="button">{squad.action}</button>
                  </article>
                ))}
              </div>
            </RolePanel>
          </div>

          <aside className="role-side-stack">
            <RolePanel title="Quick Actions">
              <div className="role-quick-actions role-organizer-actions-list">
                {data.quickActions.map((action) => (
                  <RoleActionButton action={action} key={action.label} />
                ))}
              </div>
            </RolePanel>

            <AlertsPanel alerts={data.alerts} />

            <RolePanel title="Pipeline Status">
              <div className="role-pipeline-list">
                {data.pipeline.length === 0 ? (
                  <RoleEmptyState
                    detail="Import and analytics processing status will appear when data is available."
                    title="No pipeline status available."
                  />
                ) : data.pipeline.map((item) => (
                  <article key={item.label}>
                    <span>{item.label}</span>
                    <strong className={`role-tone-text-${item.tone}`}>{item.value}</strong>
                  </article>
                ))}
              </div>
            </RolePanel>
          </aside>
        </section>
      </div>
    </div>
  );
}
