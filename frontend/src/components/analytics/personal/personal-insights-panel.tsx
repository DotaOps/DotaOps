import { AnalyticsEmptyBlock } from "@/components/analytics/analytics-empty-block";
import {
  ContextWeightDetails,
  ContextWeightInsightCard,
  isContextWeightInsight,
  personalInsightMetricLabel
} from "@/components/analytics/personal/context-weight-card";
import { SectionHeader } from "@/components/section-header";
import type { PlayerInsight } from "@/lib/analytics-data";

export function PersonalInsightsPanel({ insights }: Readonly<{ insights: PlayerInsight[] }>) {
  return (
    <div className="analytics-terminal-panel analytics-data-panel ops-panel">
      <SectionHeader
        eyebrow="Gameplay insights"
        title="Personal Insights"
        description="Rule-based signals from your recent match and hero analytics."
      />
      <PlayerInsightsList insights={insights} />
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

  const hasContextWeight = insights.some(isContextWeightInsight);

  return (
    <div className="analytics-real-stack">
      {hasContextWeight ? (
        <p className="analytics-context-note">
          Raw match statistics are unchanged. Context weighting is used only for long-term interpretation and coaching-style insights.
        </p>
      ) : null}
      {insights.slice(0, 5).map((insight) => (
        insight.metricName === "contextWeight"
          ? <ContextWeightInsightCard insight={insight} key={`${insight.category}-${insight.metricName}-${insight.title}`} />
          : <PlayerInsightRow insight={insight} key={`${insight.category}-${insight.metricName}-${insight.title}`} />
      ))}
    </div>
  );
}

function PlayerInsightRow({ insight }: Readonly<{ insight: PlayerInsight }>) {
  return (
    <article className="analytics-rank-row">
      <span className="ops-mono">{insight.category.slice(0, 3)}</span>
      <div>
        <strong>{insight.title}</strong>
        <p>{insight.description}</p>
        <ContextWeightDetails contextWeight={insight.contextWeight} />
      </div>
      <div>
        <span>{personalInsightMetricLabel(insight)}</span>
        <em>{insight.evidence}</em>
      </div>
    </article>
  );
}
