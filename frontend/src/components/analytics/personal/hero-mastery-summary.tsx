import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type {
  HeroMasteryNote,
  HeroMasteryResponse,
  HeroMasteryVerdict
} from "@/lib/analytics-data";
import { classNames, formatPercent } from "@/lib/utils";

const VERDICT_COPY: Record<HeroMasteryVerdict, { label: string; text: string }> = {
  INSUFFICIENT_DATA: {
    label: "Not enough data",
    text: "Backend verdict: the current hero sample is too small for a reliable read."
  },
  NEEDS_WORK: {
    label: "Focus area",
    text: "Backend verdict: this hero has at least one rule-based area to review."
  },
  STABLE: {
    label: "Stable pick",
    text: "Backend verdict: this hero is broadly near your overall baseline."
  },
  STRONG: {
    label: "Strong hero",
    text: "Backend verdict: this hero is currently ahead of your overall baseline."
  }
};

export function HeroMasterySummary({ mastery }: Readonly<{ mastery: HeroMasteryResponse }>) {
  const verdict = VERDICT_COPY[mastery.masteryVerdict];
  const statGroups = heroMasteryStatGroups(mastery);

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
        <p>{verdictDescription(verdict.text, mastery.deterministicNotes)}</p>
      </div>
      <div className="analytics-mastery-stat-sections">
        {statGroups.map((group) => (
          <section className="analytics-mastery-stat-section" key={group.title}>
            <div className="analytics-mastery-stat-section-header">
              <span className="ops-label">{group.title}</span>
              <p>{group.description}</p>
            </div>
            <div className="analytics-mastery-stat-grid">
              {group.stats.map((stat) => (
                <article className="analytics-mastery-stat-card" key={`${group.title}-${stat.label}`}>
                  <span className="ops-label">{stat.label}</span>
                  <strong>{stat.value}</strong>
                  <p>{stat.detail}</p>
                </article>
              ))}
            </div>
          </section>
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

function heroMasteryStatGroups(mastery: HeroMasteryResponse) {
  return [
    {
      description: "Raw match outcomes for this hero.",
      stats: [
        { detail: `${mastery.wins}-${mastery.losses} W-L`, label: "Games", value: countMetricValue(mastery.games) },
        { detail: "raw wins", label: "Wins", value: countMetricValue(mastery.wins) },
        { detail: "raw losses", label: "Losses", value: countMetricValue(mastery.losses) },
        { detail: "raw match result rate", label: "Win rate", value: formatPercent(mastery.winRate) }
      ],
      title: "Results"
    },
    {
      description: "Raw combat output and K/D/A profile.",
      stats: [
        { detail: "kills plus assists over deaths", label: "KDA", value: safeMetricNumber(mastery.kda, 2) },
        {
          detail: "average kills / deaths / assists",
          label: "Avg K / D / A",
          value: `${safeMetricNumber(mastery.avgKills)} / ${safeMetricNumber(mastery.avgDeaths)} / ${safeMetricNumber(mastery.avgAssists)}`
        },
        {
          detail: "average per match",
          label: "Hero damage",
          value: countMetricValue(Math.round(mastery.avgHeroDamage))
        }
      ],
      title: "Fighting"
    },
    {
      description: "Raw farm, scaling, and resource totals.",
      stats: [
        {
          detail: "average economy",
          label: "GPM / XPM",
          value: `${safeMetricNumber(mastery.avgGoldPerMin)} / ${safeMetricNumber(mastery.avgXpPerMin)}`
        },
        {
          detail: "average lane resources",
          label: "Last hits / denies",
          value: `${safeMetricNumber(mastery.avgLastHits)} / ${safeMetricNumber(mastery.avgDenies)}`
        },
        {
          detail: "average per match",
          label: "Net worth",
          value: countMetricValue(Math.round(mastery.avgNetWorth))
        },
        {
          detail: "average hero level",
          label: "Level",
          value: safeMetricNumber(mastery.avgLevel)
        }
      ],
      title: "Economy"
    },
    {
      description: "Raw building pressure.",
      stats: [
        {
          detail: "average per match",
          label: "Tower damage",
          value: countMetricValue(Math.round(mastery.avgTowerDamage))
        }
      ],
      title: "Objectives"
    },
    {
      description: "Raw utility contribution.",
      stats: [
        {
          detail: "average per match",
          label: "Healing",
          value: countMetricValue(Math.round(mastery.avgHeroHealing))
        }
      ],
      title: "Support/Utility"
    }
  ];
}

function verdictDescription(baseText: string, notes: HeroMasteryNote[]) {
  const leadingNote = notes.find((note) => note.message.trim());

  if (!leadingNote) {
    return baseText;
  }

  return `${baseText} Backend note: ${leadingNote.message}`;
}
