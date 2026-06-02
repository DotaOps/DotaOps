"use client";

import {
  Activity,
  BarChart3,
  Clock3,
  DatabaseZap,
  FileWarning,
  RefreshCw,
  ShieldCheck,
  Swords,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { SectionHeader } from "@/components/section-header";
import { TelemetryCard } from "@/components/telemetry-card";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import {
  getPublicAnalyticsSnapshot,
  refreshAnalyticsAdmin,
  type AnalyticsRefreshResult,
  type AnalyticsSnapshot,
  type HeroAnalyticsMetric,
  type PlayerAnalyticsMetric,
  type TeamAnalyticsMetric,
  type TournamentAnalyticsMetric
} from "@/lib/analytics-data";
import { getCurrentUserProfile } from "@/lib/auth";
import { formatPercent } from "@/lib/utils";

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

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Analytics unavailable.";
}

export function AnalyticsDashboard() {
  const [canRefreshAnalytics, setCanRefreshAnalytics] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [refreshResult, setRefreshResult] = useState<AnalyticsRefreshResult | null>(null);
  const [snapshot, setSnapshot] = useState<AnalyticsSnapshot>(emptySnapshot);

  const loadAnalytics = useCallback(async () => {
    const nextSnapshot = await getPublicAnalyticsSnapshot({ limit: 50 });
    setSnapshot(nextSnapshot);
    setError(null);
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function load() {
      setIsLoading(true);

      try {
        const [profile, nextSnapshot] = await Promise.all([
          getCurrentUserProfile().catch(() => null),
          getPublicAnalyticsSnapshot({ limit: 50 })
        ]);

        if (!isMounted) {
          return;
        }

        setCanRefreshAnalytics(profile?.role === "admin");
        setSnapshot(nextSnapshot);
        setError(null);
      } catch (caught) {
        if (isMounted) {
          setError(errorMessage(caught));
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
  }, []);

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
      setError(errorMessage(caught));
    } finally {
      setIsRefreshing(false);
    }
  }

  const summary = useMemo(() => {
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

  if (isLoading) {
    return (
      <div className="analytics-terminal real-analytics-dashboard">
        <section className="analytics-terminal-header ops-panel">
          <div className="analytics-terminal-copy">
            <p className="ops-label">DotaOps Analytics Terminal</p>
            <h1>Analytics Terminal</h1>
            <p className="ops-mono">Loading backend-calculated analytics.</p>
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
            Aggregated metrics from public tournament data. Role-specific player and organizer
            analytics will appear after backend endpoints are connected.
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
            <strong className="ops-data">AUTHENTICATED</strong>
          </div>
          <div>
            <Activity size={18} />
            <span className="ops-label">Metrics</span>
            <strong className="ops-data">{integerOrNoData(summary.analyzedMatches)}</strong>
          </div>
        </div>

        <div className="analytics-terminal-strip">
          <article>
            <span className="ops-label">Analytics mode</span>
            <strong>Authenticated analytics overview</strong>
          </article>
          <article>
            <span className="ops-label">Top hero</span>
            <strong>{summary.topHero?.localizedName ?? "No data"}</strong>
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

      {!error && allEmpty(snapshot) ? (
        <section className="analytics-state-panel analytics-empty-state ops-panel">
          <div>
            <FileWarning size={22} />
            <span className="ops-label">Analytics pipeline idle</span>
            <strong>No imported match analytics yet.</strong>
            <p>Import OpenDota matches first. Analytics will appear after backend processing.</p>
          </div>
          <div className="analytics-empty-steps">
            <span>OpenDota import required</span>
            <span>Backend processing required</span>
            <span>Role-specific views pending backend endpoints</span>
          </div>
        </section>
      ) : null}

      <section className="analytics-telemetry-grid">
        <TelemetryCard
          icon={DatabaseZap}
          label="Analyzed matches"
          value={integerOrNoData(summary.analyzedMatches)}
          delta="backend aggregate"
          tone="cyan"
        />
        <TelemetryCard
          icon={Clock3}
          label="Avg duration"
          value={secondsToDuration(summary.avgDuration)}
          delta="public tournament aggregate"
          tone="green"
        />
        <TelemetryCard
          icon={Swords}
          label="Avg KDA"
          value={numberOrNoData(summary.avgKda)}
          delta="teams / players / heroes"
          tone="gold"
        />
        <TelemetryCard
          icon={BarChart3}
          label="Top hero"
          value={summary.topHero?.localizedName ?? "No data"}
          delta={summary.topHero ? `${summary.topHero.gamesPlayed} games` : "missing metrics"}
          tone="red"
        />
      </section>

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
