import { classNames } from "@/lib/utils";

const metricSlots = ["tournaments", "live", "registrations", "schedule"];
const cardSlots = ["ancient-cup", "mid-wars", "radiant-finals"];

export function TournamentsLoadingSkeleton() {
  return (
    <section
      aria-busy="true"
      aria-label="Loading tournaments"
      className="tournament-command tournament-loading-skeleton"
      role="status"
    >
      <section aria-hidden="true" className="tournament-command-header ops-panel ops-command-grid">
        <div className="tournament-command-copy tournament-skeleton-command-copy">
          <SkeletonItem className="tournament-skeleton-kicker" />
          <div className="tournament-command-title-row">
            <SkeletonItem className="tournament-skeleton-title" />
          </div>
          <div className="tournament-skeleton-description tournament-skeleton-header-description">
            <SkeletonItem className="tournament-skeleton-copy-line" />
            <SkeletonItem className="tournament-skeleton-copy-line tournament-skeleton-copy-short" />
          </div>
        </div>

        <div className="tournament-command-actions">
          <SkeletonItem className="tournament-skeleton-header-action" />
        </div>

        <div className="tournament-command-header-grid">
          <div className="tournament-meta-grid">
            {metricSlots.map((slot) => (
              <article className="tournament-meta-card tournament-skeleton-meta-card" key={slot}>
                <SkeletonItem className="tournament-skeleton-meta-icon" />
                <SkeletonItem className="tournament-skeleton-meta-label" />
                <SkeletonItem className="tournament-skeleton-meta-value" />
                <SkeletonItem className="tournament-skeleton-meta-detail" />
              </article>
            ))}
          </div>
        </div>
      </section>

      <section aria-hidden="true" className="tournament-command-panel ops-panel">
        <div className="section-header tournament-skeleton-section-header">
          <div>
            <SkeletonItem className="tournament-skeleton-kicker" />
            <SkeletonItem className="tournament-skeleton-section-title" />
            <div className="tournament-skeleton-description">
              <SkeletonItem className="tournament-skeleton-copy-line" />
            </div>
          </div>
        </div>

        <div className="tournament-card-grid">
          {cardSlots.map((slot) => (
            <article className="tournament-card ops-card tournament-skeleton-card" key={slot}>
              <div className="card-title-row">
                <div className="tournament-skeleton-card-heading">
                  <SkeletonItem className="tournament-skeleton-card-title" />
                  <SkeletonItem className="tournament-skeleton-card-format" />
                </div>
                <SkeletonItem className="tournament-skeleton-status" />
              </div>

              <div className="tournament-skeleton-card-description">
                <SkeletonItem className="tournament-skeleton-copy-line" />
                <SkeletonItem className="tournament-skeleton-copy-line" />
                <SkeletonItem className="tournament-skeleton-copy-line tournament-skeleton-copy-short" />
              </div>

              <div className="card-meta-grid">
                <SkeletonItem className="tournament-skeleton-card-meta" />
                <SkeletonItem className="tournament-skeleton-card-meta tournament-skeleton-card-meta-short" />
              </div>

              <SkeletonItem className="tournament-skeleton-link" />
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}

function SkeletonItem({ className }: { className: string }) {
  return <span className={classNames("route-skeleton-item", className)} />;
}
