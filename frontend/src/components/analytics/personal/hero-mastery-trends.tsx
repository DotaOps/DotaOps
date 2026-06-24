import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type {
  HeroMasteryRecentMatch,
  HeroMasteryResponse
} from "@/lib/analytics-data";
import { classNames, formatPercent } from "@/lib/utils";

type TrendPoint = {
  label: string;
  outcome: "win" | "loss" | "unknown";
  value: number | null;
};

type TrendSeries = {
  description: string;
  emptyText: string;
  label: string;
  points: TrendPoint[];
  valueSuffix?: string;
};

type RecentFormSummary = {
  avgGoldPerMin: number | null;
  avgKda: number | null;
  avgXpPerMin: number | null;
  losses: number;
  roughContextCount: number;
  sampleSize: number;
  stompContextCount: number;
  winRate: number | null;
  wins: number;
};

export function HeroMasteryTrends({ mastery }: Readonly<{ mastery: HeroMasteryResponse }>) {
  const chronologicalMatches = [...mastery.recentMatches].reverse();
  const recentForm = recentFormSummary(mastery.recentMatches);
  const trendSeries = buildTrendSeries(chronologicalMatches);
  const hasTrendData = trendSeries.some((series) => series.points.some((point) => point.value !== null));
  const hasRecentTrend = mastery.recentTrend.sampleSize > 0 || mastery.recentTrend.metrics.length > 0;

  if (chronologicalMatches.length === 0 && !hasRecentTrend) {
    return (
      <AnalyticsEmptyBlock
        title="No recent form sample yet."
        detail="Recent trend cards appear after this hero has imported matches in the current analytics scope."
      />
    );
  }

  return (
    <section className="analytics-mastery-card analytics-mastery-trends-card">
      <div className="analytics-mastery-card-header">
        <div>
          <span className="ops-label">Recent form</span>
          <strong>Recent Performance Trends</strong>
          <p>Calculated only from recent matches returned for this hero, not from the full baseline.</p>
        </div>
        <span className="ops-badge">{recentForm.sampleSize} recent rows</span>
      </div>

      <div className="analytics-mastery-recent-form-grid">
        <RecentFormStat label="Recent games" value={countMetricValue(recentForm.sampleSize)} detail="sample only" />
        <RecentFormStat
          label="Recent W-L"
          value={`${recentForm.wins}-${recentForm.losses}`}
          detail={recentForm.winRate === null ? "win rate unavailable" : `${formatPercent(recentForm.winRate)} win rate`}
        />
        <RecentFormStat
          label="Recent avg KDA"
          value={safeMetricNumber(recentForm.avgKda, 2)}
          detail="from returned recent matches"
        />
        <RecentFormStat
          label="Recent GPM / XPM"
          value={`${safeMetricNumber(recentForm.avgGoldPerMin)} / ${safeMetricNumber(recentForm.avgXpPerMin)}`}
          detail="from returned recent matches"
        />
        <RecentFormStat
          label="Rough / stomp context"
          value={`${recentForm.roughContextCount} / ${recentForm.stompContextCount}`}
          detail="rough or stomp classifications"
        />
      </div>

      {hasRecentTrend ? (
        <div className="analytics-mastery-trend-note">
          <span className="ops-label">Backend trend window</span>
          <p>{mastery.recentTrend.interpretation}</p>
        </div>
      ) : null}

      {hasTrendData ? (
        <div className="analytics-mastery-mini-trend-grid">
          {trendSeries.map((series) => (
            <MiniTrendCard key={series.label} series={series} />
          ))}
          <OutcomeStrip matches={chronologicalMatches} />
        </div>
      ) : (
        <AnalyticsEmptyBlock
          title="Not enough recent rows for mini trends."
          detail="The recent form sample exists, but numeric trend values are not available for this hero in the current filter scope."
        />
      )}
    </section>
  );
}

function RecentFormStat({
  detail,
  label,
  value
}: Readonly<{
  detail: string;
  label: string;
  value: string;
}>) {
  return (
    <article>
      <span className="ops-label">{label}</span>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  );
}

function MiniTrendCard({ series }: Readonly<{ series: TrendSeries }>) {
  const values = series.points.map((point) => point.value).filter((value): value is number => value !== null);

  return (
    <article className="analytics-mastery-mini-trend-card">
      <div>
        <span className="ops-label">{series.label}</span>
        <strong>{latestValueLabel(values.at(-1), series.valueSuffix)}</strong>
        <p>{series.description}</p>
      </div>
      {values.length >= 2 ? (
        <Sparkline points={series.points} valueSuffix={series.valueSuffix} />
      ) : (
        <p className="analytics-mastery-mini-trend-empty">{series.emptyText}</p>
      )}
    </article>
  );
}

function Sparkline({
  points,
  valueSuffix
}: Readonly<{
  points: TrendPoint[];
  valueSuffix?: string;
}>) {
  const numericPoints = points.filter((point): point is TrendPoint & { value: number } => point.value !== null);
  const values = numericPoints.map((point) => point.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const step = numericPoints.length > 1 ? 100 / (numericPoints.length - 1) : 100;
  const chartPoints = numericPoints.map((point, index) => {
    const x = index * step;
    const y = 34 - ((point.value - min) / range) * 28;
    return {
      ...point,
      x,
      y
    };
  });
  const polyline = chartPoints.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(" ");

  return (
    <div className="analytics-mastery-sparkline" aria-label={`${numericPoints.length} point ${valueSuffix ?? ""} trend`}>
      <svg aria-hidden="true" focusable="false" viewBox="0 0 100 40">
        <polyline points={polyline} />
        {chartPoints.map((point, index) => (
          <circle
            className={classNames(`is-${point.outcome}`)}
            cx={point.x}
            cy={point.y}
            key={`${point.label}-${index}`}
            r="2.6"
          />
        ))}
      </svg>
      <div>
        <span>{numericPoints[0]?.label ?? "Start"}</span>
        <span>{numericPoints.at(-1)?.label ?? "Latest"}</span>
      </div>
    </div>
  );
}

function OutcomeStrip({ matches }: Readonly<{ matches: HeroMasteryRecentMatch[] }>) {
  if (matches.length === 0) {
    return null;
  }

  return (
    <article className="analytics-mastery-mini-trend-card analytics-mastery-outcome-strip">
      <div>
        <span className="ops-label">Win/loss sequence</span>
        <strong>{matches.length} recent matches</strong>
        <p>Oldest to newest returned match sample.</p>
      </div>
      <div className="analytics-mastery-result-strip">
        {matches.map((match, index) => (
          <span
            className={classNames(
              match.won === true && "is-win",
              match.won === false && "is-loss",
              match.won === null && "is-unknown"
            )}
            key={`${match.matchId ?? match.matchGameId ?? "match"}-${index}`}
            title={`${matchLabel(match, index)}: ${resultLabel(match.won)}`}
          >
            {match.won === true ? "W" : match.won === false ? "L" : "?"}
          </span>
        ))}
      </div>
    </article>
  );
}

function buildTrendSeries(matches: HeroMasteryRecentMatch[]): TrendSeries[] {
  return [
    {
      description: "KDA across recent returned matches.",
      emptyText: "KDA trend needs at least two numeric recent matches.",
      label: "KDA trend",
      points: matches.map((match, index) => trendPoint(match, index, match.kda))
    },
    {
      description: "Gold and XP pacing shown as GPM/XPM average.",
      emptyText: "Economy trend needs GPM or XPM values.",
      label: "GPM/XPM trend",
      points: matches.map((match, index) => {
        const economyValue = averageNullable(match.goldPerMin, match.xpPerMin);
        return trendPoint(match, index, economyValue);
      })
    },
    {
      description: "Hero damage when available, otherwise tower damage.",
      emptyText: "Impact trend needs damage values.",
      label: "Damage trend",
      points: matches.map((match, index) => trendPoint(match, index, match.heroDamage ?? match.towerDamage))
    },
    {
      description: "Deaths across recent returned matches.",
      emptyText: "Deaths trend needs at least two recent rows.",
      label: "Deaths trend",
      points: matches.map((match, index) => trendPoint(match, index, match.deaths))
    }
  ];
}

function trendPoint(match: HeroMasteryRecentMatch, index: number, value: number | null): TrendPoint {
  return {
    label: matchLabel(match, index),
    outcome: match.won === true ? "win" : match.won === false ? "loss" : "unknown",
    value
  };
}

function recentFormSummary(matches: HeroMasteryRecentMatch[]): RecentFormSummary {
  const sampleSize = matches.length;
  const wins = matches.filter((match) => match.won === true).length;
  const losses = matches.filter((match) => match.won === false).length;
  const roughContextCount = matches.filter((match) => match.contextClassification === "ROUGH_GAME").length;
  const stompContextCount = matches.filter((match) => match.contextClassification === "STOMP_LOSS").length;

  return {
    avgGoldPerMin: average(matches.map((match) => match.goldPerMin)),
    avgKda: average(matches.map((match) => match.kda)),
    avgXpPerMin: average(matches.map((match) => match.xpPerMin)),
    losses,
    roughContextCount,
    sampleSize,
    stompContextCount,
    winRate: sampleSize > 0 ? (wins / sampleSize) * 100 : null,
    wins
  };
}

function average(values: Array<number | null>) {
  const numericValues = values.filter((value): value is number => value !== null && Number.isFinite(value));

  if (numericValues.length === 0) {
    return null;
  }

  return numericValues.reduce((sum, value) => sum + value, 0) / numericValues.length;
}

function averageNullable(first: number | null, second: number | null) {
  return average([first, second]);
}

function latestValueLabel(value: number | undefined, suffix?: string) {
  if (value === undefined) {
    return "No data";
  }

  return `${safeMetricNumber(value, 2)}${suffix ?? ""}`;
}

function matchLabel(match: HeroMasteryRecentMatch, index: number) {
  if (match.dotaMatchId) {
    return `Dota ${match.dotaMatchId}`;
  }

  return `Match ${index + 1}`;
}

function resultLabel(won: boolean | null) {
  if (won === true) {
    return "Win";
  }

  if (won === false) {
    return "Loss";
  }

  return "Result unavailable";
}
