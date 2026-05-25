import { Gamepad2 } from "lucide-react";

import {
  DashboardTopbar,
  FormChips,
  PlayerAvatar,
  RoleActionButton,
  RolePanel,
  StatusChip
} from "@/components/dashboard/role-dashboard-primitives";
import { playerDashboardData as data } from "@/lib/role-dashboard-data";
import { classNames } from "@/lib/utils";

export function PlayerDashboardView({
  avatarUrl,
  displayName
}: {
  avatarUrl?: string | null;
  displayName: string;
}) {
  return (
    <div className="role-dashboard role-dashboard-player">
      <DashboardTopbar
        avatarUrl={avatarUrl}
        displayName={displayName}
      />

      <div className="role-dashboard-content">
        <section className="role-hero role-player-hero">
          <div className="role-player-hero-copy">
            <div className="role-hero-label-row">
              <span className="role-hero-kicker">Pro Series</span>
              <span>Live Event</span>
            </div>
            <h1>{data.hero.name}</h1>
            <p>{data.hero.description}</p>
            <div className="role-player-hero-actions">
              <button className="role-action-button role-action-primary" type="button">
                <Gamepad2 size={18} />
                <span>Recap Replay</span>
              </button>
              <button className="role-action-button role-action-secondary" type="button">
                Event Specs
              </button>
            </div>
          </div>

          <div className="role-hero-form role-player-form">
            <span>Recent Form</span>
            <FormChips values={data.hero.recentForm} />
          </div>
        </section>

        <section className="role-player-grid">
          <div className="role-player-main">
            <div className="role-section-heading">
              <h2>Personal Performance</h2>
              <span>Last 20 Matches</span>
            </div>

            <div className="role-performance-grid">
              {data.performance.map((metric) => (
                <article
                  className={classNames(
                    "role-performance-card",
                    `role-tone-${metric.tone}`,
                    metric.featured && "is-featured"
                  )}
                  key={metric.label}
                >
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  {metric.detail ? <p>{metric.detail}</p> : null}
                </article>
              ))}
            </div>
          </div>

          <RolePanel title="Team Liquid Roster" className="role-player-roster-panel">
            <div className="role-player-roster">
              {data.roster.map((player) => (
                <article key={player.id}>
                  <PlayerAvatar player={player} />
                  <div>
                    <strong>{player.name}</strong>
                    <span>{player.role}</span>
                  </div>
                  <StatusChip
                    tone={
                      player.status === "online"
                        ? "cyan"
                        : player.status === "in-game"
                          ? "gold"
                          : "muted"
                    }
                  >
                    {player.status}
                  </StatusChip>
                </article>
              ))}
            </div>
            <RoleActionButton
              action={{ disabled: true, icon: Gamepad2, label: "Team Strat Room" }}
              variant="secondary"
            />
          </RolePanel>
        </section>

        <RolePanel
          title="Recent Match Log"
          action={
            <div className="role-table-actions">
              <button type="button">All Roles</button>
              <button type="button">Win Only</button>
            </div>
          }
        >
          <div className="role-table-wrap">
            <table className="role-data-table role-match-log-table">
              <thead>
                <tr>
                  <th>Hero</th>
                  <th>Result</th>
                  <th>Type</th>
                  <th>K / D / A</th>
                  <th>Duration</th>
                  <th>GPM/XPM</th>
                  <th>Analyzed</th>
                </tr>
              </thead>
              <tbody>
                {data.matchLog.map((match) => (
                  <tr key={`${match.hero}-${match.duration}`}>
                    <td>
                      <span className="role-hero-cell">
                        <em>{match.hero.slice(0, 2).toUpperCase()}</em>
                        <strong>{match.hero}</strong>
                      </span>
                    </td>
                    <td>
                      <StatusChip tone={match.result === "WIN" ? "green" : "red"}>
                        {match.result}
                      </StatusChip>
                    </td>
                    <td>{match.type}</td>
                    <td>
                      <strong>{match.kda}</strong>
                    </td>
                    <td>{match.duration}</td>
                    <td>{match.gpmXpm}</td>
                    <td>
                      <StatusChip tone={match.analyzed === "Analyzing" ? "cyan" : "muted"}>
                        {match.analyzed}
                      </StatusChip>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button className="role-load-more" type="button">
            Load More Battle Records
          </button>
        </RolePanel>
      </div>
    </div>
  );
}
