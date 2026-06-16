import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  formatAnalyticsDateTime,
  matchTeamsLabel,
  matchWinnerLabel,
  playerHeroReferenceLabel,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import {
  type AnalyticsMatchHistory,
  type HeroAnalyticsMetric,
  type PlayerHeroPerformance
} from "@/lib/analytics-data";
import { classNames, formatPercent } from "@/lib/utils";

type HeroSelectHandler = (heroId: string, heroName: string | null) => void;

export function MatchHistoryList({
  emptyText,
  matches
}: Readonly<{
  emptyText: string;
  matches: AnalyticsMatchHistory[];
}>) {
  if (matches.length === 0) {
    return <AnalyticsEmptyBlock title={emptyText} detail="Match history rows will appear after imported match records are processed." />;
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Dota Match ID</th>
            <th>Tournament</th>
            <th>Teams</th>
            <th>Game</th>
          </tr>
        </thead>
        <tbody>
          {matches.map((match, index) => (
            <tr key={`${match.matchId ?? "match"}-${match.matchGameId ?? index}`}>
              <td>
                <strong>{match.dotaMatchId ?? "No data"}</strong>
                <span>Official match history</span>
              </td>
              <td>
                <strong>{match.tournamentName ?? "Tournament unavailable"}</strong>
                <span>{formatAnalyticsDateTime(match.playedAt)}</span>
              </td>
              <td>
                <strong>{matchTeamsLabel(match)}</strong>
                <span>{matchWinnerLabel(match)}</span>
              </td>
              <td>
                <strong>{match.matchGameId ?? "No game ID"}</strong>
                <span>{match.matchId ?? "No match ID"}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function PlayerHeroPerformanceTable({
  heroes,
  onSelectHero,
  selectedHeroId
}: Readonly<{
  heroes: PlayerHeroPerformance[];
  onSelectHero?: HeroSelectHandler;
  selectedHeroId?: string | null;
}>) {
  if (heroes.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No hero-specific analytics yet."
        detail="Hero breakdown rows will appear after imported matches link heroes to your player profile."
      />
    );
  }

  return (
    <div className="analytics-real-table-wrap">
      <table className="analytics-real-table">
        <thead>
          <tr>
            <th>Hero</th>
            <th>Matches</th>
            <th>W-L</th>
            <th>Win Rate</th>
            <th>Avg KDA</th>
            <th>Economy</th>
            <th>Impact</th>
            <th>Recent / Best</th>
          </tr>
        </thead>
        <tbody>
          {heroes.slice(0, 12).map((hero) => {
            const isSelected = Boolean(hero.heroId && hero.heroId === selectedHeroId);

            return (
              <tr
                className={classNames(
                  onSelectHero && hero.heroId && "analytics-hero-selectable-row",
                  isSelected && "is-selected"
                )}
                key={`${hero.heroId ?? hero.heroName ?? "hero"}-${hero.recentMatchId ?? "recent"}`}
                onClick={onSelectHero && hero.heroId ? () => onSelectHero(hero.heroId as string, hero.heroName) : undefined}
              >
                <td>
                  <HeroSelectButton
                    heroId={hero.heroId}
                    heroName={hero.heroName}
                    isSelected={isSelected}
                    onSelectHero={onSelectHero}
                    secondaryLabel={hero.heroId ?? "Hero ID unavailable"}
                  />
                </td>
                <td>{hero.matches}</td>
                <td>{hero.wins}-{hero.losses}</td>
                <td>{formatPercent(hero.winRate)}</td>
                <td>
                  <strong>{safeMetricNumber(hero.avgKda, 2)}</strong>
                  <span>{safeMetricNumber(hero.avgKills)} / {safeMetricNumber(hero.avgDeaths)} / {safeMetricNumber(hero.avgAssists)}</span>
                </td>
                <td>
                  <strong>{safeMetricNumber(hero.avgGpm)} / {safeMetricNumber(hero.avgXpm)}</strong>
                  <span>LH/DN {safeMetricNumber(hero.avgLastHits)} / {safeMetricNumber(hero.avgDenies)}</span>
                </td>
                <td>
                  <strong>{Math.round(hero.avgHeroDamage).toLocaleString("en-US")} hero / {Math.round(hero.avgTowerDamage).toLocaleString("en-US")} tower</strong>
                  <span>Healing {Math.round(hero.avgHeroHealing).toLocaleString("en-US")}</span>
                </td>
                <td>
                  <strong>{formatAnalyticsDateTime(hero.recentPlayedAt)}</strong>
                  <span>Best KDA {safeMetricNumber(hero.bestKda, 2)} / {hero.bestDotaMatchId ?? playerHeroReferenceLabel(hero)}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function HeroMatrix({
  heroes,
  onSelectHero,
  selectedHeroId
}: Readonly<{
  heroes: HeroAnalyticsMetric[];
  onSelectHero?: HeroSelectHandler;
  selectedHeroId?: string | null;
}>) {
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
          {heroes.slice(0, 12).map((hero) => {
            const isSelected = hero.heroId === selectedHeroId;

            return (
              <tr
                className={classNames(
                  onSelectHero && "analytics-hero-selectable-row",
                  isSelected && "is-selected"
                )}
                key={`${hero.heroId}-${hero.tournamentId ?? "global"}`}
                onClick={onSelectHero ? () => onSelectHero(hero.heroId, hero.localizedName) : undefined}
              >
                <td>
                  <HeroSelectButton
                    heroId={hero.heroId}
                    heroName={hero.localizedName}
                    isSelected={isSelected}
                    onSelectHero={onSelectHero}
                    secondaryLabel={hero.tournamentName ?? "Tournament aggregate"}
                  />
                </td>
                <td>{hero.gamesPlayed}</td>
                <td>{hero.wins}-{hero.losses}</td>
                <td>{formatPercent(hero.winRate)}</td>
                <td>{hero.kda.toFixed(2)}</td>
                <td>{Math.round(hero.avgHeroDamage).toLocaleString("en-US")}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function HeroSelectButton({
  heroId,
  heroName,
  isSelected,
  onSelectHero,
  secondaryLabel
}: Readonly<{
  heroId: string | null;
  heroName: string | null;
  isSelected: boolean;
  onSelectHero?: HeroSelectHandler;
  secondaryLabel: string;
}>) {
  const title = heroName ?? "Hero unavailable";

  if (!heroId || !onSelectHero) {
    return (
      <>
        <strong>{title}</strong>
        <span>{secondaryLabel}</span>
      </>
    );
  }

  return (
    <button
      aria-pressed={isSelected}
      className="analytics-hero-select-button"
      onClick={(event) => {
        event.stopPropagation();
        onSelectHero(heroId, heroName);
      }}
      type="button"
    >
      <strong>{title}</strong>
      <span>{isSelected ? "Mastery selected" : secondaryLabel}</span>
    </button>
  );
}
