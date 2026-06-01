"use client";

import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Filter,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldAlert,
  ShieldCheck
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";

import { ApiRequestError } from "@/lib/api";
import {
  listAdminAuditLogs,
  type AdminAuditAction,
  type AdminAuditFilters,
  type AdminAuditLogItem,
  type AdminAuditPage
} from "@/lib/admin-audit-data";
import { formatDateTime } from "@/lib/utils";

interface AuditFilterForm {
  action: "" | AdminAuditAction;
  actor: string;
  from: string;
  recordId: string;
  table: string;
  to: string;
}

interface AuditError {
  message: string;
  status: number | null;
}

const EMPTY_FILTERS: AuditFilterForm = {
  action: "",
  actor: "",
  from: "",
  recordId: "",
  table: "",
  to: ""
};

const PAGE_SIZES = [10, 20, 50, 100];

function normalizedFilters(filters: AuditFilterForm): AuditFilterForm {
  return {
    action: filters.action,
    actor: filters.actor.trim(),
    from: filters.from,
    recordId: filters.recordId.trim(),
    table: filters.table.trim(),
    to: filters.to
  };
}

function toIso(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

function apiFilters(filters: AuditFilterForm, page: number, size: number): AdminAuditFilters {
  return {
    action: filters.action || undefined,
    actor: filters.actor || undefined,
    from: toIso(filters.from),
    page,
    recordId: filters.recordId || undefined,
    size,
    table: filters.table || undefined,
    to: toIso(filters.to)
  };
}

function errorDetails(caught: unknown): AuditError {
  if (caught instanceof ApiRequestError) {
    return {
      message: caught.message,
      status: caught.status
    };
  }

  return {
    message: caught instanceof Error ? caught.message : "Audit logs could not be loaded.",
    status: null
  };
}

function actionLabel(action: AdminAuditAction) {
  return action.toUpperCase();
}

function displayPage(page: AdminAuditPage | null) {
  if (!page || page.page.totalPages === 0) {
    return "Page 0 / 0";
  }

  return `Page ${page.page.page + 1} / ${page.page.totalPages}`;
}

export function AdminAuditPage() {
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(EMPTY_FILTERS);
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(EMPTY_FILTERS);
  const [error, setError] = useState<AuditError | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [result, setResult] = useState<AdminAuditPage | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let isMounted = true;

    async function load() {
      setIsLoading(true);

      try {
        const nextResult = await listAdminAuditLogs(apiFilters(appliedFilters, page, pageSize));

        if (isMounted) {
          setResult(nextResult);
          setError(null);
        }
      } catch (caught) {
        if (isMounted) {
          setError(errorDetails(caught));
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void load();

    return () => {
      isMounted = false;
    };
  }, [appliedFilters, page, pageSize, reloadToken]);

  const activeFilterCount = useMemo(
    () => Object.values(appliedFilters).filter(Boolean).length,
    [appliedFilters]
  );

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setAppliedFilters(normalizedFilters(draftFilters));
  }

  function resetFilters() {
    setDraftFilters(EMPTY_FILTERS);
    setAppliedFilters(EMPTY_FILTERS);
    setPage(0);
  }

  function retry() {
    setReloadToken((value) => value + 1);
  }

  if (error?.status === 403) {
    return (
      <div className="admin-audit-page">
        <AuditHero />
        <section className="admin-audit-state ops-panel">
          <ShieldAlert size={26} />
          <div>
            <p className="ops-label">Restricted workspace</p>
            <h2>Admin access required</h2>
            <p>This audit trail is only available to DotaOps administrators.</p>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="admin-audit-page">
      <AuditHero />

      <form className="admin-audit-filters ops-panel" onSubmit={applyFilters}>
        <div className="admin-audit-section-heading">
          <div>
            <p className="ops-label">Query controls</p>
            <h2>Audit filters</h2>
          </div>
          <span className="admin-audit-filter-count">
            <Filter size={14} />
            {activeFilterCount} active
          </span>
        </div>

        <div className="admin-audit-filter-grid">
          <label>
            <span>Table</span>
            <input
              onChange={(event) => setDraftFilters({ ...draftFilters, table: event.target.value })}
              placeholder="public.teams"
              value={draftFilters.table}
            />
          </label>
          <label>
            <span>Record ID</span>
            <input
              onChange={(event) => setDraftFilters({ ...draftFilters, recordId: event.target.value })}
              placeholder="UUID"
              value={draftFilters.recordId}
            />
          </label>
          <label>
            <span>Actor</span>
            <input
              onChange={(event) => setDraftFilters({ ...draftFilters, actor: event.target.value })}
              placeholder="Nickname or profile UUID"
              value={draftFilters.actor}
            />
          </label>
          <label>
            <span>Action</span>
            <select
              onChange={(event) =>
                setDraftFilters({
                  ...draftFilters,
                  action: event.target.value as AuditFilterForm["action"]
                })}
              value={draftFilters.action}
            >
              <option value="">All actions</option>
              <option value="insert">Insert</option>
              <option value="update">Update</option>
              <option value="delete">Delete</option>
            </select>
          </label>
          <label>
            <span>From</span>
            <input
              onChange={(event) => setDraftFilters({ ...draftFilters, from: event.target.value })}
              type="datetime-local"
              value={draftFilters.from}
            />
          </label>
          <label>
            <span>To</span>
            <input
              onChange={(event) => setDraftFilters({ ...draftFilters, to: event.target.value })}
              type="datetime-local"
              value={draftFilters.to}
            />
          </label>
        </div>

        <div className="admin-audit-filter-actions">
          <button className="button ops-button-primary" type="submit">
            <Search size={16} />
            Apply filters
          </button>
          <button className="button ops-button-secondary" onClick={resetFilters} type="button">
            <RotateCcw size={16} />
            Reset
          </button>
        </div>
      </form>

      {error ? (
        <section className="admin-audit-state admin-audit-error ops-panel">
          <AlertTriangle size={24} />
          <div>
            <p className="ops-label">Audit API unavailable</p>
            <h2>Audit logs could not be loaded</h2>
            <p>{error.message}</p>
            <button className="button ops-button-secondary" onClick={retry} type="button">
              <RefreshCw size={15} />
              Retry
            </button>
          </div>
        </section>
      ) : (
        <AuditTable isLoading={isLoading} result={result} />
      )}

      {!error ? (
        <AuditPagination
          isLoading={isLoading}
          onNext={() => setPage((value) => value + 1)}
          onPrevious={() => setPage((value) => Math.max(value - 1, 0))}
          onSizeChange={(value) => {
            setPageSize(value);
            setPage(0);
          }}
          pageSize={pageSize}
          result={result}
        />
      ) : null}
    </div>
  );
}

function AuditHero() {
  return (
    <section className="admin-audit-hero ops-panel">
      <div>
        <p className="ops-label">DotaOps administration</p>
        <h1>Admin Audit Trail</h1>
        <p>Review sanitized operational changes across tournament, team, and match systems.</p>
      </div>
      <div className="admin-audit-hero-status">
        <ShieldCheck size={20} />
        <span>
          <small>Visibility</small>
          <strong>Admin only</strong>
        </span>
      </div>
    </section>
  );
}

function AuditTable({
  isLoading,
  result
}: {
  isLoading: boolean;
  result: AdminAuditPage | null;
}) {
  if (isLoading) {
    return (
      <section aria-live="polite" className="admin-audit-state ops-panel" role="status">
        <RefreshCw className="admin-audit-spin" size={22} />
        <div>
          <p className="ops-label">Loading records</p>
          <h2>Reading sanitized audit logs</h2>
        </div>
      </section>
    );
  }

  if (!result || result.items.length === 0) {
    return (
      <section className="admin-audit-state ops-panel">
        <ShieldCheck size={24} />
        <div>
          <p className="ops-label">No records found</p>
          <h2>Audit trail is empty</h2>
          <p>No audit events match the current filters.</p>
        </div>
      </section>
    );
  }

  return (
    <section className="admin-audit-table-panel ops-panel">
      <div className="admin-audit-section-heading">
        <div>
          <p className="ops-label">Sanitized event stream</p>
          <h2>Audit records</h2>
        </div>
        <strong>{result.page.totalElements.toLocaleString("en-US")} records</strong>
      </div>
      <div className="admin-audit-table-wrap">
        <table className="admin-audit-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Table</th>
              <th>Record ID</th>
              <th>Summary</th>
              <th>Changed fields</th>
            </tr>
          </thead>
          <tbody>
            {result.items.map((item) => (
              <AuditRow item={item} key={item.id} />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function AuditRow({ item }: { item: AdminAuditLogItem }) {
  return (
    <tr>
      <td className="ops-mono">{formatDateTime(item.createdAt)}</td>
      <td>
        <strong>{item.actor.nickname ?? "System"}</strong>
      </td>
      <td>
        <span className={`admin-audit-action admin-audit-action-${item.action}`}>
          {actionLabel(item.action)}
        </span>
      </td>
      <td className="ops-mono">{item.table}</td>
      <td className="ops-mono admin-audit-record-id">{item.recordId ?? "N/A"}</td>
      <td>{item.summary}</td>
      <td>
        <div className="admin-audit-field-list">
          {item.changedFields.length > 0 ? (
            item.changedFields.map((field) => <span key={field}>{field}</span>)
          ) : (
            <em>No safe field changes</em>
          )}
        </div>
      </td>
    </tr>
  );
}

function AuditPagination({
  isLoading,
  onNext,
  onPrevious,
  onSizeChange,
  pageSize,
  result
}: {
  isLoading: boolean;
  onNext: () => void;
  onPrevious: () => void;
  onSizeChange: (value: number) => void;
  pageSize: number;
  result: AdminAuditPage | null;
}) {
  return (
    <section className="admin-audit-pagination ops-panel">
      <button
        className="button ops-button-secondary"
        disabled={isLoading || !result?.page.hasPrevious}
        onClick={onPrevious}
        type="button"
      >
        <ChevronLeft size={16} />
        Previous
      </button>
      <strong className="ops-mono">{displayPage(result)}</strong>
      <label>
        <span>Rows</span>
        <select
          disabled={isLoading}
          onChange={(event) => onSizeChange(Number(event.target.value))}
          value={pageSize}
        >
          {PAGE_SIZES.map((size) => (
            <option key={size} value={size}>{size}</option>
          ))}
        </select>
      </label>
      <button
        className="button ops-button-secondary"
        disabled={isLoading || !result?.page.hasNext}
        onClick={onNext}
        type="button"
      >
        Next
        <ChevronRight size={16} />
      </button>
    </section>
  );
}
