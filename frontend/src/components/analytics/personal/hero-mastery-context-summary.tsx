import {
  countMetricValue,
  safeMetricNumber
} from "@/components/analytics/analytics-formatters";
import type { HeroMasteryContextSummary } from "@/lib/analytics-data";

export function HeroMasteryContextSummary({
  contextSummary
}: Readonly<{
  contextSummary: HeroMasteryContextSummary;
}>) {
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
      <div>
        <span className="ops-label">Context layer</span>
        <strong>Context Summary</strong>
        <p>{contextSummary.note}</p>
        <p>
          Raw hero stats are unchanged. Context weighting only affects long-term interpretation and the mastery verdict.
        </p>
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
