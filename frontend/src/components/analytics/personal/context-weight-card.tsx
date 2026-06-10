import { safeMetricNumber } from "@/components/analytics/analytics-formatters";
import type {
  PlayerInsight,
  PlayerInsightContextWeight
} from "@/lib/analytics-data";

export function personalInsightMetricLabel(insight: PlayerInsight) {
  const current = safeMetricNumber(insight.currentValue, 2);

  if (insight.comparisonValue === null) {
    return `${insight.metricName}: ${current}`;
  }

  return `${insight.metricName}: ${current} vs ${safeMetricNumber(insight.comparisonValue, 2)}`;
}

export function isContextWeightInsight(insight: PlayerInsight) {
  return insight.metricName === "contextWeight" || insight.contextWeight !== null;
}

function contextWeightLabel(contextWeight: PlayerInsightContextWeight | null) {
  return `Context weight: ${safeMetricNumber(contextWeight?.weight, 2)}`;
}

function contextEnumLabel(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function ContextWeightInsightCard({ insight }: Readonly<{ insight: PlayerInsight }>) {
  return (
    <article className="analytics-context-card">
      <div>
        <span className="ops-label">Context layer</span>
        <strong>{insight.title}</strong>
        <p>{insight.description}</p>
        <p>
          Rough or stomp-like games stay visible with real raw stats. Their context only reduces how much they skew
          long-term interpretation, so one unusually bad game does not dominate trend analytics.
        </p>
      </div>
      <ContextWeightDetails
        contextWeight={insight.contextWeight}
        fallbackMetric={personalInsightMetricLabel(insight)}
      />
    </article>
  );
}

export function ContextWeightDetails({
  contextWeight,
  fallbackMetric
}: Readonly<{
  contextWeight: PlayerInsightContextWeight | null;
  fallbackMetric?: string;
}>) {
  if (!contextWeight) {
    return fallbackMetric ? <span className="analytics-context-weight">{fallbackMetric}</span> : null;
  }

  return (
    <div className="analytics-context-meta">
      <span className="analytics-context-weight">{contextWeightLabel(contextWeight)}</span>
      <p>
        1.00 means normal game context. Lower values mean rough or stomp game context with less influence on
        long-term interpretations.
      </p>
      {contextWeight.message ? <em>{contextWeight.message}</em> : null}
      {contextWeight.classification || contextWeight.reasons.length > 0 ? (
        <div className="analytics-context-badges">
          {contextWeight.classification ? (
            <span className="ops-badge">{contextEnumLabel(contextWeight.classification)}</span>
          ) : null}
          {contextWeight.reasons.map((reason) => (
            <span className="ops-badge" key={reason}>{contextEnumLabel(reason)}</span>
          ))}
        </div>
      ) : null}
    </div>
  );
}
