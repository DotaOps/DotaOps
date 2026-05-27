"use client";

import { classNames } from "@/lib/utils";

export type DashboardLoadingRole = "captain" | "organizer" | "player";

const rosterRows = ["player-a", "player-b", "player-c", "player-d", "player-e"];
const squadRows = ["squad-a", "squad-b", "squad-c"];
const chipRows = ["form-a", "form-b", "form-c", "form-d", "form-e"];

export function DashboardLoadingSkeleton({
  role = "player"
}: {
  role?: DashboardLoadingRole;
}) {
  return (
    <section
      aria-busy="true"
      aria-label="Loading dashboard"
      className={classNames("role-dashboard", `role-dashboard-${role}`, "dashboard-loading-skeleton")}
      role="status"
    >
      <div aria-hidden="true" className="role-dashboard-content">
        {role === "captain" ? <CaptainDashboardContentSkeleton /> : null}
        {role === "organizer" ? <OrganizerDashboardContentSkeleton /> : null}
        {role === "player" ? <PlayerDashboardContentSkeleton /> : null}
      </div>
    </section>
  );
}

function PlayerDashboardContentSkeleton() {
  return (
    <>
      <section className="role-hero role-player-hero dashboard-skeleton-hero">
        <div className="role-player-hero-copy dashboard-skeleton-hero-copy">
          <div className="dashboard-skeleton-label-row">
            <SkeletonItem className="dashboard-skeleton-hero-kicker" />
            <SkeletonItem className="dashboard-skeleton-inline-label" />
          </div>
          <SkeletonItem className="dashboard-skeleton-player-title" />
          <div className="dashboard-skeleton-copy-stack">
            <SkeletonItem className="dashboard-skeleton-copy-line" />
            <SkeletonItem className="dashboard-skeleton-copy-line dashboard-skeleton-copy-short" />
          </div>
          <ActionSkeletons />
        </div>

        <RecentFormSkeleton className="role-hero-form role-player-form" />
      </section>

      <section className="role-player-grid">
        <div className="role-player-main">
          <div className="role-section-heading dashboard-skeleton-section-heading">
            <SkeletonItem className="dashboard-skeleton-section-title" />
            <SkeletonItem className="dashboard-skeleton-section-meta" />
          </div>

          <div className="role-performance-grid">
            {["kda", "impact", "winrate", "heroes"].map((metric, index) => (
              <PerformanceCardSkeleton featured={index === 2} key={metric} />
            ))}
          </div>
        </div>

        <RolePanelSkeleton className="role-player-roster-panel" rows="roster" />
      </section>

      <RolePanelSkeleton action rows="match-log" />
    </>
  );
}

function CaptainDashboardContentSkeleton() {
  return (
    <>
      <KpiSkeletonGrid columns="four" count={4} />

      <section className="role-hero role-captain-hero dashboard-skeleton-hero">
        <div className="role-hero-content dashboard-skeleton-hero-copy">
          <SkeletonItem className="dashboard-skeleton-hero-kicker" />
          <SkeletonItem className="dashboard-skeleton-captain-title" />
          <SkeletonItem className="dashboard-skeleton-status-line" />
        </div>

        <RecentFormSkeleton className="role-hero-form" />

        <div className="role-hero-meta dashboard-skeleton-captain-meta">
          {["opponent", "time"].map((item) => (
            <article key={item}>
              <SkeletonItem className="dashboard-skeleton-meta-icon" />
              <div className="dashboard-skeleton-meta-copy">
                <SkeletonItem className="dashboard-skeleton-inline-label" />
                <SkeletonItem className="dashboard-skeleton-meta-value" />
              </div>
            </article>
          ))}
          <SkeletonItem className="dashboard-skeleton-briefing" />
        </div>
      </section>

      <section className="role-captain-grid">
        <div className="role-captain-main">
          <RolePanelSkeleton action rows="table" />
          <RolePanelSkeleton rows="strip-roster" />

          <section className="role-mini-card-grid">
            {["wins", "lane", "draft"].map((metric) => (
              <MiniCardSkeleton key={metric} />
            ))}
          </section>
        </div>

        <DashboardSideStackSkeleton channel />
      </section>
    </>
  );
}

function OrganizerDashboardContentSkeleton() {
  return (
    <>
      <section className="role-hero role-organizer-hero dashboard-skeleton-hero">
        <div className="role-organizer-hero-copy dashboard-skeleton-hero-copy">
          <div className="dashboard-skeleton-label-row">
            <SkeletonItem className="dashboard-skeleton-hero-kicker" />
            <SkeletonItem className="dashboard-skeleton-inline-label" />
          </div>
          <SkeletonItem className="dashboard-skeleton-organizer-title" />
          <div className="dashboard-skeleton-copy-stack">
            <SkeletonItem className="dashboard-skeleton-copy-line" />
            <SkeletonItem className="dashboard-skeleton-copy-line dashboard-skeleton-copy-short" />
          </div>
          <div className="role-organizer-signals dashboard-skeleton-signals">
            {["teams", "integrity", "status"].map((signal) => (
              <article key={signal}>
                <SkeletonItem className="dashboard-skeleton-inline-label" />
                <SkeletonItem className="dashboard-skeleton-signal-value" />
              </article>
            ))}
          </div>
          <ActionSkeletons />
        </div>
      </section>

      <KpiSkeletonGrid columns="five" count={5} />

      <section className="role-organizer-grid">
        <div className="role-organizer-main">
          <RolePanelSkeleton action rows="table" />
          <RolePanelSkeleton rows="squads" />
        </div>

        <DashboardSideStackSkeleton />
      </section>
    </>
  );
}

function KpiSkeletonGrid({
  columns,
  count
}: {
  columns: "four" | "five";
  count: number;
}) {
  return (
    <section className={classNames("role-kpi-grid", `role-kpi-${columns}`)}>
      {Array.from({ length: count }, (_, index) => (
        <article className="role-kpi-card dashboard-skeleton-kpi" key={index}>
          <div className="role-kpi-heading">
            <SkeletonItem className="dashboard-skeleton-kpi-label" />
            <SkeletonItem className="dashboard-skeleton-kpi-icon" />
          </div>
          <div className="role-kpi-value-row">
            <SkeletonItem className="dashboard-skeleton-kpi-value" />
            <SkeletonItem className="dashboard-skeleton-kpi-track" />
          </div>
          <SkeletonItem className="dashboard-skeleton-kpi-detail" />
        </article>
      ))}
    </section>
  );
}

function PerformanceCardSkeleton({ featured }: { featured?: boolean }) {
  return (
    <article className={classNames("role-performance-card", featured && "is-featured")}>
      <SkeletonItem className="dashboard-skeleton-card-label" />
      <SkeletonItem className="dashboard-skeleton-card-value" />
      <SkeletonItem className="dashboard-skeleton-card-detail" />
    </article>
  );
}

function MiniCardSkeleton() {
  return (
    <article className="role-mini-card dashboard-skeleton-mini-card">
      <SkeletonItem className="dashboard-skeleton-mini-icon" />
      <SkeletonItem className="dashboard-skeleton-card-label" />
      <SkeletonItem className="dashboard-skeleton-card-value" />
      <SkeletonItem className="dashboard-skeleton-card-detail" />
    </article>
  );
}

function RolePanelSkeleton({
  action,
  className,
  rows
}: {
  action?: boolean;
  className?: string;
  rows: "match-log" | "roster" | "squads" | "strip-roster" | "table";
}) {
  return (
    <section className={classNames("role-panel", className)}>
      <div className="role-panel-header">
        <SkeletonItem className="dashboard-skeleton-panel-title" />
        {action ? <SkeletonItem className="dashboard-skeleton-panel-action" /> : null}
      </div>
      {rows === "roster" ? <PlayerRosterSkeleton /> : null}
      {rows === "strip-roster" ? <RosterStripSkeleton /> : null}
      {rows === "squads" ? <SquadGridSkeleton /> : null}
      {rows === "table" ? <TableSkeleton /> : null}
      {rows === "match-log" ? <TableSkeleton footer rows={5} /> : null}
    </section>
  );
}

function TableSkeleton({
  footer = false,
  rows = 4
}: {
  footer?: boolean;
  rows?: number;
}) {
  return (
    <>
      <div className="dashboard-skeleton-table">
        {Array.from({ length: rows }, (_, index) => (
          <SkeletonItem
            className={classNames(
              "dashboard-skeleton-table-row",
              index === 0 && "dashboard-skeleton-table-heading"
            )}
            key={index}
          />
        ))}
      </div>
      {footer ? <SkeletonItem className="dashboard-skeleton-table-footer" /> : null}
    </>
  );
}

function PlayerRosterSkeleton() {
  return (
    <>
      <div className="role-player-roster">
        {rosterRows.map((row) => (
          <article key={row}>
            <SkeletonItem className="dashboard-skeleton-roster-avatar" />
            <div className="dashboard-skeleton-roster-copy">
              <SkeletonItem className="dashboard-skeleton-roster-name" />
              <SkeletonItem className="dashboard-skeleton-roster-meta" />
            </div>
            <SkeletonItem className="dashboard-skeleton-roster-status" />
          </article>
        ))}
      </div>
      <SkeletonItem className="dashboard-skeleton-panel-button" />
    </>
  );
}

function RosterStripSkeleton() {
  return (
    <div className="role-roster-strip">
      {rosterRows.map((row) => (
        <article key={row}>
          <SkeletonItem className="dashboard-skeleton-roster-avatar" />
          <div className="dashboard-skeleton-roster-copy">
            <SkeletonItem className="dashboard-skeleton-roster-name" />
            <SkeletonItem className="dashboard-skeleton-roster-meta" />
          </div>
        </article>
      ))}
    </div>
  );
}

function SquadGridSkeleton() {
  return (
    <div className="role-squad-grid">
      {squadRows.map((row) => (
        <article key={row}>
          <SkeletonItem className="dashboard-skeleton-squad-avatar" />
          <SkeletonItem className="dashboard-skeleton-squad-name" />
          <SkeletonItem className="dashboard-skeleton-roster-status" />
          <SkeletonItem className="dashboard-skeleton-squad-button" />
        </article>
      ))}
    </div>
  );
}

function DashboardSideStackSkeleton({ channel = false }: { channel?: boolean }) {
  return (
    <aside className="role-side-stack">
      <SidePanelSkeleton rows={3} />
      <SidePanelSkeleton rows={2} tone="alerts" />
      <SidePanelSkeleton rows={channel ? 1 : 3} tone={channel ? "channel" : "pipeline"} />
    </aside>
  );
}

function SidePanelSkeleton({
  rows,
  tone = "actions"
}: {
  rows: number;
  tone?: "actions" | "alerts" | "channel" | "pipeline";
}) {
  return (
    <section className="role-panel">
      <div className="role-panel-header">
        <SkeletonItem className="dashboard-skeleton-panel-title dashboard-skeleton-panel-title-short" />
      </div>
      <div className={classNames("dashboard-skeleton-side-list", `dashboard-skeleton-side-${tone}`)}>
        {Array.from({ length: rows }, (_, index) => (
          <SkeletonItem className="dashboard-skeleton-side-row" key={index} />
        ))}
      </div>
    </section>
  );
}

function RecentFormSkeleton({ className }: { className: string }) {
  return (
    <div className={className}>
      <SkeletonItem className="dashboard-skeleton-inline-label" />
      <div className="role-form-chips">
        {chipRows.map((chip) => (
          <SkeletonItem className="dashboard-skeleton-form-chip" key={chip} />
        ))}
      </div>
    </div>
  );
}

function ActionSkeletons() {
  return (
    <div className="dashboard-skeleton-actions">
      <SkeletonItem className="dashboard-skeleton-action" />
      <SkeletonItem className="dashboard-skeleton-action dashboard-skeleton-action-secondary" />
    </div>
  );
}

function SkeletonItem({ className }: { className: string }) {
  return <span className={classNames("route-skeleton-item", className)} />;
}
