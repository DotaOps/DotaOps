import {
  type PlayerComparisonCandidate,
  type PlayerComparisonWarning
} from "@/lib/analytics-data";

export function PlayerComparisonWarnings({ warnings }: Readonly<{ warnings: PlayerComparisonWarning[] }>) {
  if (warnings.length === 0) {
    return null;
  }

  return (
    <div className="analytics-warning-list">
      {warnings.map((warning, index) => (
        <article key={`${warning.code}-${warning.profileId ?? "shared"}-${warning.heroId ?? index}`}>
          <span className="ops-label">{warning.severity}</span>
          <strong>{warning.message}</strong>
          <p>
            {warning.metricName} sample {warning.sampleSize.toLocaleString("en-US")} /
            recommended {warning.recommendedMinimum.toLocaleString("en-US")}
          </p>
        </article>
      ))}
    </div>
  );
}

export function PlayerCandidateDataWarnings({
  candidates
}: Readonly<{
  candidates: PlayerComparisonCandidate[];
}>) {
  return (
    <div className="analytics-warning-list">
      {candidates.map((candidate) => (
        <article key={`candidate-warning-${candidate.profileId}`}>
          <span className="ops-label">Candidate warning</span>
          <strong>{candidate.displayName} has no analytics data in this scope.</strong>
          <p>{candidate.label ?? "Comparison can still be attempted, but aggregate cards may be empty."}</p>
        </article>
      ))}
    </div>
  );
}
