import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type { HeroMasteryContextSummary } from "@/lib/analytics-data";

const DEFAULT_CONTEXT_NOTE = "No recent hero match context is available.";

export function HeroMasteryContextSummary({
  contextSummary
}: Readonly<{
  contextSummary: HeroMasteryContextSummary | null | undefined;
}>) {
  if (!contextSummary || !hasContextSummary(contextSummary)) {
    return null;
  }

  const stats = [
    {
      label: "Average context weight",
      value: safeMetricNumber(contextSummary.averageContextWeight, 2)
    },
    {
      label: "Rough games",
      value: countMetricValue(contextSummary.roughGameCount)
    },
    {
      label: "Stomp losses",
      value: countMetricValue(contextSummary.stompLossCount)
    },
    {
      label: "Low confidence",
      value: countMetricValue(contextSummary.lowConfidenceCount)
    },
    {
      label: "Normal games",
      value: countMetricValue(contextSummary.normalGameCount)
    }
  ];

  return (
    <section className="analytics-context-card analytics-mastery-context-card">
      <div className="analytics-mastery-context-copy">
        <div>
          <span className="ops-label">Raw stats</span>
          <strong>Raw Hero Stats Stay Unchanged</strong>
          <p>
            Raw hero stats are unchanged. Context weighting only affects long-term interpretation and the mastery verdict.
          </p>
        </div>
        <div>
          <span className="ops-label">Context interpretation</span>
          <strong>Context Summary</strong>
          <p>{contextSummary.note}</p>
        </div>
      </div>
      <div className="analytics-mastery-context-grid">
        {stats.map((stat) => (
          <article key={stat.label}>
            <span className="ops-label">{stat.label}</span>
            <strong>{stat.value}</strong>
          </article>
        ))}
      </div>
    </section>
  );
}

function hasContextSummary(contextSummary: HeroMasteryContextSummary) {
  const contextGameCount =
    contextSummary.roughGameCount +
    contextSummary.stompLossCount +
    contextSummary.lowConfidenceCount +
    contextSummary.normalGameCount;
  const hasNote = contextSummary.note.trim() !== "" && contextSummary.note !== DEFAULT_CONTEXT_NOTE;
  const hasCustomWeight = Number.isFinite(contextSummary.averageContextWeight)
    && contextSummary.averageContextWeight !== 1;

  return contextGameCount > 0 || hasNote || hasCustomWeight;
}
