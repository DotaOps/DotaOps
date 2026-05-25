import Link from "next/link";
import type { ReactNode } from "react";

import { HeaderProfileLink } from "@/components/header-profile-link";
import type {
  DashboardAction,
  DashboardAlert,
  DashboardKpi,
  DashboardTone,
  RosterPlayer
} from "@/lib/role-dashboard-data";
import { classNames } from "@/lib/utils";

interface DashboardTopbarProps {
  avatarUrl?: string | null;
  displayName: string;
}

export function DashboardTopbar({
  avatarUrl,
  displayName
}: DashboardTopbarProps) {
  return (
    <header className="role-dashboard-topbar">
      <div className="role-topbar-actions">
        <HeaderProfileLink avatarUrl={avatarUrl} displayName={displayName} />
      </div>
    </header>
  );
}

export function RoleActionButton({
  action,
  variant = "panel"
}: {
  action: DashboardAction;
  variant?: "primary" | "panel" | "secondary";
}) {
  const Icon = action.icon;
  const className = classNames(
    "role-action-button",
    `role-action-${variant}`,
    action.tone && `role-tone-${action.tone}`,
    action.disabled && "is-disabled"
  );
  const content = (
    <>
      <Icon size={18} />
      <span>{action.label}</span>
    </>
  );

  if (action.href && !action.disabled) {
    return (
      <Link className={className} href={action.href}>
        {content}
      </Link>
    );
  }

  return (
    <button className={className} type="button" disabled={action.disabled}>
      {content}
    </button>
  );
}

export function RoleKpiGrid({
  columns = "four",
  items
}: {
  columns?: "three" | "four" | "five";
  items: DashboardKpi[];
}) {
  return (
    <section className={classNames("role-kpi-grid", `role-kpi-${columns}`)} aria-label="Dashboard KPIs">
      {items.map((item) => (
        <RoleKpiCard item={item} key={item.label} />
      ))}
    </section>
  );
}

export function RoleKpiCard({ item }: { item: DashboardKpi }) {
  const Icon = item.icon;

  return (
    <article className={classNames("role-kpi-card", `role-tone-${item.tone}`)}>
      <div className="role-kpi-heading">
        <span>{item.label}</span>
        <Icon size={20} />
      </div>
      <div className="role-kpi-value-row">
        <strong>{item.value}</strong>
        {typeof item.progress === "number" ? (
          <div className="role-progress-track" aria-hidden="true">
            <span style={{ width: `${item.progress}%` }} />
          </div>
        ) : null}
      </div>
      {item.detail ? <p>{item.detail}</p> : null}
    </article>
  );
}

export function FormChips({ values }: { values: Array<"W" | "L"> }) {
  return (
    <div className="role-form-chips" aria-label="Recent form">
      {values.map((value, index) => (
        <span className={value === "W" ? "is-win" : "is-loss"} key={`${value}-${index}`}>
          {value}
        </span>
      ))}
    </div>
  );
}

export function StatusChip({
  children,
  tone = "muted"
}: {
  children: ReactNode;
  tone?: DashboardTone;
}) {
  return <span className={classNames("role-status-chip", `role-tone-${tone}`)}>{children}</span>;
}

export function RolePanel({
  action,
  children,
  className,
  eyebrow,
  title
}: {
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  eyebrow?: string;
  title: string;
}) {
  return (
    <section className={classNames("role-panel", className)}>
      <div className="role-panel-header">
        <div>
          {eyebrow ? <p>{eyebrow}</p> : null}
          <h2>{title}</h2>
        </div>
        {action ? <div>{action}</div> : null}
      </div>
      {children}
    </section>
  );
}

export function AlertsPanel({ alerts }: { alerts: DashboardAlert[] }) {
  return (
    <RolePanel title="Tactical Alerts" className="role-alert-panel">
      <div className="role-alert-list">
        {alerts.map((alert) => (
          <article className={classNames("role-alert", `role-tone-${alert.tone}`)} key={alert.title}>
            <strong>{alert.title}</strong>
            <p>{alert.detail}</p>
          </article>
        ))}
      </div>
    </RolePanel>
  );
}

export function PlayerAvatar({ player }: { player: RosterPlayer }) {
  return (
    <span className="role-player-avatar" aria-hidden="true">
      {player.avatarCode}
    </span>
  );
}
