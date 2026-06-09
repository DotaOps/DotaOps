"use client";

import {
  Activity,
  BarChart3,
  Clock3,
  DatabaseZap,
  Filter,
  RefreshCw,
  ShieldCheck,
  Swords,
  Trophy,
  UsersRound
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import { analyticsErrorMessage } from "@/components/analytics/analytics-errors";
import {
  countMetricValue,
  formatAnalyticsDateTime,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import {
  HeroMatrix,
  MatchHistoryList,
  PlayerHeroPerformanceTable
} from "@/components/analytics/analytics-tables";
import { ComparisonCards } from "@/components/analytics/comparison/comparison-cards";
import { PlayerComparisonPanel } from "@/components/analytics/comparison/player-comparison-panel";
import { SectionHeader } from "@/components/section-header";
import { TelemetryCard } from "@/components/telemetry-card";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import {
  compareAnalyticsTeams,
  getMyPlayerAnalytics,
  getMyTeamAnalytics,
  getMyTeamLookups,
  getOrganizerAnalytics,
  getOrganizerTournamentAnalytics,
  getOrganizerTournamentLookups,
  getPublicAnalyticsSnapshot,
  getHeroLookups,
  getTeamPlayerLookups,
  refreshAnalyticsAdmin,
  type AnalyticsFilters,
  type AnalyticsRefreshResult,
  type AnalyticsSnapshot,
  type CurrentTeamAnalyticsResponse,
  type HeroLookup,
  type HeroAnalyticsMetric,
  type OrganizerAnalyticsResponse,
  type OrganizerTournamentLookup,
  type OrganizerTournamentAnalyticsResponse,
  type PlayerAnalyticsMetric,
  type PlayerAnalyticsResponse,
  type PlayerInsight,
  type PlayerProgressPoint,
  type RecentImportMetric,
  type TeamComparisonResponse,
  type TeamLookup,
  type TeamPlayerLookup,
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

type AnalyticsFilterForm = {
  from: string;
  heroId: string;
  limit: number;
  profileId: string;
  teamId: string;
  to: string;
  tournamentId: string;
};

const DEFAULT_PUBLIC_FILTERS: AnalyticsFilterForm = {
  from: "",
  heroId: "",
  limit: 50,
  profileId: "",
  teamId: "",
  to: "",
  tournamentId: ""
};

const ANALYTICS_LIMIT_OPTIONS = [10, 25, 50, 100];

type AnalyticsTab = {
  key: string;
  label: string;
};

function tabsForRole(state: RoleAnalyticsState | null): AnalyticsTab[] {
  if (state?.kind === "player") {
    return [
      { key: "overview", label: "Overview" },
      { key: "personal", label: "Personal" },
      { key: "team", label: "Team" },
      { key: "compare", label: "Compare" },
      { key: "public", label: "Public Aggregate" },
      { key: "filters", label: "Filters" }
    ];
  }

  if (state?.kind === "organizer" || state?.kind === "admin") {
    return [
      { key: "overview", label: "Overview" },
      { key: "drilldown", label: "Tournament Drilldown" },
      { key: "compare", label: "Compare" },
      { key: "public", label: "Public Aggregate" },
      { key: "filters", label: "Filters" }
    ];
  }

  return [{ key: "overview", label: "Overview" }];
}

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

function progressResultLabel(point: PlayerProgressPoint) {
  if (point.won === true) {
    return "Win";
  }

  if (point.won === false) {
    return "Loss";
  }

  return "Result unavailable";
}

function insightMetricLabel(insight: PlayerInsight) {
  const current = safeMetricNumber(insight.currentValue, 2);

  if (insight.comparisonValue === null) {
    return `${insight.metricName}: ${current}`;
  }

  return `${insight.metricName}: ${current} vs ${safeMetricNumber(insight.comparisonValue, 2)}`;
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

function toAnalyticsIsoDateTime(value: string) {
  if (!value.trim()) {
    return undefined;
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

function filtersFromForm(form: AnalyticsFilterForm): AnalyticsFilters {
  return {
    from: toAnalyticsIsoDateTime(form.from),
    heroId: form.heroId.trim() || undefined,
    limit: form.limit,
    profileId: form.profileId.trim() || undefined,
    teamId: form.teamId.trim() || undefined,
    to: toAnalyticsIsoDateTime(form.to),
    tournamentId: form.tournamentId.trim() || undefined
  };
}

function hasPublicFilters(form: AnalyticsFilterForm) {
  return Boolean(
    form.heroId.trim() ||
    form.profileId.trim() ||
    form.teamId.trim() ||
    form.tournamentId.trim() ||
    form.from.trim() ||
    form.to.trim() ||
    form.limit !== DEFAULT_PUBLIC_FILTERS.limit
  );
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

async function loadRoleAnalytics(profile: CurrentUserProfile | null, filters?: AnalyticsFilters): Promise<RoleAnalyticsState> {
  if (profile?.role === "player") {
    const [personal, team] = await Promise.all([
      getMyPlayerAnalytics(filters),
      getMyTeamAnalytics(filters)
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
      organizer: await getOrganizerAnalytics(filters)
    };
  }

  if (profile?.role === "admin") {
    return {
      kind: "admin",
      organizer: await getOrganizerAnalytics(filters)
    };
  }

  return {
    kind: "unsupported",
    role: profile?.role ?? null
  };
}

export function AnalyticsDashboard() {
  const [appliedPublicFilters, setAppliedPublicFilters] = useState<AnalyticsFilters>(
    filtersFromForm(DEFAULT_PUBLIC_FILTERS)
  );
  const [canRefreshAnalytics, setCanRefreshAnalytics] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filterDraft, setFilterDraft] = useState<AnalyticsFilterForm>(DEFAULT_PUBLIC_FILTERS);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSlowLoading, setIsSlowLoading] = useState(false);
  const [profile, setProfile] = useState<CurrentUserProfile | null>(null);
  const [publicAggregateError, setPublicAggregateError] = useState<string | null>(null);
  const [refreshResult, setRefreshResult] = useState<AnalyticsRefreshResult | null>(null);
  const [roleAnalytics, setRoleAnalytics] = useState<RoleAnalyticsState | null>(null);
  const [selectedTab, setSelectedTab] = useState("overview");
  const [snapshot, setSnapshot] = useState<AnalyticsSnapshot>(emptySnapshot);
  const [tournamentDrilldown, setTournamentDrilldown] =
    useState<OrganizerTournamentAnalyticsResponse | null>(null);

  const loadAnalytics = useCallback(async () => {
    const currentProfile = await getCurrentUserProfile();
    const nextRoleAnalytics = await loadRoleAnalytics(currentProfile, appliedPublicFilters);

    let nextSnapshot = emptySnapshot();
    let nextPublicAggregateError: string | null = null;

    try {
      nextSnapshot = await getPublicAnalyticsSnapshot(appliedPublicFilters);
    } catch (error) {
      nextPublicAggregateError = analyticsErrorMessage(error);
    }

    setCanRefreshAnalytics(currentProfile?.role === "admin");
    setError(null);
    setProfile(currentProfile);
    setPublicAggregateError(nextPublicAggregateError);
    setRoleAnalytics(nextRoleAnalytics);
    setSnapshot(nextSnapshot);
  }, [appliedPublicFilters]);

  useEffect(() => {
    let isMounted = true;

    async function load() {
      setIsLoading(true);
      setIsSlowLoading(false);
      const slowTimer = window.setTimeout(() => {
        if (isMounted) {
          setIsSlowLoading(true);
        }
      }, 900);

      try {
        await loadAnalytics();
      } catch (error) {
        if (isMounted) {
          setError(analyticsErrorMessage(error));
          setRoleAnalytics(null);
          setSnapshot(emptySnapshot());
        }
      } finally {
        window.clearTimeout(slowTimer);
        if (isMounted) {
          setIsLoading(false);
          setIsSlowLoading(false);
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
    } catch (error) {
      setError(analyticsErrorMessage(error));
    } finally {
      setIsRefreshing(false);
    }
  }

  function applyPublicFilters(nextFilters: AnalyticsFilterForm) {
    setAppliedPublicFilters(filtersFromForm(nextFilters));
  }

  function resetPublicFilters() {
    setFilterDraft(DEFAULT_PUBLIC_FILTERS);
    setAppliedPublicFilters(filtersFromForm(DEFAULT_PUBLIC_FILTERS));
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
  const tabs = tabsForRole(roleAnalytics);
  const activeTab = tabs.some((tab) => tab.key === selectedTab) ? selectedTab : "overview";

  if (isLoading) {
    return (
      <div className="analytics-terminal real-analytics-dashboard">
        <section className="analytics-terminal-header ops-panel">
          <div className="analytics-terminal-copy">
            <p className="ops-label">DotaOps Analytics Terminal</p>
            <h1>Analytics Terminal</h1>
            <p className="ops-mono">Loading official analytics.</p>
            {isSlowLoading ? <p className="analytics-slow-query">Still loading analytics...</p> : null}
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
            Authenticated analytics overview with aggregated tournament metrics retained as a
            secondary read-only signal.
          </p>
        </div>

        <div className="analytics-terminal-status">
          <div>
            <DatabaseZap size={18} />
            <span className="ops-label">Data source</span>
            <strong className="ops-data">OFFICIAL</strong>
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

      {roleAnalytics ? (
        <AnalyticsSectionTabs activeTab={activeTab} tabs={tabs} onChange={setSelectedTab} />
      ) : null}

      {error ? (
        <section className="analytics-state-panel ops-panel">
          <strong>Analytics unavailable.</strong>
          <p>{error}</p>
        </section>
      ) : null}

      {!error && roleAnalytics ? (
        <AnalyticsTabContent
          activeTab={activeTab}
          appliedFilters={appliedPublicFilters}
          canRefreshAnalytics={canRefreshAnalytics}
          currentProfile={profile}
          filterDraft={filterDraft}
          isRefreshing={isRefreshing}
          onApplyFilters={applyPublicFilters}
          onChangeFilters={setFilterDraft}
          onRefreshAnalytics={refreshAnalytics}
          onResetFilters={resetPublicFilters}
          onTournamentDrilldownChange={setTournamentDrilldown}
          publicAggregateError={publicAggregateError}
          publicSummary={publicSummary}
          refreshResult={refreshResult}
          roleAnalytics={roleAnalytics}
          snapshot={snapshot}
          tournamentDrilldown={tournamentDrilldown}
        />
      ) : null}
    </div>
  );
}

function AnalyticsSectionTabs({
  activeTab,
  onChange,
  tabs
}: Readonly<{
  activeTab: string;
  onChange: (tab: string) => void;
  tabs: AnalyticsTab[];
}>) {
  return (
    <nav aria-label="Analytics sections" className="analytics-section-tabs">
      {tabs.map((tab) => (
        <button
          aria-pressed={activeTab === tab.key}
          className={activeTab === tab.key ? "active" : ""}
          key={tab.key}
          onClick={() => onChange(tab.key)}
          type="button"
        >
          {tab.label}
        </button>
      ))}
    </nav>
  );
}

function AnalyticsTabContent({
  activeTab,
  appliedFilters,
  canRefreshAnalytics,
  currentProfile,
  filterDraft,
  isRefreshing,
  onApplyFilters,
  onChangeFilters,
  onRefreshAnalytics,
  onResetFilters,
  onTournamentDrilldownChange,
  publicAggregateError,
  publicSummary,
  refreshResult,
  roleAnalytics,
  snapshot,
  tournamentDrilldown
}: Readonly<{
  activeTab: string;
  appliedFilters: AnalyticsFilters;
  canRefreshAnalytics: boolean;
  currentProfile: CurrentUserProfile | null;
  filterDraft: AnalyticsFilterForm;
  isRefreshing: boolean;
  onApplyFilters: (filters: AnalyticsFilterForm) => void;
  onChangeFilters: (filters: AnalyticsFilterForm) => void;
  onRefreshAnalytics: () => Promise<void>;
  onResetFilters: () => void;
  onTournamentDrilldownChange: (analytics: OrganizerTournamentAnalyticsResponse | null) => void;
  publicAggregateError: string | null;
  publicSummary: {
    analyzedMatches: number;
    avgDuration: number;
    avgKda: number;
    playersTracked: number;
    teamsTracked: number;
    topHero: HeroAnalyticsMetric | null;
  };
  refreshResult: AnalyticsRefreshResult | null;
  roleAnalytics: RoleAnalyticsState;
  snapshot: AnalyticsSnapshot;
  tournamentDrilldown: OrganizerTournamentAnalyticsResponse | null;
}>) {
  if (activeTab === "public") {
    return (
      <PublicAggregatePanel
        publicAggregateError={publicAggregateError}
        publicSummary={publicSummary}
        snapshot={snapshot}
      />
    );
  }

  if (activeTab === "filters") {
    return (
      <AdvancedAnalyticsFilters
        draft={filterDraft}
        hasActiveFilters={hasPublicFilters(filterDraft)}
        onApply={onApplyFilters}
        onChange={onChangeFilters}
        onReset={onResetFilters}
        roleAnalytics={roleAnalytics}
        tournamentDrilldown={tournamentDrilldown}
      />
    );
  }

  if (roleAnalytics.kind === "player") {
    return (
      <PlayerRoleAnalyticsPanel
        activeTab={activeTab}
        appliedFilters={appliedFilters}
        currentProfile={currentProfile}
        currentProfileId={currentProfile?.profileId ?? ""}
        personal={roleAnalytics.personal}
        team={roleAnalytics.team}
      />
    );
  }

  if (roleAnalytics.kind === "organizer") {
    return (
      <OrganizerRoleAnalyticsPanel
        activeTab={activeTab}
        analytics={roleAnalytics.organizer}
        appliedFilters={appliedFilters}
        canRefreshAnalytics={canRefreshAnalytics}
        isRefreshing={isRefreshing}
        mode="organizer"
        onRefreshAnalytics={onRefreshAnalytics}
        onTournamentDrilldownChange={onTournamentDrilldownChange}
        refreshResult={refreshResult}
        tournamentDrilldown={tournamentDrilldown}
      />
    );
  }

  if (roleAnalytics.kind === "admin") {
    return (
      <OrganizerRoleAnalyticsPanel
        activeTab={activeTab}
        analytics={roleAnalytics.organizer}
        appliedFilters={appliedFilters}
        canRefreshAnalytics={canRefreshAnalytics}
        isRefreshing={isRefreshing}
        mode="admin"
        onRefreshAnalytics={onRefreshAnalytics}
        onTournamentDrilldownChange={onTournamentDrilldownChange}
        refreshResult={refreshResult}
        tournamentDrilldown={tournamentDrilldown}
      />
    );
  }

  return (
    <section className="analytics-state-panel ops-panel">
      <strong>Analytics workspace unavailable for this role.</strong>
      <p>Role-specific analytics are available for player, organizer, and admin accounts.</p>
    </section>
  );
}

function PlayerRoleAnalyticsPanel({
  activeTab,
  appliedFilters,
  currentProfile,
  currentProfileId,
  personal,
  team
}: Readonly<{
  activeTab: string;
  appliedFilters: AnalyticsFilters;
  currentProfile: CurrentUserProfile | null;
  currentProfileId: string;
  personal: PlayerAnalyticsResponse;
  team: CurrentTeamAnalyticsResponse;
}>) {
  const primaryMetric = personal.metrics[0] ?? null;
  const teamMetric = team.teamSummary[0] ?? null;

  if (activeTab === "overview") {
    return (
      <>
        <section className="analytics-terminal-panel analytics-data-panel ops-panel">
          <SectionHeader
            eyebrow="Player overview"
            title="Performance Snapshot"
            description="Primary personal and team analytics from protected role-based endpoints."
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
              icon={UsersRound}
              label="Team games"
              value={teamMetric ? integerOrNoData(teamMetric.gamesPlayed) : "No data"}
              delta={team.team ? team.team.name : "no active team"}
              tone="red"
            />
          </section>
          {!primaryMetric && !teamMetric ? (
            <AnalyticsEmptyBlock
              title="No player analytics yet."
              detail="Import OpenDota matches connected to your profile or team to populate this overview."
            />
          ) : null}
        </section>
      </>
    );
  }

  if (activeTab === "personal") {
    return (
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Personal analytics"
          title="Personal Performance"
          description="Protected analytics scoped to the current player profile."
        />
        <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
          <div className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Gameplay insights"
              title="Personal Insights"
              description="Rule-based signals from your recent match and hero analytics."
            />
            <PlayerInsightsList insights={personal.insights} />
          </div>
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
              eyebrow="Hero detail"
              title="Top Hero Breakdown"
              description="Per-hero averages and match references for your current player profile."
            />
            <PlayerHeroPerformanceTable heroes={personal.heroDetails} />
          </div>
          <div className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Personal match history"
              title="Analyzed Matches"
              description="Imported OpenDota matches connected to your player profile."
            />
            <MatchHistoryList emptyText="No analyzed personal matches yet." matches={personal.matchHistory} />
          </div>
          <div className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Progress trend"
              title="Recent Progress"
              description="Chronological match-by-match progress for your current player profile."
            />
            <PlayerProgressTable progress={personal.progress} />
          </div>
        </section>
      </section>
    );
  }

  if (activeTab === "team") {
    return (
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
                  title="Player Rows"
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
            detail="No current team analytics are available for this player account."
          />
        )}
      </section>
    );
  }

  if (activeTab === "compare") {
    return (
      <PlayerComparisonPanel
        appliedFilters={appliedFilters}
        currentProfile={currentProfile}
        currentProfileId={currentProfileId}
        fallbackPlayers={team.rosterPerformance}
      />
    );
  }

  return (
    <AnalyticsEmptyBlock
      title="Analytics section unavailable."
      detail="Select another analytics section."
    />
  );
}

function OrganizerRoleAnalyticsPanel({
  activeTab,
  analytics,
  appliedFilters,
  canRefreshAnalytics,
  isRefreshing,
  mode,
  onRefreshAnalytics,
  onTournamentDrilldownChange,
  refreshResult,
  tournamentDrilldown
}: Readonly<{
  activeTab: string;
  analytics: OrganizerAnalyticsResponse;
  appliedFilters: AnalyticsFilters;
  canRefreshAnalytics: boolean;
  isRefreshing: boolean;
  mode: "admin" | "organizer";
  onRefreshAnalytics: () => Promise<void>;
  onTournamentDrilldownChange: (analytics: OrganizerTournamentAnalyticsResponse | null) => void;
  refreshResult: AnalyticsRefreshResult | null;
  tournamentDrilldown: OrganizerTournamentAnalyticsResponse | null;
}>) {
  if (activeTab === "drilldown") {
    return (
      <OrganizerTournamentDrilldown
        analytics={tournamentDrilldown}
        appliedFilters={appliedFilters}
        onAnalyticsChange={onTournamentDrilldownChange}
      />
    );
  }

  if (activeTab === "compare") {
    return tournamentDrilldown ? (
      <TeamComparisonPanel appliedFilters={appliedFilters} analytics={tournamentDrilldown} />
    ) : (
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Team comparison"
          title="Team vs Team"
          description="Load a tournament drilldown first, then compare analyzed teams from that response."
        />
        <AnalyticsEmptyBlock
          title="No tournament drilldown selected."
          detail="Open the Tournament Drilldown tab and load a managed tournament before comparing teams."
        />
      </section>
    );
  }

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
          delta="registration queue"
          tone="gold"
        />
        <TelemetryCard
          icon={ShieldCheck}
          label="Approved registrations"
          value={countMetricValue(analytics.approvedRegistrations)}
          delta="accepted teams"
          tone="green"
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
          delta="imported match data"
          tone="red"
        />
        <TelemetryCard
          icon={RefreshCw}
          label="Import jobs"
          value={countMetricValue(analytics.importJobs)}
          delta="analytics pipeline"
          tone="cyan"
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
      <div className="analytics-overview-note">
        <strong>Tournament-level analytics are available in the Tournament Drilldown tab.</strong>
        <p>Team comparison uses the selected tournament analytics response and stays scoped to available official data.</p>
      </div>
      {canRefreshAnalytics ? (
        <section className="analytics-admin-panel ops-panel">
          <SectionHeader
            eyebrow="Admin operation"
            title="Analytics Refresh"
            description="Refreshes official analytics aggregates. This action is only visible to admin accounts."
            action={
              <button className="button ops-button-primary" disabled={isRefreshing} onClick={() => void onRefreshAnalytics()} type="button">
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
    </section>
  );
}

function AdvancedAnalyticsFilters({
  draft,
  hasActiveFilters,
  onApply,
  onChange,
  onReset,
  roleAnalytics,
  tournamentDrilldown
}: Readonly<{
  draft: AnalyticsFilterForm;
  hasActiveFilters: boolean;
  onApply: (filters: AnalyticsFilterForm) => void;
  onChange: (filters: AnalyticsFilterForm) => void;
  onReset: () => void;
  roleAnalytics: RoleAnalyticsState;
  tournamentDrilldown: OrganizerTournamentAnalyticsResponse | null;
}>) {
  const [heroLookups, setHeroLookups] = useState<HeroLookup[]>([]);
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [playerLookups, setPlayerLookups] = useState<TeamPlayerLookup[]>([]);
  const [teamLookups, setTeamLookups] = useState<TeamLookup[]>([]);
  const [tournamentLookups, setTournamentLookups] = useState<OrganizerTournamentLookup[]>([]);

  function updateField(field: keyof AnalyticsFilterForm, value: string) {
    onChange({
      ...draft,
      [field]: field === "limit" ? Number(value) : value
    });
  }

  useEffect(() => {
    let cancelled = false;

    async function loadLookups() {
      setIsLoadingLookups(true);
      setLookupError(null);

      try {
        const [heroes, tournaments, teams] = await Promise.all([
          getHeroLookups(100),
          roleAnalytics.kind === "organizer" || roleAnalytics.kind === "admin"
            ? getOrganizerTournamentLookups(100)
            : Promise.resolve([]),
          roleAnalytics.kind === "player" ? getMyTeamLookups(100) : Promise.resolve([])
        ]);

        if (!cancelled) {
          setHeroLookups(heroes);
          setTournamentLookups(tournaments);
          setTeamLookups(teams);
        }
      } catch (error) {
        if (!cancelled) {
          setLookupError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsLoadingLookups(false);
        }
      }
    }

    void loadLookups();

    return () => {
      cancelled = true;
    };
  }, [roleAnalytics.kind]);

  useEffect(() => {
    let cancelled = false;

    async function loadPlayers() {
      if (roleAnalytics.kind !== "player" || !draft.teamId.trim()) {
        setPlayerLookups([]);
        return;
      }

      try {
        const players = await getTeamPlayerLookups(draft.teamId.trim(), 100);

        if (!cancelled) {
          setPlayerLookups(players);
        }
      } catch (error) {
        if (!cancelled) {
          setPlayerLookups([]);
          setLookupError(analyticsErrorMessage(error));
        }
      }
    }

    void loadPlayers();

    return () => {
      cancelled = true;
    };
  }, [draft.teamId, roleAnalytics.kind]);

  const organizerTeamOptions = useMemo(() => {
    const rows = [
      ...(tournamentDrilldown?.teamComparison ?? []),
      ...(tournamentDrilldown?.topTeams ?? [])
    ];
    const unique = new Map<string, TeamAnalyticsMetric>();

    for (const team of rows) {
      unique.set(team.teamId, team);
    }

    return [...unique.values()];
  }, [tournamentDrilldown]);

  const showOrganizerTournamentLookup = roleAnalytics.kind === "organizer" || roleAnalytics.kind === "admin";
  const showPlayerTeamLookup = roleAnalytics.kind === "player";

  return (
    <section className="analytics-terminal-panel analytics-data-panel analytics-filter-panel ops-panel">
      <SectionHeader
        eyebrow="Advanced analytics filters"
        title="Analytics Filters"
        description="Filter public metrics and role-based analytics by tournament, team, player, hero, and time range."
      />
      {lookupError ? <AnalyticsEmptyBlock title="Lookup data unavailable." detail={lookupError} /> : null}
      {isLoadingLookups ? <p className="analytics-slow-query">Loading filter options...</p> : null}
      <form
        className="analytics-filter-grid"
        onSubmit={(event) => {
          event.preventDefault();
          onApply(draft);
        }}
      >
        <label>
          <span>Tournament</span>
          {showOrganizerTournamentLookup ? (
            <select value={draft.tournamentId} onChange={(event) => updateField("tournamentId", event.target.value)}>
              <option value="">All manageable tournaments</option>
              {tournamentLookups.map((tournament) => (
                <option key={tournament.tournamentId} value={tournament.tournamentId}>
                  {tournament.title} ({tournament.status})
                </option>
              ))}
            </select>
          ) : (
            <input
              autoComplete="off"
              placeholder="Advanced tournament filter"
              type="text"
              value={draft.tournamentId}
              onChange={(event) => updateField("tournamentId", event.target.value)}
            />
          )}
        </label>
        <label>
          <span>Team</span>
          {showPlayerTeamLookup ? (
            <select value={draft.teamId} onChange={(event) => updateField("teamId", event.target.value)}>
              <option value="">Current player scope</option>
              {teamLookups.map((team) => (
                <option key={team.teamId} value={team.teamId}>
                  {team.name}{team.tag ? ` (${team.tag})` : ""}
                </option>
              ))}
            </select>
          ) : organizerTeamOptions.length > 0 ? (
            <select value={draft.teamId} onChange={(event) => updateField("teamId", event.target.value)}>
              <option value="">All teams in selected drilldown</option>
              {organizerTeamOptions.map((team) => (
                <option key={team.teamId} value={team.teamId}>
                  {team.teamName}
                </option>
              ))}
            </select>
          ) : (
            <input
              autoComplete="off"
              placeholder="Advanced team filter"
              type="text"
              value={draft.teamId}
              onChange={(event) => updateField("teamId", event.target.value)}
            />
          )}
        </label>
        <label>
          <span>Player</span>
          {showPlayerTeamLookup && draft.teamId.trim() ? (
            <select value={draft.profileId} onChange={(event) => updateField("profileId", event.target.value)}>
              <option value="">All team players</option>
              {playerLookups.map((player) => (
                <option key={player.profileId} value={player.profileId}>
                  {player.displayName || player.nickname}
                </option>
              ))}
            </select>
          ) : (
            <input
              autoComplete="off"
              placeholder="Optional profile UUID"
              type="text"
              value={draft.profileId}
              onChange={(event) => updateField("profileId", event.target.value)}
            />
          )}
        </label>
        <label>
          <span>Hero</span>
          <select value={draft.heroId} onChange={(event) => updateField("heroId", event.target.value)}>
            <option value="">All heroes</option>
            {heroLookups.map((hero) => (
              <option key={hero.heroId} value={hero.heroId}>
                {hero.localizedName}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Limit</span>
          <select value={draft.limit} onChange={(event) => updateField("limit", event.target.value)}>
            {ANALYTICS_LIMIT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <fieldset className="analytics-disabled-filters">
          <legend>Time range</legend>
          <input
            aria-label="From date"
            type="datetime-local"
            value={draft.from}
            onChange={(event) => updateField("from", event.target.value)}
          />
          <input
            aria-label="To date"
            type="datetime-local"
            value={draft.to}
            onChange={(event) => updateField("to", event.target.value)}
          />
          <span>Optional time range filter</span>
        </fieldset>
        <div className="analytics-filter-actions">
          <button className="button ops-button-primary" type="submit">
            <Filter size={16} />
            Apply filters
          </button>
          <button className="button ops-button-ghost" onClick={onReset} type="button">
            Reset filters
          </button>
          <span className="ops-mono">
            {hasActiveFilters ? "Filtered analytics workspace" : "Default analytics workspace"}
          </span>
        </div>
      </form>
    </section>
  );
}

function OrganizerTournamentDrilldown({
  analytics,
  appliedFilters,
  onAnalyticsChange
}: Readonly<{
  analytics: OrganizerTournamentAnalyticsResponse | null;
  appliedFilters: AnalyticsFilters;
  onAnalyticsChange: (analytics: OrganizerTournamentAnalyticsResponse | null) => void;
}>) {
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [isSlowLoading, setIsSlowLoading] = useState(false);
  const [tournamentId, setTournamentId] = useState("");
  const [tournamentLookups, setTournamentLookups] = useState<OrganizerTournamentLookup[]>([]);

  useEffect(() => {
    let cancelled = false;

    async function loadTournamentLookups() {
      setIsLoadingLookups(true);
      setError(null);

      try {
        const lookups = await getOrganizerTournamentLookups(100);

        if (!cancelled) {
          setTournamentLookups(lookups);
          setTournamentId((current) => current || lookups[0]?.tournamentId || "");
        }
      } catch (error) {
        if (!cancelled) {
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsLoadingLookups(false);
        }
      }
    }

    void loadTournamentLookups();

    return () => {
      cancelled = true;
    };
  }, []);

  async function loadTournamentAnalytics() {
    const cleanTournamentId = tournamentId.trim();

    if (!cleanTournamentId) {
      setError("Select a tournament to load organizer tournament analytics.");
      onAnalyticsChange(null);
      return;
    }

    setError(null);
    setIsLoading(true);
    setIsSlowLoading(false);
    const slowTimer = window.setTimeout(() => setIsSlowLoading(true), 900);

    try {
      const response = await getOrganizerTournamentAnalytics(cleanTournamentId, appliedFilters);
      onAnalyticsChange(response);
    } catch (error) {
      onAnalyticsChange(null);
      setError(analyticsErrorMessage(error));
    } finally {
      window.clearTimeout(slowTimer);
      setIsLoading(false);
      setIsSlowLoading(false);
    }
  }

  return (
    <section className="analytics-terminal-panel analytics-data-panel analytics-drilldown-panel ops-panel">
      <SectionHeader
        eyebrow="Tournament analytics drilldown"
        title="Organizer Tournament Scope"
        description="Load analytics for one managed tournament. This uses the existing organizer tournament analytics endpoint."
      />
      <form
        className="analytics-drilldown-form"
        onSubmit={(event) => {
          event.preventDefault();
          void loadTournamentAnalytics();
        }}
      >
        <label>
          <span>Tournament</span>
          <select
            disabled={isLoadingLookups || tournamentLookups.length === 0}
            value={tournamentId}
            onChange={(event) => setTournamentId(event.target.value)}
          >
            <option value="">
              {isLoadingLookups ? "Loading tournaments..." : "Select tournament"}
            </option>
            {tournamentLookups.map((tournament) => (
              <option key={tournament.tournamentId} value={tournament.tournamentId}>
                {tournament.title} ({tournament.status})
              </option>
            ))}
          </select>
        </label>
        <button className="button ops-button-primary" disabled={isLoading} type="submit">
          {isLoading ? "Loading..." : "Apply drilldown"}
        </button>
        <p className="ops-mono">Uses your manageable tournament lookup.</p>
      </form>
      {!isLoadingLookups && tournamentLookups.length === 0 && !error ? (
        <AnalyticsEmptyBlock
          title="No manageable tournaments found."
          detail="Tournament analytics drilldown becomes available after you create or manage a tournament."
        />
      ) : null}
      {isSlowLoading ? <p className="analytics-slow-query">Still loading analytics...</p> : null}
      {error ? <AnalyticsEmptyBlock title="Tournament analytics unavailable." detail={error} /> : null}
      {analytics ? <OrganizerTournamentAnalyticsView analytics={analytics} appliedFilters={appliedFilters} /> : null}
    </section>
  );
}

function OrganizerTournamentAnalyticsView({
  analytics,
  appliedFilters
}: Readonly<{
  analytics: OrganizerTournamentAnalyticsResponse;
  appliedFilters: AnalyticsFilters;
}>) {
  return (
    <div className="analytics-drilldown-results">
      <section className="analytics-telemetry-grid">
        <TelemetryCard
          icon={DatabaseZap}
          label="Games processed"
          value={countMetricValue(analytics.gamesProcessed)}
          delta="organizer tournament"
          tone="cyan"
        />
        <TelemetryCard
          icon={Activity}
          label="Missing imports"
          value={countMetricValue(analytics.matchesWithoutImport)}
          delta="matches without import"
          tone="gold"
        />
        <TelemetryCard
          icon={ShieldCheck}
          label="Import coverage"
          value={formatPercent(analytics.importCoveragePercent)}
          delta="processed coverage"
          tone="green"
        />
        <TelemetryCard
          icon={Clock3}
          label="Avg duration"
          value={secondsToDuration(analytics.avgDurationSeconds)}
          delta="analyzed games"
          tone="red"
        />
      </section>

      {analytics.tournamentSummary ? (
        <TournamentMatrix tournaments={[analytics.tournamentSummary]} />
      ) : (
        <AnalyticsEmptyBlock
          title="Tournament summary unavailable."
          detail="No tournament summary is available for this drilldown."
        />
      )}

      <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
        <div className="analytics-terminal-panel analytics-data-panel ops-panel">
          <SectionHeader
            eyebrow="Top teams"
            title="Analyzed Teams"
            description="Team rows returned by the organizer tournament analytics endpoint."
          />
          <TeamMatrix teams={analytics.topTeams} />
        </div>
        <div className="analytics-terminal-panel analytics-data-panel ops-panel">
          <SectionHeader
            eyebrow="Hero metrics"
            title="Tournament Hero Pool"
            description="Hero performance rows scoped to this tournament."
          />
          <HeroMatrix heroes={analytics.heroMetrics} />
        </div>
      </section>

      <TeamComparisonPanel analytics={analytics} appliedFilters={appliedFilters} />

      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Recent imports"
          title="Import Job Signal"
          description="Recent match import jobs linked to this tournament."
        />
        <RecentImportsList imports={analytics.recentImports} />
      </section>
    </div>
  );
}

function PublicAggregatePanel({
  publicAggregateError,
  publicSummary,
  snapshot
}: Readonly<{
  publicAggregateError: string | null;
  publicSummary: {
    analyzedMatches: number;
    avgDuration: number;
    avgKda: number;
    topHero: HeroAnalyticsMetric | null;
  };
  snapshot: AnalyticsSnapshot;
}>) {
  return (
    <>
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Public analytics aggregate"
          title="Aggregated Tournament Metrics"
          description="These panels use public tournament aggregates and the same applied filters as the analytics workspace."
        />
        {publicAggregateError ? (
          <AnalyticsEmptyBlock title="Public aggregate unavailable." detail={publicAggregateError} />
        ) : null}
        {!publicAggregateError && allEmpty(snapshot) ? (
          <AnalyticsEmptyBlock
            title="No imported match analytics yet."
            detail="Import OpenDota matches first. Analytics will appear after processing."
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
              delta="aggregated metric"
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
              description="Win rate, KDA, and damage metrics from official hero analytics."
            />
            <HeroMatrix heroes={snapshot.heroes} />
          </section>

          <section className="analytics-terminal-grid analytics-terminal-grid-secondary">
            <div className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Team aggregate"
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
              description="Official tournament summaries and most picked heroes."
            />
            <TournamentMatrix tournaments={snapshot.tournaments} />
          </section>
        </>
      ) : null}
    </>
  );
}

function TeamComparisonPanel({
  analytics,
  appliedFilters
}: Readonly<{
  analytics: OrganizerTournamentAnalyticsResponse;
  appliedFilters: AnalyticsFilters;
}>) {
  const [comparison, setComparison] = useState<TeamComparisonResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isComparing, setIsComparing] = useState(false);
  const [leftId, setLeftId] = useState("");
  const [rightId, setRightId] = useState("");
  const teams = useMemo(() => {
    const unique = new Map<string, TeamAnalyticsMetric>();

    for (const team of [...analytics.teamComparison, ...analytics.topTeams]) {
      unique.set(team.teamId, team);
    }

    return [...unique.values()];
  }, [analytics.teamComparison, analytics.topTeams]);

  const effectiveLeftId = leftId || teams[0]?.teamId || "";
  const effectiveRightId =
    (rightId && rightId !== effectiveLeftId ? rightId : "") ||
    teams.find((team) => team.teamId !== effectiveLeftId)?.teamId ||
    "";

  useEffect(() => {
    let cancelled = false;

    async function runComparison() {
      if (!effectiveLeftId || !effectiveRightId || effectiveLeftId === effectiveRightId) {
        setComparison(null);
        return;
      }

      setIsComparing(true);
      setError(null);

      try {
        const response = await compareAnalyticsTeams({
          filters: {
            from: appliedFilters.from,
            heroId: appliedFilters.heroId,
            limit: appliedFilters.limit,
            to: appliedFilters.to,
            tournamentId: analytics.tournamentId
          },
          teamAId: effectiveLeftId,
          teamBId: effectiveRightId
        });

        if (!cancelled) {
          setComparison(response);
        }
      } catch (error) {
        if (!cancelled) {
          setComparison(null);
          setError(analyticsErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setIsComparing(false);
        }
      }
    }

    void runComparison();

    return () => {
      cancelled = true;
    };
  }, [analytics.tournamentId, appliedFilters.from, appliedFilters.heroId, appliedFilters.limit, appliedFilters.to, effectiveLeftId, effectiveRightId]);

  if (teams.length < 2) {
    return (
      <section className="analytics-terminal-panel analytics-data-panel ops-panel">
        <SectionHeader
          eyebrow="Team comparison"
          title="Team vs Team"
          description="Compare two teams from the selected tournament."
        />
        <AnalyticsEmptyBlock
          title="Team comparison requires at least two analyzed teams."
          detail="The selected tournament analytics response returned fewer than two team rows."
        />
      </section>
    );
  }

  const leftTeam = comparison?.teamA ?? null;
  const rightTeam = comparison?.teamB ?? null;

  return (
    <section className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Team comparison"
        title="Team vs Team"
        description="Compare two teams from the selected tournament."
      />
      <div className="analytics-comparison-controls">
        <label>
          <span>Team A</span>
          <select value={effectiveLeftId} onChange={(event) => setLeftId(event.target.value)}>
            {teams.map((team) => (
              <option key={`left-${team.teamId}`} value={team.teamId}>
                {team.teamName}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>Team B</span>
          <select value={effectiveRightId} onChange={(event) => setRightId(event.target.value)}>
            {teams
              .filter((team) => team.teamId !== effectiveLeftId)
              .map((team) => (
                <option key={`right-${team.teamId}`} value={team.teamId}>
                  {team.teamName}
                </option>
              ))}
          </select>
        </label>
      </div>
      {isComparing ? <p className="analytics-slow-query">Comparing teams...</p> : null}
      {error ? <AnalyticsEmptyBlock title="Team comparison unavailable." detail={error} /> : null}
      {!error && comparison && leftTeam && rightTeam ? (
        <>
          <span className="ops-badge">Access scope: {comparison.filters.accessScope}</span>
          <ComparisonCards
            left={{
              metrics: teamComparisonMetrics(leftTeam),
              name: leftTeam.teamName,
              subtitle: leftTeam.tournamentName ?? "Selected tournament"
            }}
            right={{
              metrics: teamComparisonMetrics(rightTeam),
              name: rightTeam.teamName,
              subtitle: rightTeam.tournamentName ?? "Selected tournament"
            }}
          />
          {comparison.heroMetrics.length > 0 ? (
            <section className="analytics-terminal-panel analytics-data-panel ops-panel">
              <SectionHeader
                eyebrow="Hero metrics"
                title="Compared Team Hero Pool"
                description="Hero performance returned for the selected team comparison."
              />
              <HeroMatrix heroes={comparison.heroMetrics} />
            </section>
          ) : null}
          <section className="analytics-terminal-panel analytics-data-panel ops-panel">
            <SectionHeader
              eyebrow="Recent matches"
              title="Team Match History"
              description="Recent matches returned by the team comparison endpoint."
            />
            <MatchHistoryList emptyText="No recent team matches returned." matches={comparison.recentMatches} />
          </section>
        </>
      ) : null}
    </section>
  );
}

function teamComparisonMetrics(team: TeamAnalyticsMetric) {
  return [
    { label: "Games", value: countMetricValue(team.gamesPlayed) },
    { label: "W-L", value: `${team.wins}-${team.losses}` },
    { label: "Win rate", value: formatPercent(team.winRate) },
    { label: "KDA", value: safeMetricNumber(team.avgKda, 2) },
    {
      label: "K/D/A",
      value: `${safeMetricNumber(team.avgKills)} / ${safeMetricNumber(team.avgDeaths)} / ${safeMetricNumber(team.avgAssists)}`
    },
    { label: "GPM/XPM", value: `${safeMetricNumber(team.avgGpm)} / ${safeMetricNumber(team.avgXpm)}` }
  ];
}

function RecentImportsList({ imports }: Readonly<{ imports: RecentImportMetric[] }>) {
  if (imports.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No recent imports."
        detail="Recent import jobs will appear once this tournament has match import activity."
      />
    );
  }

  return (
    <div className="analytics-real-stack">
      {imports.slice(0, 8).map((item) => (
        <article className="analytics-rank-row" key={item.id}>
          <span className="ops-mono">{item.status.slice(0, 2).toUpperCase()}</span>
          <div>
            <strong>{item.dotaMatchId ?? "No Dota match ID"}</strong>
            <p>{item.createdAt ?? "Created time unavailable"}</p>
          </div>
          <div>
            <span>{item.status}</span>
            <em>{item.errorCode ?? "No error"}</em>
          </div>
        </article>
      ))}
    </div>
  );
}

function PlayerInsightsList({ insights }: Readonly<{ insights: PlayerInsight[] }>) {
  if (insights.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="Not enough match data yet."
        detail="Insights appear after enough recent and hero-specific match data exists."
      />
    );
  }

  return (
    <div className="analytics-real-stack">
      {insights.slice(0, 5).map((insight) => (
        <article className="analytics-rank-row" key={`${insight.category}-${insight.metricName}-${insight.title}`}>
          <span className="ops-mono">{insight.category.slice(0, 3)}</span>
          <div>
            <strong>{insight.title}</strong>
            <p>{insight.description}</p>
          </div>
          <div>
            <span>{insightMetricLabel(insight)}</span>
            <em>{insight.evidence}</em>
          </div>
        </article>
      ))}
    </div>
  );
}

function PlayerProgressTable({ progress }: Readonly<{ progress: PlayerProgressPoint[] }>) {
  if (progress.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No progress data yet."
        detail="Progress rows will appear after imported matches link to your player profile."
      />
    );
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Played</th>
            <th>Hero</th>
            <th>Result</th>
            <th>KDA</th>
            <th>Economy</th>
            <th>Impact</th>
          </tr>
        </thead>
        <tbody>
          {progress.map((point, index) => (
            <tr key={`${point.matchId ?? "match"}-${point.matchGameId ?? index}`}>
              <td>
                <strong>{formatAnalyticsDateTime(point.playedAt)}</strong>
                <span>{point.dotaMatchId ? `Dota ${point.dotaMatchId}` : point.matchId ?? "Match ID unavailable"}</span>
              </td>
              <td>
                <strong>{point.heroName ?? "Hero unavailable"}</strong>
                <span>{point.heroId ?? "Hero ID unavailable"}</span>
              </td>
              <td>
                <strong>{progressResultLabel(point)}</strong>
                <span>{point.kills}-{point.deaths}-{point.assists}</span>
              </td>
              <td>{point.kda.toFixed(2)}</td>
              <td>
                <strong>{countMetricValue(point.goldPerMin)} / {countMetricValue(point.xpPerMin)}</strong>
                <span>LH/DN {countMetricValue(point.lastHits)} / {countMetricValue(point.denies)}</span>
              </td>
              <td>
                <strong>{countMetricValue(point.heroDamage)} hero / {countMetricValue(point.towerDamage)} tower</strong>
                <span>Healing {countMetricValue(point.heroHealing)}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TeamMatrix({ teams }: Readonly<{ teams: TeamAnalyticsMetric[] }>) {
  if (teams.length === 0) {
    return <AnalyticsEmptyBlock title="No team metrics available." detail="Team comparison stays empty until imported team match data is processed." />;
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

function PlayerMatrix({ players }: Readonly<{ players: PlayerAnalyticsMetric[] }>) {
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

function TournamentMatrix({ tournaments }: Readonly<{ tournaments: TournamentAnalyticsMetric[] }>) {
  if (tournaments.length === 0) {
    return <AnalyticsEmptyBlock title="No tournament metrics available." detail="Tournament aggregates require processed match analytics." />;
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
                <span>Official metric</span>
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

