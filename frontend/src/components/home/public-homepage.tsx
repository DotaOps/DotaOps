import {
  Activity,
  ArrowRight,
  BarChart3,
  CalendarDays,
  Clock,
  Shield,
  Trophy
} from "lucide-react";
import Link from "next/link";
import { AnimatedHomepageBackground } from "@/components/home/animated-homepage-background";
import { HeaderProfileLink } from "@/components/header-profile-link";
import type { PublicHomepageData } from "@/lib/homepage-data";
import type { Tournament } from "@/lib/types";

function formatCount(value: number | null) {
  return value === null ? "No data" : String(value);
}

function formatDate(value: string | null | undefined) {
  if (!value) {
    return "Schedule pending";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "Schedule pending";
  }

  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const month = months[date.getUTCMonth()] ?? "Jan";
  const day = String(date.getUTCDate()).padStart(2, "0");
  const year = date.getUTCFullYear();
  const hour = String(date.getUTCHours()).padStart(2, "0");
  const minute = String(date.getUTCMinutes()).padStart(2, "0");

  return `${month} ${day}, ${year}, ${hour}:${minute} UTC`;
}

function formatDuration(seconds: number | null) {
  if (seconds === null) {
    return "No data";
  }

  const minutes = Math.round(seconds / 60);
  return `${minutes} min`;
}

function statusLabel(status: Tournament["status"]) {
  return status
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function registrationLabel(tournament: Tournament) {
  if (tournament.teamsCount > 0) {
    return `${tournament.registrationsCount}/${tournament.teamsCount} teams`;
  }

  return `${tournament.registrationsCount} registered`;
}

export function PublicHomepage({
  avatarUrl = null,
  displayName = "Profile",
  homepageData,
  isAuthenticated = false
}: {
  avatarUrl?: string | null;
  displayName?: string;
  homepageData: PublicHomepageData;
  isAuthenticated?: boolean;
}) {
  const platformStats = [
    {
      icon: Trophy,
      label: "Public tournaments",
      value: formatCount(homepageData.publicTournamentCount)
    },
    {
      icon: CalendarDays,
      label: "Registrations open",
      value: formatCount(homepageData.registrationOpenCount)
    },
    {
      icon: Activity,
      label: "Active or published",
      value: formatCount(homepageData.activeOrPublishedCount)
    },
    {
      icon: BarChart3,
      label: "Analyzed matches",
      value: formatCount(homepageData.analyzedMatchesCount)
    }
  ];
  const featuredList = homepageData.featuredTournaments.slice(0, 6);
  const analyticsHighlights = [
    {
      label: "Top hero",
      value: homepageData.topHeroPreview?.localizedName ?? "No data",
      meta: homepageData.topHeroPreview
        ? `${homepageData.topHeroPreview.gamesPlayed} games | ${homepageData.topHeroPreview.winRate.toFixed(1)}% win rate`
        : "Hero analytics will appear after match imports."
    },
    {
      label: "Top team metric",
      value: homepageData.topTeamPreview?.teamName ?? "No data",
      meta: homepageData.topTeamPreview
        ? `${homepageData.topTeamPreview.gamesPlayed} games | ${homepageData.topTeamPreview.winRate.toFixed(1)}% win rate`
        : "Team insights will appear after tournament matches are available."
    },
    {
      label: "Average duration",
      value: formatDuration(homepageData.averageDurationSeconds),
      meta:
        homepageData.averageDurationSeconds === null
          ? "Tournament duration metrics are not available yet."
          : "Based on processed public match results."
    }
  ];

  return (
    <div className="public-home">
      <AnimatedHomepageBackground />
      <header className="public-home-header">
        <Link href="/" className="public-brand">
          DotaOps
        </Link>
        <nav aria-label="Public navigation">
          {isAuthenticated ? (
            <HeaderProfileLink avatarUrl={avatarUrl} displayName={displayName} />
          ) : (
            <>
              <Link href="/turnirji">Tournaments</Link>
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
            <span>Public Operations Hub</span>
            <h1>
              Dota 2 tournament operations <strong>&amp; analytics</strong>
            </h1>
            <p>
              A public command center for Dota 2 tournaments, team registration windows, and
              match insights. Browse the tournament catalog, then create an account when your
              roster is ready to operate.
            </p>
            <div className="public-hero-actions">
              <Link
                className="public-button public-button-primary"
                href={isAuthenticated ? "/dashboard" : "/register"}
              >
                {isAuthenticated ? "Open Dashboard" : "Create account"}
              </Link>
              <Link className="public-button public-button-secondary" href="/turnirji">
                Browse tournaments
              </Link>
              {!isAuthenticated && (
                <Link className="public-button public-button-secondary" href="/login">
                  Login
                </Link>
              )}
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
              <span>Featured Public Tournaments</span>
              <h2>{featuredList.length > 0 ? "Featured tournament circuits" : "Tournament feed warming up"}</h2>
              <p>
                {featuredList.length > 0
                  ? "Discover active Dota 2 tournaments, registration windows, and competitive formats."
                  : "No public tournaments are published yet."}
              </p>
            </div>
            <Link href="/turnirji">
              Full tournament hub <ArrowRight size={16} />
            </Link>
          </div>

          {featuredList.length > 0 ? (
            <div className="public-tournament-grid">
              {featuredList.map((tournament) => (
                <article className="public-tournament-card" key={tournament.id}>
                  <div className="public-tournament-card-head">
                    <span>{statusLabel(tournament.status)}</span>
                    <Trophy size={22} />
                  </div>
                  <h3>{tournament.title}</h3>
                  <p>{tournament.description || "No tournament description available."}</p>
                  <dl className="public-stat-list">
                    <div>
                      <dt>Format</dt>
                      <dd>{tournament.format}</dd>
                    </div>
                    <div>
                      <dt>Schedule</dt>
                      <dd>{formatDate(tournament.startsAt)}</dd>
                    </div>
                    <div>
                      <dt>Registration</dt>
                      <dd>{registrationLabel(tournament)}</dd>
                    </div>
                  </dl>
                  <Link className="public-card-link" href={`/turnirji/${tournament.slug}`}>
                    View tournament <ArrowRight size={15} />
                  </Link>
                </article>
              ))}
            </div>
          ) : (
            <article className="public-empty-card">
              <Trophy size={32} />
              <h3>No public tournaments published yet.</h3>
              <p>Published tournament circuits will appear here as soon as organizers make them public.</p>
              <Link className="public-button public-button-secondary" href="/turnirji">
                Open tournaments
              </Link>
            </article>
          )}
        </section>

        <section className="public-section public-ops-section">
          <div className="public-landing-split">
            <div className="public-registration-panel">
              <h2>
                <CalendarDays size={22} /> Registration Windows
              </h2>
              {homepageData.upcomingTournaments.length > 0 ? (
                <div className="public-match-list">
                  {homepageData.upcomingTournaments.map((tournament) => (
                    <Link
                      className="public-match-row public-tournament-row"
                      href={`/turnirji/${tournament.slug}`}
                      key={tournament.id}
                    >
                      <span>{statusLabel(tournament.status)}</span>
                      <strong>
                        {tournament.title} <em>{tournament.format}</em>
                      </strong>
                      <small>
                        <Clock size={13} /> {formatDate(tournament.startsAt)}
                      </small>
                      <strong>
                        {registrationLabel(tournament)} <em>{tournament.organizer}</em>
                      </strong>
                    </Link>
                  ))}
                </div>
              ) : (
                <article className="public-empty-card public-empty-card-compact">
                  <h3>No upcoming public tournaments.</h3>
                  <p>Registration windows and published tournament schedules will be listed here.</p>
                </article>
              )}
            </div>

            <div className="public-analytics-panel">
              <h2>
                <BarChart3 size={22} /> Analytics Preview
              </h2>
              {homepageData.hasAnalyticsData ? (
                <div className="public-analytics-preview public-analytics-preview-premium">
                  {analyticsHighlights.map((item) => (
                    <article className="public-glass-card" key={item.label}>
                      <span>{item.label}</span>
                      <h3>{item.value}</h3>
                      <p>{item.meta}</p>
                    </article>
                  ))}
                </div>
              ) : (
                <article className="public-empty-card public-empty-card-compact">
                  <h3>Analytics will appear after tournament matches are available.</h3>
                  <p>Match insights will surface here once tournament results have been processed.</p>
                </article>
              )}
            </div>
          </div>
        </section>

        <section className="public-final-cta">
          <span className="public-final-cta-kicker">Team-ready tournament operations</span>
          <h2>Ready to join the operations?</h2>
          <p>
            Create an account to manage a team workspace, register for tournaments, or operate
            tournament circuits with structured team and tournament workflows.
          </p>
          <div>
            <Link
              className="public-button public-button-primary"
              href={isAuthenticated ? "/dashboard" : "/register"}
            >
              {isAuthenticated ? "Open Dashboard" : "Create account"}
            </Link>
            <Link className="public-button public-button-secondary" href="/turnirji">
              Public Tournaments
            </Link>
            {!isAuthenticated && (
              <Link className="public-button public-button-secondary" href="/login">
                Login
              </Link>
            )}
          </div>
        </section>
      </main>

      <footer className="public-footer">
        <div>
          <strong>DotaOps</strong>
          <p>(c) 2026 DotaOps Analytics Engine. All rights reserved.</p>
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
            <Shield size={16} /> Public hub
          </span>
        </nav>
      </footer>
    </div>
  );
}
