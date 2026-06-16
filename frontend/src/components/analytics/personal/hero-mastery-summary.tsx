import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type {
  HeroMasteryResponse,
  HeroMasteryVerdict
} from "@/lib/analytics-data";
import { classNames, formatPercent } from "@/lib/utils";

const VERDICT_COPY: Record<HeroMasteryVerdict, { label: string; text: string }> = {
  INSUFFICIENT_DATA: {
    label: "Insufficient data",
    text: "More matches are needed before this hero can be evaluated reliably."
  },
  NEEDS_WORK: {
    label: "Needs work",
    text: "Several hero-specific metrics are below your current overall baseline."
  },
  STABLE: {
    label: "Stable",
    text: "This hero is broadly in line with your current overall baseline."
  },
  STRONG: {
    label: "Strong",
    text: "This hero is performing ahead of your current overall baseline."
  }
};

export function HeroMasterySummary({ mastery }: Readonly<{ mastery: HeroMasteryResponse }>) {
  const verdict = VERDICT_COPY[mastery.masteryVerdict];
  const stats = [
    { detail: `${mastery.wins}-${mastery.losses} W-L`, label: "Games", value: countMetricValue(mastery.games) },
    { detail: "raw match result rate", label: "Win rate", value: formatPercent(mastery.winRate) },
    {
      detail: `${safeMetricNumber(mastery.avgKills)} / ${safeMetricNumber(mastery.avgDeaths)} / ${safeMetricNumber(mastery.avgAssists)}`,
      label: "KDA",
      value: safeMetricNumber(mastery.kda, 2)
    },
    {
      detail: "average economy",
      label: "GPM / XPM",
      value: `${safeMetricNumber(mastery.avgGoldPerMin)} / ${safeMetricNumber(mastery.avgXpPerMin)}`
    },
    {
      detail: "average lane resources",
      label: "LH / DN",
      value: `${safeMetricNumber(mastery.avgLastHits)} / ${safeMetricNumber(mastery.avgDenies)}`
    },
    {
      detail: "average per match",
      label: "Net worth",
      value: countMetricValue(Math.round(mastery.avgNetWorth))
    },
    {
      detail: "average per match",
      label: "Hero damage",
      value: countMetricValue(Math.round(mastery.avgHeroDamage))
    },
    {
      detail: "average per match",
      label: "Tower damage",
      value: countMetricValue(Math.round(mastery.avgTowerDamage))
    },
    {
      detail: "average per match",
      label: "Healing",
      value: countMetricValue(Math.round(mastery.avgHeroHealing))
    },
    {
      detail: "average hero level",
      label: "Level",
      value: safeMetricNumber(mastery.avgLevel)
    }
  ];

  return (
    <section className="analytics-mastery-summary">
      <div
        className={classNames(
          "analytics-mastery-verdict",
          `is-${mastery.masteryVerdict.toLowerCase().replace(/_/g, "-")}`
        )}
      >
        <span className="ops-label">Mastery verdict</span>
        <strong>{verdict.label}</strong>
        <p>{verdict.text}</p>
      </div>
      <div className="analytics-mastery-stat-grid">
        {stats.map((stat) => (
          <article className="analytics-mastery-stat-card" key={stat.label}>
            <span className="ops-label">{stat.label}</span>
            <strong>{stat.value}</strong>
            <p>{stat.detail}</p>
          </article>
        ))}
      </div>
      <RecentTrendSummary mastery={mastery} />
    </section>
  );
}

function RecentTrendSummary({ mastery }: Readonly<{ mastery: HeroMasteryResponse }>) {
  const trend = mastery.recentTrend;

  if (trend.sampleSize === 0 && trend.metrics.length === 0) {
    return null;
  }

  return (
    <article className="analytics-mastery-card">
      <div className="analytics-mastery-card-header">
        <div>
          <span className="ops-label">Recent trend</span>
          <strong>{trend.interpretation}</strong>
        </div>
        <span className="ops-badge">
          {trend.recentWindowSize} recent / {trend.previousWindowSize} previous
        </span>
      </div>
      {trend.metrics.length > 0 ? (
        <div className="analytics-mastery-trend-grid">
          {trend.metrics.map((metric) => (
            <article
              className={classNames(
                "analytics-mastery-trend-item",
                `is-${metric.direction.toLowerCase()}`
              )}
              key={metric.metric}
            >
              <span>{labelFromMetric(metric.metric)}</span>
              <strong>{safeMetricNumber(metric.recentValue, 2)}</strong>
              <p>
                Previous {safeMetricNumber(metric.previousValue, 2)} / delta {signedNumber(metric.delta)}
              </p>
            </article>
          ))}
        </div>
      ) : null}
    </article>
  );
}

function labelFromMetric(metric: string) {
  return metric
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function signedNumber(value: number) {
  const prefix = value > 0 ? "+" : "";
  return `${prefix}${safeMetricNumber(value, 2)}`;
}
