type ComparisonCardSide = {
  metrics: Array<{ label: string; value: string }>;
  name: string;
  subtitle: string;
};

export function ComparisonCards({
  left,
  right
}: Readonly<{
  left: ComparisonCardSide;
  right: ComparisonCardSide;
}>) {
  return (
    <div className="analytics-comparison-cards">
      {[left, right].map((side) => (
        <article className="analytics-comparison-card" key={side.name}>
          <div>
            <span className="ops-label">{side.subtitle}</span>
            <strong>{side.name}</strong>
          </div>
          <dl>
            {side.metrics.map((metric) => (
              <div key={`${side.name}-${metric.label}`}>
                <dt>{metric.label}</dt>
                <dd>{metric.value}</dd>
              </div>
            ))}
          </dl>
        </article>
      ))}
    </div>
  );
}
