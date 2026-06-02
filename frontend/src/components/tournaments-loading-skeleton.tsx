import { classNames } from "@/lib/utils";

const cardSlots = ["ancient-cup", "mid-wars", "radiant-finals"];

export function TournamentsLoadingSkeleton() {
  return (
    <section
      aria-busy="true"
      aria-label="Loading tournaments"
      className="tournament-command tournament-loading-skeleton"
      role="status"
    >
      <section aria-hidden="true" className="tournament-command-header ops-panel">
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
              <div className="tournament-card-media tournament-skeleton-card-media">
                <SkeletonItem className="tournament-skeleton-card-image" />
                <div className="tournament-card-media-badges">
                  <SkeletonItem className="tournament-skeleton-status" />
                  <SkeletonItem className="tournament-skeleton-team-size" />
                </div>
              </div>

              <div className="tournament-card-body">
                <div className="card-title-row">
                  <div className="tournament-skeleton-card-heading">
                    <SkeletonItem className="tournament-skeleton-card-title" />
                    <SkeletonItem className="tournament-skeleton-card-format" />
                  </div>
                </div>

                <div className="tournament-skeleton-card-description">
                  <SkeletonItem className="tournament-skeleton-copy-line" />
                  <SkeletonItem className="tournament-skeleton-copy-line" />
                  <SkeletonItem className="tournament-skeleton-copy-line tournament-skeleton-copy-short" />
                </div>

                <div className="card-meta-grid">
                  <SkeletonItem className="tournament-skeleton-card-meta" />
                  <SkeletonItem className="tournament-skeleton-card-meta" />
                  <SkeletonItem className="tournament-skeleton-card-meta tournament-skeleton-card-meta-short" />
                </div>

                <SkeletonItem className="tournament-skeleton-card-capacity" />

                <SkeletonItem className="tournament-skeleton-link" />
              </div>
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
