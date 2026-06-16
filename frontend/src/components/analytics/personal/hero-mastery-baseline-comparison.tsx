import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import { safeMetricNumber } from "@/components/analytics/analytics-formatters";
import type { HeroMasteryBaselineComparison } from "@/lib/analytics-data";
import { classNames, formatPercent } from "@/lib/utils";

export function HeroMasteryBaselineComparison({
  comparisons
}: Readonly<{
  comparisons: HeroMasteryBaselineComparison[];
}>) {
  if (comparisons.length === 0) {
    return (
      <AnalyticsEmptyBlock
        title="No baseline comparison returned."
        detail="A hero baseline comparison appears after enough overall and hero-specific matches exist."
      />
    );
  }

  return (
    <section className="analytics-mastery-card">
      <div className="analytics-mastery-card-header">
        <div>
          <span className="ops-label">Player baseline</span>
          <strong>Hero vs Overall Baseline</strong>
          <p>Direction is taken from the backend comparison rules, including metrics where lower can be better.</p>
        </div>
      </div>
      <div className="analytics-real-table-wrap">
        <table className="analytics-real-table analytics-mastery-baseline-table">
          <thead>
            <tr>
              <th>Metric</th>
              <th>Hero value</th>
              <th>Your average</th>
              <th>Delta</th>
              <th>Direction</th>
              <th>Interpretation</th>
            </tr>
          </thead>
          <tbody>
            {comparisons.map((comparison) => (
              <tr key={comparison.metric}>
                <td>
                  <strong>{metricLabel(comparison.metric)}</strong>
                  <span>{comparison.metric}</span>
                </td>
                <td>{metricValue(comparison.metric, comparison.heroValue)}</td>
                <td>{metricValue(comparison.metric, comparison.overallValue)}</td>
                <td>{deltaValue(comparison.metric, comparison.delta)}</td>
                <td>
                  <DirectionIndicator direction={comparison.direction} />
                </td>
                <td>{comparison.interpretation}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function DirectionIndicator({
  direction
}: Readonly<{
  direction: HeroMasteryBaselineComparison["direction"];
}>) {
  return (
    <span className={classNames("analytics-baseline-direction", `is-${direction.toLowerCase()}`)}>
      <i aria-hidden="true" />
      <span className={classNames("analytics-direction-badge", `is-${direction.toLowerCase()}`)}>
        {directionLabel(direction)}
      </span>
    </span>
  );
}

function metricLabel(metric: string) {
  return metric
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function metricValue(metric: string, value: number) {
  const normalized = metric.toLowerCase();

  if (normalized.includes("winrate") || normalized.includes("win rate")) {
    return formatPercent(value);
  }

  if (
    normalized.includes("damage") ||
    normalized.includes("worth") ||
    normalized.includes("hits") ||
    normalized.includes("denies") ||
    normalized.includes("gpm") ||
    normalized.includes("xpm") ||
    normalized.includes("healing")
  ) {
    return Math.round(value).toLocaleString("en-US");
  }

  return safeMetricNumber(value, 2);
}

function deltaValue(metric: string, value: number) {
  const prefix = value > 0 ? "+" : "";
  return `${prefix}${metricValue(metric, value)}`;
}

function directionLabel(direction: HeroMasteryBaselineComparison["direction"]) {
  if (direction === "BETTER") {
    return "Better";
  }

  if (direction === "WORSE") {
    return "Worse";
  }

  return "Similar";
}
