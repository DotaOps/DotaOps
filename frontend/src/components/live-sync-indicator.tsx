"use client";

import { AlertTriangle, Pause, RadioTower, RefreshCw } from "lucide-react";

import type { LiveRefreshStatus } from "@/hooks/use-tournament-live-refresh";
import { classNames } from "@/lib/utils";

interface LiveSyncIndicatorProps {
  errorCount?: number;
  lastError?: string;
  lastUpdated: Date | null;
  onRefresh?: () => Promise<void> | void;
  status: LiveRefreshStatus;
}

function statusLabel(status: LiveRefreshStatus) {
  if (status === "live") {
    return "Live sync";
  }

  if (status === "polling") {
    return "Polling";
  }

  if (status === "paused") {
    return "Paused";
  }

  if (status === "reconnecting") {
    return "Reconnecting";
  }

  return "Error";
}

function formatTime(value: Date | null) {
  if (!value) {
    return "Awaiting sync";
  }

  return `Last updated ${new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(value)}`;
}

export function LiveSyncIndicator({
  errorCount = 0,
  lastError,
  lastUpdated,
  onRefresh,
  status
}: LiveSyncIndicatorProps) {
  return (
    <div className={classNames("live-sync-indicator", `is-${status}`)}>
      {status === "paused" ? (
        <Pause size={14} />
      ) : status === "error" || status === "reconnecting" ? (
        <AlertTriangle size={14} />
      ) : (
        <RadioTower size={14} />
      )}
      <span>{statusLabel(status)}</span>
      <small>{formatTime(lastUpdated)}</small>
      {errorCount > 0 ? <em>{lastError ?? "Retrying backend sync."}</em> : null}
      {onRefresh ? (
        <button aria-label="Refresh live data" onClick={() => void onRefresh()} type="button">
          <RefreshCw size={13} />
        </button>
      ) : null}
    </div>
  );
}
