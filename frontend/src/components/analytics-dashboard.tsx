"use client";

import {
  Activity,
  BarChart3,
  Clock3,
  DatabaseZap,
  RefreshCw,
  ShieldCheck,
  Swords,
  Trophy,
  UsersRound
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { SectionHeader } from "@/components/section-header";
import { TelemetryCard } from "@/components/telemetry-card";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import { ApiRequestError } from "@/lib/api";
import {
  getMyPlayerAnalytics,
  getMyTeamAnalytics,
  getOrganizerAnalytics,
  getPublicAnalyticsSnapshot,
  refreshAnalyticsAdmin,
  type AnalyticsMatchHistory,
  type AnalyticsRefreshResult,
  type AnalyticsSnapshot,
  type CurrentTeamAnalyticsResponse,
  type HeroAnalyticsMetric,
  type OrganizerAnalyticsResponse,
  type PlayerAnalyticsMetric,
  type PlayerAnalyticsResponse,
  type TeamAnalyticsMetric,
  type TournamentAnalyticsMetric
} from "@/lib/analytics-data";
import { getCurrentUserProfile, type CurrentUserProfile } from "@/lib/auth";
import { formatPercent } from "@/lib/utils";

type RoleAnalyticsState =
  | {
      kind: "admin";
      organizer: OrganizerAnalyticsResponse;
    }
  | {
      kind: "organizer";
      organizer: OrganizerAnalyticsResponse;
    }
  | {
      kind: "player";
      personal: PlayerAnalyticsResponse;
      team: CurrentTeamAnalyticsResponse;
    }
  | {
      kind: "unsupported";
      role: string | null;
    };

function emptySnapshot(): AnalyticsSnapshot {
  return {
    heroes: [],
    players: [],
    teams: [],
    tournaments: []
  };
}

function secondsToDuration(value: number | null) {
  if (!value || value <= 0) {
    return "No data";
  }

  const minutes = Math.floor(value / 60);
  const seconds = Math.round(value % 60);
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function numberOrNoData(value: number, digits = 1) {
  return value > 0 ? value.toFixed(digits) : "No data";
}

function integerOrNoData(value: number) {
  return value > 0 ? value.toLocaleString("en-US") : "No data";
}

function countMetricValue(value: number | null | undefined) {
  return typeof value === "number" && Number.isFinite(value)
    ? value.toLocaleString("en-US")
    : "No data";
}

function average(values: number[]) {
  const usable = values.filter((value) => Number.isFinite(value) && value > 0);

  if (usable.length === 0) {
    return 0;
  }

  return usable.reduce((total, value) => total + value, 0) / usable.length;
}

function allEmpty(snapshot: AnalyticsSnapshot) {
  return (
    snapshot.heroes.length === 0 &&
    snapshot.players.length === 0 &&
    snapshot.teams.length === 0 &&
    snapshot.tournaments.length === 0
  );
}

function analyticsErrorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) {
      return "Login session expired. Please log in again to view analytics.";
    }

    if (error.status === 403) {
      return "You do not have permission to view this analytics workspace.";
    }

    return error.message;
  }

  return error instanceof Error ? error.message : "Analytics unavailable.";
}

function roleModeLabel(state: RoleAnalyticsState | null) {
  if (state?.kind === "player") {
    return "Player analytics";
  }

  if (state?.kind === "organizer") {
    return "Organizer analytics";
  }

  if (state?.kind === "admin") {
    return "Admin analytics";
  }

  return "Authenticated analytics";
}

function roleMetricValue(state: RoleAnalyticsState | null, publicSummaryMatches: number) {
  if (state?.kind === "player") {
    const personalGames = state.personal.metrics.reduce((total, metric) => total + metric.gamesPlayed, 0);
    const teamGames = state.team.teamSummary.reduce((total, metric) => total + metric.gamesPlayed, 0);
    return personalGames + teamGames;
  }

  if (state?.kind === "organizer" || state?.kind === "admin") {
    return state.organizer.processedMatchGames;
  }

  return publicSummaryMatches;
}

async function loadRoleAnalytics(profile: CurrentUserProfile | null): Promise<RoleAnalyticsState> {
  if (profile?.role === "player") {
    const [personal, team] = await Promise.all([
      getMyPlayerAnalytics(),
      getMyTeamAnalytics()
    ]);

    return {
      kind: "player",
      personal,
      team
    };
  }

  if (profile?.role === "organizer") {
    return {
      kind: "organizer",
      organizer: await getOrganizerAnalytics()
    };
  }

  if (profile?.role === "admin") {
    return {
      kind: "admin",
      organizer: await getOrganizerAnalytics()
    };
  }

  return {
    kind: "unsupported",
    role: profile?.role ?? null
  };
}

export function AnalyticsDashboard() {
  const [canRefreshAnalytics, setCanRefreshAnalytics] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [profile, setProfile] = useState<CurrentUserProfile | null>(null);
  const [publicAggregateError, setPublicAggregateError] = useState<string | null>(null);
  const [refreshResult, setRefreshResult] = useState<AnalyticsRefreshResult | null>(null);
  const [roleAnalytics, setRoleAnalytics] = useState<RoleAnalyticsState | null>(null);
  const [snapshot, setSnapshot] = useState<AnalyticsSnapshot>(emptySnapshot);

  const loadAnalytics = useCallback(async () => {
    const currentProfile = await getCurrentUserProfile();
    const nextRoleAnalytics = await loadRoleAnalytics(currentProfile);

    let nextSnapshot = emptySnapshot();
    let nextPublicAggregateError: string | null = null;

    try {
      nextSnapshot = await getPublicAnalyticsSnapshot({ limit: 50 });
    } catch (caught) {
      nextPublicAggregateError = analyticsErrorMessage(caught);
    }

    setCanRefreshAnalytics(currentProfile?.role === "admin");
    setError(null);
    setProfile(currentProfile);
    setPublicAggregateError(nextPublicAggregateError);
    setRoleAnalytics(nextRoleAnalytics);
    setSnapshot(nextSnapshot);
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function load() {
      setIsLoading(true);

      try {
        await loadAnalytics();
      } catch (caught) {
        if (isMounted) {
          setError(analyticsErrorMessage(caught));
          setRoleAnalytics(null);
          setSnapshot(emptySnapshot());
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      isMounted = false;
    };
  }, [loadAnalytics]);

  const liveSync = useTournamentLiveRefresh({
    enabled: !isLoading,
    hiddenIntervalMs: 60_000,
    intervalMs: 60_000,
    label: "analytics dashboard",
    onRefresh: loadAnalytics
  });

  async function refreshAnalytics() {
    setIsRefreshing(true);
    setRefreshResult(null);
    setError(null);

    try {
      const result = await refreshAnalyticsAdmin();
      setRefreshResult(result);
      await loadAnalytics();
    } catch (caught) {
      setError(analyticsErrorMessage(caught));
    } finally {
      setIsRefreshing(false);
    }
  }

  const publicSummary = useMemo(() => {
    const topHero = [...snapshot.heroes].sort((a, b) => b.gamesPlayed - a.gamesPlayed)[0] ?? null;
    const avgDuration = average(
      snapshot.tournaments
        .map((tournament) => tournament.avgDurationSeconds ?? 0)
    );
    const avgKda = average([
      ...snapshot.teams.map((team) => team.avgKda),
      ...snapshot.players.map((player) => player.kda),
      ...snapshot.heroes.map((hero) => hero.kda)
    ]);
    const analyzedMatches = snapshot.tournaments.reduce(
      (total, tournament) => total + tournament.gamesPlayed,
      0
    );

    return {
      analyzedMatches,
      avgDuration,
      avgKda,
      playersTracked: snapshot.players.length,
      teamsTracked: snapshot.teams.length,
      topHero
    };
  }, [snapshot]);

  const primaryMetricValue = roleMetricValue(roleAnalytics, publicSummary.analyzedMatches);

  if (isLoading) {
    return (
      <div className="analytics-terminal real-analytics-dashboard">
        <section className="analytics-terminal-header ops-panel">
          <div className="analytics-terminal-copy">
            <p className="ops-label">DotaOps Analytics Terminal</p>
            <h1>Analytics Terminal</h1>
            <p className="ops-mono">Loading role-based backend analytics.</p>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="analytics-terminal real-analytics-dashboard">
      <section className="analytics-terminal-header ops-panel">
        <div className="analytics-terminal-copy">
          <p className="ops-label">DOTAOPS ANALYTICS ENGINE</p>
          <h1>Analytics Terminal</h1>
          <p className="ops-mono">
            Role-based analytics from backend APIs, with public aggregate metrics retained as a
            secondary read-only signal.
          </p>
        </div>

        <div className="analytics-terminal-status">
          <div>
            <DatabaseZap size={18} />
            <span className="ops-label">Data source</span>
            <strong className="ops-data">BACKEND</strong>
          </div>
          <div>
            <ShieldCheck size={18} />
            <span className="ops-label">Access</span>
            <strong className="ops-data">{profile?.role?.toUpperCase() ?? "AUTH"}</strong>
          </div>
          <div>
            <Activity size={18} />
            <span className="ops-label">Metrics</span>
            <strong className="ops-data">{integerOrNoData(primaryMetricValue)}</strong>
          </div>
        </div>

        <div className="analytics-terminal-strip">
          <article>
            <span className="ops-label">Analytics mode</span>
            <strong>{roleModeLabel(roleAnalytics)}</strong>
          </article>
          <article>
            <span className="ops-label">Top public hero</span>
            <strong>{publicSummary.topHero?.localizedName ?? "No data"}</strong>
          </article>
          <article>
            <span className="ops-label">Live sync</span>
            <button className="analytics-refresh-ghost" onClick={() => void liveSync.refreshNow()} type="button">
              <RefreshCw size={14} />
              Refresh
            </button>
          </article>
        </div>
      </section>

      {error ? (
        <section className="analytics-state-panel ops-panel">
          <strong>Analytics unavailable.</strong>
          <p>{error}</p>
        </section>
      ) : null}

      {!error && roleAnalytics ? <RoleAnalyticsPanel state={roleAnalytics} /> : null}

      {canRefreshAnalytics ? (
        <section className="analytics-admin-panel ops-panel">
          <SectionHeader
            eyebrow="Admin operation"
            title="Analytics Refresh"
            description="Refreshes backend analytics materialized views. This action is only visible to admin accounts."
            action={
              <button className="button ops-button-primary" disabled={isRefreshing} onClick={() => void refreshAnalytics()} type="button">
                <RefreshCw size={16} />
                {isRefreshing ? "Refreshing..." : "Refresh Analytics Engine"}
              </button>
            }
          />
          {refreshResult ? (
            <div className="analytics-refresh-result">
              <span className="ops-badge">{refreshResult.status}</span>
              <strong>{refreshResult.message}</strong>
              <p className="ops-mono">
                {refreshResult.durationMs === null ? "Duration unavailable" : `${refreshResult.durationMs}ms`}
              </p>
            </div>
          ) : null}
        </section>
      ) : null}

      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Public analytics aggregate"
          title="Read-only Public Metrics"
          description="General tournament-wide analytics remain available as a secondary aggregate signal."
        />
        {publicAggregateError ? (
          <AnalyticsEmptyBlock title="Public aggregate unavailable." detail={publicAggregateError} />
        ) : null}
        {!publicAggregateError && allEmpty(snapshot) ? (
          <AnalyticsEmptyBlock
            title="No imported match analytics yet."
            detail="Import OpenDota matches first. Public analytics will appear after backend processing."
          />
        ) : null}
      </section>

      {!publicAggregateError ? (
        <>
          <section className="analytics-telemetry-grid">
            <TelemetryCard
              icon={DatabaseZap}
              label="Public analyzed matches"
              value={integerOrNoData(publicSummary.analyzedMatches)}
              delta="backend aggregate"
              tone="cyan"
            />
            <TelemetryCard
              icon={Clock3}
              label="Avg duration"
              value={secondsToDuration(publicSummary.avgDuration)}
              delta="public tournament aggregate"
              tone="green"
            />
            <TelemetryCard
              icon={Swords}
              label="Avg KDA"
              value={numberOrNoData(publicSummary.avgKda)}
              delta="teams / players / heroes"
              tone="gold"
            />
            <TelemetryCard
              icon={BarChart3}
              label="Top hero"
              value={publicSummary.topHero?.localizedName ?? "No data"}
              delta={publicSummary.topHero ? `${publicSummary.topHero.gamesPlayed} games` : "missing metrics"}
              tone="red"
            />
          </section>

          <section className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Hero performance"
              title="Hero Performance Matrix"
              description="Win rate, KDA, and damage metrics from backend hero analytics."
            />
            <HeroMatrix heroes={snapshot.heroes} />
          </section>

          <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
            <div className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Team comparison"
                title="Team Power Index"
                description="Team-level win rate, KDA, and economy aggregates."
              />
              <TeamMatrix teams={snapshot.teams} />
            </div>

            <div className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Player telemetry"
                title="Player Performance"
                description="Public player metrics from imported match player records."
              />
              <PlayerMatrix players={snapshot.players} />
            </div>
          </section>

          <section className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Tournament aggregates"
              title="Tournament Analytics"
              description="Backend-calculated tournament summaries and most picked heroes."
            />
            <TournamentMatrix tournaments={snapshot.tournaments} />
          </section>
        </>
      ) : null}
    </div>
  );
}

function RoleAnalyticsPanel({ state }: { state: RoleAnalyticsState }) {
  if (state.kind === "player") {
    return <PlayerRoleAnalyticsPanel personal={state.personal} team={state.team} />;
  }

  if (state.kind === "organizer") {
    return <OrganizerRoleAnalyticsPanel analytics={state.organizer} mode="organizer" />;
  }

  if (state.kind === "admin") {
    return <OrganizerRoleAnalyticsPanel analytics={state.organizer} mode="admin" />;
  }

  return (
    <section className="analytics-state-panel ops-panel">
      <strong>Analytics workspace unavailable for this role.</strong>
      <p>Role-specific analytics are available for player, organizer, and admin accounts.</p>
    </section>
  );
}

function PlayerRoleAnalyticsPanel({
  personal,
  team
}: {
  personal: PlayerAnalyticsResponse;
  team: CurrentTeamAnalyticsResponse;
}) {
  const primaryMetric = personal.metrics[0] ?? null;
  const teamMetric = team.teamSummary[0] ?? null;

  return (
    <>
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Player analytics"
          title="Personal Performance"
          description="Protected analytics scoped to the current player profile."
        />
        <section className="analytics-telemetry-grid">
          <TelemetryCard
            icon={DatabaseZap}
            label="Games played"
            value={primaryMetric ? integerOrNoData(primaryMetric.gamesPlayed) : "No data"}
            delta={primaryMetric ? `${primaryMetric.wins}-${primaryMetric.losses} W-L` : "personal endpoint"}
            tone="cyan"
          />
          <TelemetryCard
            icon={Trophy}
            label="Win rate"
            value={primaryMetric ? formatPercent(primaryMetric.winRate) : "No data"}
            delta="personal matches"
            tone="green"
          />
          <TelemetryCard
            icon={Swords}
            label="KDA"
            value={primaryMetric ? primaryMetric.kda.toFixed(2) : "No data"}
            delta={primaryMetric ? `${primaryMetric.avgKills.toFixed(1)} / ${primaryMetric.avgDeaths.toFixed(1)} / ${primaryMetric.avgAssists.toFixed(1)}` : "kills / deaths / assists"}
            tone="gold"
          />
          <TelemetryCard
            icon={BarChart3}
            label="Avg damage"
            value={primaryMetric ? Math.round(primaryMetric.avgHeroDamage).toLocaleString("en-US") : "No data"}
            delta="hero damage"
            tone="red"
          />
        </section>
        <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
          <div className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Personal hero pool"
              title="Hero Performance"
              description="Hero analytics scoped to your profile."
            />
            <HeroMatrix heroes={personal.heroPerformance} />
          </div>
          <div className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Personal match history"
              title="Analyzed Matches"
              description="Imported OpenDota matches connected to your player profile."
            />
            <MatchHistoryList emptyText="No analyzed personal matches yet." matches={personal.matchHistory} />
          </div>
        </section>
      </section>

      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Team analytics"
          title={team.team ? team.team.name : "No active team"}
          description={team.team ? "Protected analytics scoped to your current team." : "Join or create a team to unlock team analytics."}
        />
        {team.team ? (
          <>
            <section className="analytics-telemetry-grid">
              <TelemetryCard
                icon={UsersRound}
                label="Team games"
                value={teamMetric ? integerOrNoData(teamMetric.gamesPlayed) : "No data"}
                delta={teamMetric ? `${teamMetric.wins}-${teamMetric.losses} W-L` : "team endpoint"}
                tone="cyan"
              />
              <TelemetryCard
                icon={Trophy}
                label="Team win rate"
                value={teamMetric ? formatPercent(teamMetric.winRate) : "No data"}
                delta="team summary"
                tone="green"
              />
              <TelemetryCard
                icon={Swords}
                label="Team KDA"
                value={teamMetric ? teamMetric.avgKda.toFixed(2) : "No data"}
                delta="team average"
                tone="gold"
              />
              <TelemetryCard
                icon={BarChart3}
                label="Roster rows"
                value={integerOrNoData(team.rosterPerformance.length)}
                delta="roster performance"
                tone="red"
              />
            </section>
            <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
              <div className="analytics-terminal-panel analytics-data-panel ops-panel">
                <SectionHeader
                  eyebrow="Team summary"
                  title="Team Performance"
                  description="Team-level win rate, KDA, and economy metrics."
                />
                <TeamMatrix teams={team.teamSummary} />
              </div>
              <div className="analytics-terminal-panel analytics-data-panel ops-panel">
                <SectionHeader
                  eyebrow="Roster performance"
                  title="Player Comparison"
                  description="Protected player rows for current team members."
                />
                <PlayerMatrix players={team.rosterPerformance} />
              </div>
            </section>
            <section className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Team match history"
                title="Analyzed Team Matches"
                description="Imported OpenDota matches connected to your current team."
              />
              <MatchHistoryList emptyText="No analyzed team matches yet." matches={team.recentTeamMatches} />
            </section>
          </>
        ) : (
          <AnalyticsEmptyBlock
            title="Team analytics unavailable."
            detail="The backend returned no current team for this player account."
          />
        )}
      </section>
    </>
  );
}

function OrganizerRoleAnalyticsPanel({
  analytics,
  mode
}: {
  analytics: OrganizerAnalyticsResponse;
  mode: "admin" | "organizer";
}) {
  return (
    <section className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow={mode === "admin" ? "Admin analytics" : "Organizer analytics"}
        title={mode === "admin" ? "Global Analytics Overview" : "Organizer Operations Overview"}
        description={
          mode === "admin"
            ? "Admin/global analytics counts from the organizer analytics endpoint."
            : "Operational analytics scoped to tournaments you can manage."
        }
      />
      <section className="analytics-telemetry-grid">
        <TelemetryCard
          icon={Trophy}
          label="Tournaments"
          value={countMetricValue(analytics.tournaments)}
          delta={mode === "admin" ? "global scope" : "managed workspace"}
          tone="cyan"
        />
        <TelemetryCard
          icon={UsersRound}
          label="Pending registrations"
          value={countMetricValue(analytics.pendingRegistrations)}
          delta={`${countMetricValue(analytics.approvedRegistrations)} approved`}
          tone="gold"
        />
        <TelemetryCard
          icon={Activity}
          label="Active/published"
          value={countMetricValue(analytics.activePublishedTournaments)}
          delta="published or live"
          tone="green"
        />
        <TelemetryCard
          icon={DatabaseZap}
          label="Processed games"
          value={countMetricValue(analytics.processedMatchGames)}
          delta={`${countMetricValue(analytics.importJobs)} import jobs`}
          tone="red"
        />
      </section>
      {analytics.tournaments === 0 &&
      analytics.pendingRegistrations === 0 &&
      analytics.approvedRegistrations === 0 &&
      analytics.activePublishedTournaments === 0 &&
      analytics.processedMatchGames === 0 &&
      analytics.importJobs === 0 ? (
        <AnalyticsEmptyBlock
          title="No organizer analytics yet."
          detail="Create or publish tournaments and import match data to populate this role-specific overview."
        />
      ) : null}
    </section>
  );
}

function MatchHistoryList({
  emptyText,
  matches
}: {
  emptyText: string;
  matches: AnalyticsMatchHistory[];
}) {
  if (matches.length === 0) {
    return <AnalyticsEmptyBlock title={emptyText} detail="Match history rows will appear after backend analytics links imported match records." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Dota Match ID</th>
            <th>Match</th>
            <th>Game</th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match, index) => (
            <tr key={`${match.matchId ?? "match"}-${match.matchGameId ?? index}`}>
              <td>
                <strong>{match.dotaMatchId ?? "No data"}</strong>
                <span>Backend match history</span>
              </td>
              <td>{match.matchId ?? "No data"}</td>
              <td>{match.matchGameId ?? "No data"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function HeroMatrix({ heroes }: { heroes: HeroAnalyticsMetric[] }) {
  if (heroes.length === 0) {
    return <AnalyticsEmptyBlock title="No hero metrics available." detail="Hero performance rows will appear after imported matches include hero data." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Hero</th>
            <th>Games</th>
            <th>W-L</th>
            <th>Win Rate</th>
            <th>KDA</th>
            <th>Avg Damage</th>
          </tr>
        </thead>
        <tbody>
          {heroes.slice(0, 12).map((hero) => (
            <tr key={`${hero.heroId}-${hero.tournamentId ?? "global"}`}>
              <td>
                <strong>{hero.localizedName}</strong>
                <span>{hero.tournamentName ?? "Tournament aggregate"}</span>
              </td>
              <td>{hero.gamesPlayed}</td>
              <td>{hero.wins}-{hero.losses}</td>
              <td>{formatPercent(hero.winRate)}</td>
              <td>{hero.kda.toFixed(2)}</td>
              <td>{Math.round(hero.avgHeroDamage).toLocaleString("en-US")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TeamMatrix({ teams }: { teams: TeamAnalyticsMetric[] }) {
  if (teams.length === 0) {
    return <AnalyticsEmptyBlock title="No team metrics available." detail="Team comparison stays empty until backend analytics receives imported team match data." />;
  }

  return (
    <div className="analytics-real-stack">
      {[...teams]
        .sort((a, b) => b.winRate - a.winRate)
        .slice(0, 8)
        .map((team, index) => (
          <article className="analytics-rank-row" key={`${team.teamId}-${team.tournamentId ?? "global"}`}>
            <span className="ops-mono">#{String(index + 1).padStart(2, "0")}</span>
            <div>
              <strong>{team.teamName}</strong>
              <p>{team.tournamentName ?? "Tournament aggregate"}</p>
            </div>
            <div>
              <span>{formatPercent(team.winRate)}</span>
              <em>KDA {team.avgKda.toFixed(2)}</em>
            </div>
          </article>
        ))}
    </div>
  );
}

function PlayerMatrix({ players }: { players: PlayerAnalyticsMetric[] }) {
  if (players.length === 0) {
    return <AnalyticsEmptyBlock title="No player metrics available." detail="Player telemetry will populate from imported OpenDota player records." />;
  }

  return (
    <div className="analytics-real-stack">
      {players.slice(0, 8).map((player) => (
        <article className="analytics-rank-row" key={`${player.profileId}-${player.tournamentId ?? "global"}`}>
          <span className="ops-mono">{player.displayName.slice(0, 2).toUpperCase()}</span>
          <div>
            <strong>{player.displayName}</strong>
            <p>{player.teamName ?? "No team"} / {player.tournamentName ?? "Tournament aggregate"}</p>
          </div>
          <div>
            <span>{player.kda.toFixed(2)}</span>
            <em>{formatPercent(player.winRate)} WR</em>
          </div>
        </article>
      ))}
    </div>
  );
}

function TournamentMatrix({ tournaments }: { tournaments: TournamentAnalyticsMetric[] }) {
  if (tournaments.length === 0) {
    return <AnalyticsEmptyBlock title="No tournament metrics available." detail="Tournament aggregates require processed match analytics from the backend." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Tournament</th>
            <th>Matches</th>
            <th>Duration</th>
            <th>Avg KDA</th>
            <th>Teams</th>
            <th>Top Heroes</th>
          </tr>
        </thead>
        <tbody>
          {tournaments.map((tournament) => (
            <tr key={tournament.tournamentId}>
              <td>
                <strong>{tournament.tournamentName}</strong>
                <span>Backend calculated</span>
              </td>
              <td>{tournament.gamesPlayed}</td>
              <td>{secondsToDuration(tournament.avgDurationSeconds)}</td>
              <td>{tournament.avgKda.toFixed(2)}</td>
              <td>{tournament.teamsCount}</td>
              <td>
                {tournament.mostPickedHeroes.length > 0
                  ? tournament.mostPickedHeroes
                    .slice(0, 3)
                    .map((hero) => hero.localizedName)
                    .join(", ")
                  : "No data"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AnalyticsEmptyBlock({
  detail,
  title
}: {
  detail: string;
  title: string;
}) {
  return (
    <div className="analytics-empty-block">
      <span className="ops-label">Awaiting backend data</span>
      <strong>{title}</strong>
      <p>{detail}</p>
    </div>
  );
}
