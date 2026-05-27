import { Clock3, Gamepad2 } from "lucide-react";

import {
  AlertsPanel,
  FormChips,
  PlayerAvatar,
  RoleActionButton,
  RoleKpiGrid,
  RolePanel,
  StatusChip
} from "@/components/dashboard/role-dashboard-primitives";
import { captainDashboardData as data } from "@/lib/role-dashboard-data";

export function CaptainDashboardView() {
  return (
    <div className="role-dashboard role-dashboard-captain">
      <div className="role-dashboard-content">
        <RoleKpiGrid items={data.kpis} />

        <section className="role-hero role-captain-hero">
          <div className="role-hero-content">
            <span className="role-hero-kicker">Current Active Roster</span>
            <h1>{data.hero.team}</h1>
            <p className="role-hero-status">
              Captain Status: <strong>{data.hero.status}</strong>
            </p>
          </div>

          <div className="role-hero-form">
            <span>Recent Form</span>
            <FormChips values={data.hero.recentForm} />
          </div>

          <div className="role-hero-meta">
            <article>
              <span className="role-opponent-code">{data.hero.opponentCode}</span>
              <div>
                <p>Next Match vs</p>
                <strong>{data.hero.opponent}</strong>
              </div>
            </article>
            <article>
              <Clock3 size={26} />
              <div>
                <p>Scheduled Start</p>
                <strong>{data.hero.scheduledStart}</strong>
              </div>
            </article>
            <button className="role-briefing-button" type="button">
              Pre-Match Briefing
            </button>
          </div>
        </section>

        <section className="role-captain-grid">
          <div className="role-captain-main">
            <RolePanel
              title="Upcoming Matches"
              action={<span className="role-view-all">View all</span>}
            >
              <div className="role-table-wrap">
                <table className="role-data-table">
                  <thead>
                    <tr>
                      <th>Tournament</th>
                      <th>Opponent</th>
                      <th>Time</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.upcomingMatches.map((match) => (
                      <tr key={`${match.tournament}-${match.opponent}`}>
                        <td>
                          <strong>{match.tournament}</strong>
                          <span>{match.detail}</span>
                        </td>
                        <td>
                          <span className="role-team-cell">
                            <em>{match.opponentCode}</em>
                            {match.opponent}
                          </span>
                        </td>
                        <td>{match.time}</td>
                        <td>
                          <StatusChip tone={match.status === "ready" ? "cyan" : match.status === "queued" ? "gold" : "muted"}>
                            {match.status}
                          </StatusChip>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </RolePanel>

            <RolePanel title="Team Roster">
              <div className="role-roster-strip">
                {data.roster.map((player) => (
                  <article key={player.id}>
                    <PlayerAvatar player={player} />
                    <div>
                      <strong>{player.name}</strong>
                      <span>
                        {player.role} / {player.hero}
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            </RolePanel>

            <section className="role-mini-card-grid">
              {data.performance.map((item) => {
                const Icon = item.icon;

                return (
                  <article className={`role-mini-card role-tone-${item.tone}`} key={item.label}>
                    <Icon size={18} />
                    <span>{item.label}</span>
                    <strong>{item.value}</strong>
                    <p>{item.detail}</p>
                  </article>
                );
              })}
            </section>
          </div>

          <aside className="role-side-stack">
            <RolePanel title="Quick Actions">
              <div className="role-quick-actions">
                {data.quickActions.map((action) => (
                  <RoleActionButton action={action} key={action.label} />
                ))}
              </div>
            </RolePanel>

            <AlertsPanel alerts={data.alerts} />

            <RolePanel title="Captain Channel" eyebrow="Private workspace">
              <div className="role-captain-channel">
                <Gamepad2 size={20} />
                <strong>Draft prep room ready</strong>
                <p>Briefing actions are placeholders until match workflows are wired to backend data.</p>
              </div>
            </RolePanel>
          </aside>
        </section>
      </div>
    </div>
  );
}
