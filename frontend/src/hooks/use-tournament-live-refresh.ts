"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type LiveRefreshStatus = "live" | "polling" | "paused" | "reconnecting" | "error";

interface UseTournamentLiveRefreshOptions {
  enabled: boolean;
  hiddenIntervalMs?: number;
  immediate?: boolean;
  intervalMs: number;
  label?: string;
  onRefresh: () => Promise<void> | void;
}

interface LiveRefreshState {
  errorCount: number;
  lastError?: string;
  lastUpdated: Date | null;
  refreshNow: () => Promise<void>;
  status: LiveRefreshStatus;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Live refresh failed.";
}

function isDocumentHidden() {
  return typeof document !== "undefined" && document.visibilityState === "hidden";
}

export function useTournamentLiveRefresh({
  enabled,
  hiddenIntervalMs = 60_000,
  immediate = false,
  intervalMs,
  onRefresh
}: UseTournamentLiveRefreshOptions): LiveRefreshState {
  const [errorCount, setErrorCount] = useState(0);
  const [lastError, setLastError] = useState<string | undefined>();
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [status, setStatus] = useState<LiveRefreshStatus>(enabled ? "polling" : "paused");
  const isRunningRef = useRef(false);
  const hasImmediateRunRef = useRef(false);
  const onRefreshRef = useRef(onRefresh);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  const runRefresh = useCallback(async () => {
    if (!enabled || isRunningRef.current) {
      return;
    }

    isRunningRef.current = true;
    setStatus((current) => {
      if (isDocumentHidden()) {
        return "paused";
      }

      return current === "reconnecting" || current === "error" ? "reconnecting" : "polling";
    });

    try {
      await onRefreshRef.current();
      setLastUpdated(new Date());
      setLastError(undefined);
      setErrorCount(0);
      setStatus(isDocumentHidden() ? "paused" : "live");
    } catch (error) {
      setLastError(errorMessage(error));
      setErrorCount((current) => {
        const nextCount = current + 1;
        setStatus(nextCount >= 3 ? "error" : "reconnecting");
        return nextCount;
      });
    } finally {
      isRunningRef.current = false;
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }

    let disposed = false;

    function clearTimer() {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    }

    function nextDelay() {
      if (isDocumentHidden()) {
        return hiddenIntervalMs;
      }

      const failures = Math.min(errorCount, 3);
      return Math.min(intervalMs * 2 ** failures, 60_000);
    }

    function scheduleNext() {
      clearTimer();

      if (disposed || !enabled) {
        return;
      }

      const delay = nextDelay();

      if (isDocumentHidden() && delay <= 0) {
        setStatus("paused");
        return;
      }

      setStatus((current) => (isDocumentHidden() ? "paused" : current));
      timerRef.current = window.setTimeout(async () => {
        await runRefresh();
        scheduleNext();
      }, delay);
    }

    function handleVisibilityChange() {
      if (isDocumentHidden()) {
        setStatus("paused");
        scheduleNext();
        return;
      }

      void runRefresh().finally(scheduleNext);
    }

    if (immediate && !hasImmediateRunRef.current) {
      hasImmediateRunRef.current = true;
      void runRefresh().finally(scheduleNext);
    } else {
      scheduleNext();
    }

    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      disposed = true;
      clearTimer();
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled, errorCount, hiddenIntervalMs, immediate, intervalMs, runRefresh]);

  return {
    errorCount,
    lastError,
    lastUpdated,
    refreshNow: runRefresh,
    status: enabled ? status : "paused"
  };
}
