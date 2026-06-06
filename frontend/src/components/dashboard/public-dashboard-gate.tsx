import { LockKeyhole, Trophy } from "lucide-react";
import Link from "next/link";

export function PublicDashboardGate() {
  return (
    <div className="role-dashboard role-dashboard-public">
      <div className="role-public-shell">
        <section className="role-public-card">
          <div className="role-public-icon" aria-hidden="true">
            <LockKeyhole size={28} />
          </div>
          <p className="role-hero-kicker">Restricted Dashboard</p>
          <h1>Dashboard access requires login</h1>
          <p>
            Public visitors can view tournaments, schedules, brackets and results. Private team,
            player and organizer dashboards are available after authentication.
          </p>
          <div className="role-public-actions">
            <Link className="role-action-button role-action-primary" href="/turnirji">
              <Trophy size={18} />
              <span>View Public Tournaments</span>
            </Link>
            <button className="role-action-button role-action-secondary is-disabled" type="button" disabled>
              Sign in to open your dashboard.
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
