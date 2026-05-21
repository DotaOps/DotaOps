import {
  Activity,
  ArrowRight,
  BarChart3,
  CalendarDays,
  DatabaseZap,
  LayoutDashboard,
  RadioTower,
  Shield,
  Trophy,
  UserRound,
  UsersRound
} from "lucide-react";
import Link from "next/link";
import { AnimatedHomepageBackground } from "@/components/home/animated-homepage-background";

const upcomingOps = [
  { left: "Liquid", regionA: "EUW", right: "Falcons", regionB: "MENA", time: "18:00" },
  { left: "OG", regionA: "EUW", right: "Tundra", regionB: "EUW", time: "21:00" }
];

const topTeams = [
  { name: "Team Liquid", rating: "1,942", tags: ["Global #1", "Rematch 78%"] },
  { name: "Falcons", rating: "1,915", tags: ["Global #2", "Winrate 67%"] },
  { name: "Xtreme Gaming", rating: "1,888", tags: ["Global #3", "Rematch 66%"] },
  { name: "Gaimin Gladiators", rating: "1,865", tags: ["Global #5", "Winrate 62%"] }
];

const platformStats = [
  { icon: Trophy, label: "Active Tournaments", value: "14" },
  { icon: Activity, label: "Live Matches", value: "42" },
  { icon: RadioTower, label: "API Latency", value: "24ms" },
  { icon: DatabaseZap, label: "Data Points/sec", value: "1,240,492" }
];

export function PublicHomepage({ isAuthenticated = false }: { isAuthenticated?: boolean }) {
  return (
    <div className="public-home">
      <AnimatedHomepageBackground />
      <header className="public-home-header">
        <Link href="/" className="public-brand">
          DotaOps
        </Link>
        <nav aria-label="Public navigation">
          <Link href="/turnirji">Tournaments</Link>
          {isAuthenticated ? (
            <>
              <Link className="public-dashboard-link" href="/dashboard">
                <LayoutDashboard size={16} />
                <span>Dashboard</span>
              </Link>
              <Link
                aria-label="Profile"
                className="public-profile-link"
                href="/profile"
                title="Profile"
              >
                <UserRound size={18} />
              </Link>
            </>
          ) : (
            <>
              <Link href="/login">Login</Link>
              <Link href="/register">Register</Link>
            </>
          )}
        </nav>
      </header>

      <main>
        <section className="public-hero">
          <div className="public-hero-backdrop" />
          <div className="public-hero-map" aria-hidden="true" />
          <div className="public-hero-radar" aria-hidden="true" />
          <div className="public-hero-particles" aria-hidden="true" />
          <div className="public-hero-copy">
            <span>Dominate the Draft</span>
            <h1>
              Dota 2 tournament operations <strong>&amp; analytics</strong>
            </h1>
            <p>
              The elite analytical engine for professional teams and tournament organizers.
              Real-time data synthesis, tactical prediction models, and full-spectrum operation
              management.
            </p>
            <div className="public-hero-actions">
              <Link
                className="public-button public-button-primary"
                href={isAuthenticated ? "/dashboard" : "/login"}
              >
                {isAuthenticated ? "Dashboard" : "Login"}
              </Link>
              <Link
                className="public-button public-button-secondary"
                href={isAuthenticated ? "/profile" : "/register"}
              >
                {isAuthenticated ? "Profile" : "Register"}
              </Link>
            </div>
          </div>
        </section>

        <section className="public-ticker" aria-label="Public platform statistics">
          <div className="public-ticker-track">
            {[0, 1].map((group) => (
              <div className="public-ticker-group" aria-hidden={group > 0} key={group}>
                {platformStats.map((stat) => {
                  const Icon = stat.icon;

                  return (
                    <span key={`${group}-${stat.label}`}>
                      <Icon size={14} /> {stat.label}: {stat.value}
                    </span>
                  );
                })}
              </div>
            ))}
          </div>
        </section>

        <section className="public-section public-spotlight">
          <div className="public-section-heading">
            <div>
              <span>Spotlight Event</span>
              <h2>Riyadh Masters 2024</h2>
            </div>
            <Link href="/turnirji">
              Full event hub <ArrowRight size={16} />
            </Link>
          </div>

          <div className="public-spotlight-grid">
            <article className="public-event-card">
              <div className="public-event-visual">
                <Trophy size={72} />
              </div>
              <div>
                <span>Grand Finals</span>
                <span>Live Now</span>
              </div>
              <h3>Team Spirit vs Gaimin Gladiators</h3>
              <p>Series Score: 1 - 1 | Game 3 in Progress</p>
            </article>

            <aside className="public-analytics-stack">
              <article className="public-glass-card">
                <h3>Win Probability</h3>
                <div className="public-probability">
                  <span style={{ height: "92%" }}>54%</span>
                  <span style={{ height: "78%" }}>46%</span>
                </div>
                <p>Spirit holds a slight gold advantage at 20 minutes with a scaling late-game draft.</p>
              </article>
              <article className="public-glass-card">
                <h3>Match Analytics</h3>
                <dl className="public-stat-list">
                  <div>
                    <dt>Gold Diff</dt>
                    <dd>+2.4k</dd>
                  </div>
                  <div>
                    <dt>XPM Lead</dt>
                    <dd>+410</dd>
                  </div>
                  <div>
                    <dt>Towers Remaining</dt>
                    <dd>11 : 9</dd>
                  </div>
                </dl>
              </article>
            </aside>
          </div>
        </section>

        <section className="public-section public-ops-section">
          <div className="public-two-column">
            <div>
              <h2>
                <CalendarDays size={22} /> Upcoming Ops
              </h2>
              <div className="public-match-list">
                {upcomingOps.map((match) => (
                  <Link className="public-match-row" href="/turnirji" key={`${match.time}-${match.left}`}>
                    <span>{match.time}</span>
                    <strong>
                      {match.left} <em>{match.regionA}</em>
                    </strong>
                    <small>vs</small>
                    <strong>
                      {match.right} <em>{match.regionB}</em>
                    </strong>
                  </Link>
                ))}
              </div>
            </div>

            <div>
              <h2>
                <BarChart3 size={22} /> Recent Intel
              </h2>
              <article className="public-intel-card">
                <span>Completed</span>
                <div>
                  <strong>Xtreme Gaming</strong>
                  <strong>2</strong>
                </div>
                <div>
                  <strong>BetBoom</strong>
                  <strong>1</strong>
                </div>
                <p>Duration: 02:44:12</p>
              </article>
            </div>
          </div>
        </section>

        <section className="public-section public-bracket-section">
          <h2>Playoff Progression</h2>
          <div className="public-bracket-grid">
            <div>
              <span>Quarters</span>
              <article>Team Spirit 2 - Cloud9 0</article>
              <article>Falcons 2 - Aurora 1</article>
            </div>
            <div>
              <span>Semis</span>
              <article>Team Spirit - Falcons</article>
            </div>
            <div>
              <span>Grand Final</span>
              <article className="is-final">
                <Trophy size={24} />
                TBD Championship Match
              </article>
            </div>
          </div>
        </section>

        <section className="public-section public-teams-section">
          <div className="public-section-heading">
            <div>
              <h2>Top Seeded Teams</h2>
              <p>Elite organizations dominating the current professional circuit.</p>
            </div>
          </div>
          <div className="public-team-grid">
            {topTeams.map((team) => (
              <article className="public-team-card" key={team.name}>
                <div className="public-team-portrait">
                  <UsersRound size={34} />
                </div>
                <h3>{team.name}</h3>
                <div>
                  <span>ELO Rating</span>
                  <strong>{team.rating}</strong>
                </div>
                <footer>
                  {team.tags.map((tag) => (
                    <span key={tag}>{tag}</span>
                  ))}
                </footer>
              </article>
            ))}
          </div>
        </section>

        <section className="public-final-cta">
          <h2>Ready to join the operations?</h2>
          <p>
            Step into the tactical hub. Whether you are a tournament organizer, a team analyst,
            or a data-driven fan, DotaOps has the intel you need.
          </p>
          <div>
            <Link
              className="public-button public-button-primary"
              href={isAuthenticated ? "/dashboard" : "/register"}
            >
              {isAuthenticated ? "Open Dashboard" : "Get Started Free"}
            </Link>
            <Link className="public-button public-button-secondary" href="/turnirji">
              Public Tournaments
            </Link>
          </div>
        </section>
      </main>

      <footer className="public-footer">
        <div>
          <strong>DotaOps</strong>
          <p>(c) 2024 DotaOps Analytics Engine. All rights reserved.</p>
        </div>
        <nav aria-label="Footer links">
          <Link href="/turnirji">Public Tournaments</Link>
          {isAuthenticated ? (
            <>
              <Link href="/dashboard">Dashboard</Link>
              <Link href="/profile">Profile</Link>
            </>
          ) : (
            <>
              <Link href="/login">Login</Link>
              <Link href="/register">Register</Link>
            </>
          )}
          <span>
            <Shield size={16} /> System Status
          </span>
        </nav>
      </footer>
    </div>
  );
}
