const metricSlots = ["registrations", "matches", "teams", "imports"];
const tableRows = ["header", "row-a", "row-b", "row-c"];

export function RouteLoadingSkeleton() {
  return (
    <section
      aria-busy="true"
      aria-label="Loading page"
      className="route-loading-skeleton"
      role="status"
    >
      <div aria-hidden="true" className="route-skeleton-hero ops-panel">
        <span className="route-skeleton-item route-skeleton-kicker" />
        <span className="route-skeleton-item route-skeleton-title" />
        <span className="route-skeleton-item route-skeleton-copy" />
        <span className="route-skeleton-item route-skeleton-copy route-skeleton-copy-short" />
        <div className="route-skeleton-actions">
          <span className="route-skeleton-item route-skeleton-chip" />
          <span className="route-skeleton-item route-skeleton-chip route-skeleton-chip-secondary" />
        </div>
      </div>

      <div aria-hidden="true" className="route-skeleton-metrics">
        {metricSlots.map((slot) => (
          <div className="route-skeleton-metric ops-panel" key={slot}>
            <span className="route-skeleton-item route-skeleton-icon" />
            <span className="route-skeleton-item route-skeleton-value" />
            <span className="route-skeleton-item route-skeleton-meta" />
          </div>
        ))}
      </div>

      <div aria-hidden="true" className="route-skeleton-layout">
        <div className="route-skeleton-panel ops-panel">
          <span className="route-skeleton-item route-skeleton-kicker" />
          <span className="route-skeleton-item route-skeleton-heading" />
          <div className="route-skeleton-table">
            {tableRows.map((row) => (
              <span className="route-skeleton-item route-skeleton-row" key={row} />
            ))}
          </div>
        </div>

        <aside className="route-skeleton-panel route-skeleton-panel-compact ops-panel">
          <span className="route-skeleton-item route-skeleton-kicker" />
          <span className="route-skeleton-item route-skeleton-heading route-skeleton-heading-short" />
          <div className="route-skeleton-stack">
            {tableRows.slice(1).map((row) => (
              <span className="route-skeleton-item route-skeleton-side-row" key={row} />
            ))}
          </div>
        </aside>
      </div>
    </section>
  );
}
