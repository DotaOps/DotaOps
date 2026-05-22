import { RouteLoadingSkeleton } from "@/components/route-loading-skeleton";
import { DashboardLoadingSkeleton } from "@/components/dashboard/dashboard-loading-skeleton";

const sidebarRows = ["dashboard", "tournaments", "organizer", "team", "analytics", "profile"];

export function WorkspaceLoadingSkeleton({ dashboard = false }: { dashboard?: boolean }) {
  return (
    <div aria-busy="true" className="app-shell route-shell-skeleton">
      <aside
        aria-hidden="true"
        className="sidebar ops-panel ops-scanline route-shell-skeleton-sidebar"
      >
        <div className="route-shell-brand">
          <span className="route-skeleton-item route-shell-brand-mark" />
          <span className="route-shell-brand-copy">
            <span className="route-skeleton-item route-shell-brand-title" />
            <span className="route-skeleton-item route-shell-brand-detail" />
          </span>
        </div>

        <div className="nav-list route-shell-nav">
          {sidebarRows.map((row) => (
            <span className="route-skeleton-item route-shell-nav-row" key={row} />
          ))}
        </div>

        <div className="route-skeleton-item route-shell-sidebar-card" />
      </aside>

      <div className="main-area">
        {dashboard ? null : (
          <header aria-hidden="true" className="topbar ops-panel route-shell-topbar">
            <div className="route-shell-topbar-actions">
              <span className="route-skeleton-item route-shell-status" />
              <span className="route-skeleton-item route-shell-status" />
              <span className="route-skeleton-item route-shell-icon" />
              <span className="route-skeleton-item route-shell-profile" />
              <span className="route-skeleton-item route-shell-action" />
            </div>
          </header>
        )}

        <main className={dashboard ? "page dashboard-page" : "page"}>
          {dashboard ? <DashboardLoadingSkeleton /> : <RouteLoadingSkeleton />}
        </main>
      </div>
    </div>
  );
}
