"use client";

import {
  AlertTriangle,
  Archive,
  ArrowLeft,
  CalendarDays,
  Check,
  CheckCircle2,
  ClipboardList,
  DatabaseZap,
  Edit3,
  Eye,
  FileText,
  Globe2,
  Lock,
  Plus,
  RadioTower,
  Rocket,
  Save,
  ShieldAlert,
  ShieldCheck,
  Trophy,
  UserCog,
  UserRoundCheck,
  UsersRound,
  X
} from "lucide-react";
import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

import { MatchImportPanel } from "@/components/match-import-panel";
import { OrganizerBracketManagementPanel } from "@/components/organizer-bracket-management-panel";
import { OrganizerGroupManagementPanel } from "@/components/organizer-group-management-panel";
import { OrganizerMatchResultsPanel } from "@/components/organizer-match-results-panel";
import { OrganizerWorkspaceLoading } from "@/components/organizer-workspace-loading";
import { useTournamentLiveRefresh } from "@/hooks/use-tournament-live-refresh";
import { ApiRequestError, type ApiFieldError } from "@/lib/api";
import { getCurrentUserProfile, type CurrentUserProfile, type ProfileRole } from "@/lib/auth";
import {
  listOrganizerTournamentMatches,
  type OrganizerMatch
} from "@/lib/organizer-match-data";
import {
  archiveOrganizerTournament,
  createOrganizerTournament,
  getOrganizerTournament,
  listOrganizerTournaments,
  publishOrganizerTournament,
  updateOrganizerTournament,
  type OrganizerTournament,
  type OrganizerTournamentFormat,
  type OrganizerTournamentPayload,
  type OrganizerTournamentStatus
} from "@/lib/organizer-tournament-data";
import {
  checkInTournamentRegistration,
  listOrganizerTournamentRegistrations,
  reviewTournamentRegistration,
  type RegistrationStatus,
  type TournamentRegistration
} from "@/lib/tournament-registration-data";
import { classNames } from "@/lib/utils";

type OrganizerView = "dashboard" | "form" | "detail";
type TournamentDetailView =
  | "overview"
  | "registrations"
  | "groups"
  | "bracket"
  | "matches"
  | "import"
  | "staff"
  | "match-controls";
type FormMode = "create" | "edit";
type LoadState = "loading" | "ready" | "login" | "permission" | "error";

interface OrganizerPanelError {
  errors: ApiFieldError[];
  message: string;
  status: number | null;
}

interface TournamentFormState {
  allowSubstitutes: boolean;
  bestOf: string;
  checkInClosesAt: string;
  checkInEnabled: boolean;
  checkInOpensAt: string;
  description: string;
  endsAt: string;
  format: OrganizerTournamentFormat;
  maxTeams: string;
  minTeams: string;
  prizePool: string;
  registrationClosesAt: string;
  registrationOpensAt: string;
  rules: string;
  slug: string;
  startsAt: string;
  teamSize: string;
  timezone: string;
  title: string;
}

const formatOptions: Array<{ label: string; value: OrganizerTournamentFormat }> = [
  { label: "Single Elimination", value: "single_elimination" },
  { label: "Double Elimination / Playoff", value: "best_of_three_playoff" },
  { label: "Round Robin", value: "round_robin" },
  { label: "Group Stage + Playoff", value: "groups_playoff" }
];

const tournamentDetailNavigation: Array<{ label: string; view: TournamentDetailView }> = [
  { label: "Overview", view: "overview" },
  { label: "Registrations", view: "registrations" },
  { label: "Groups", view: "groups" },
  { label: "Bracket", view: "bracket" },
  { label: "Matches", view: "matches" },
  { label: "Import", view: "import" },
  { label: "Staff", view: "staff" },
  { label: "Match Controls", view: "match-controls" }
];

function addLocalDays(value: Date, days: number) {
  const result = new Date(value);
  result.setDate(result.getDate() + days);
  return result;
}

function emptyForm(now = new Date()): TournamentFormState {
  const startsAt = addLocalDays(now, 7);

  return {
    allowSubstitutes: true,
    bestOf: "3",
    checkInClosesAt: "",
    checkInEnabled: false,
    checkInOpensAt: "",
    description: "DotaOps tournament",
    endsAt: toLocalInput(addLocalDays(startsAt, 1).toISOString()),
    format: "single_elimination",
    maxTeams: "8",
    minTeams: "2",
    prizePool: "",
    registrationClosesAt: toLocalInput(addLocalDays(startsAt, -1).toISOString()),
    registrationOpensAt: toLocalInput(now.toISOString()),
    rules: "standard DotaOps ruleset",
    slug: "",
    startsAt: toLocalInput(startsAt.toISOString()),
    teamSize: "5",
    timezone: "UTC",
    title: ""
  };
}

function toLocalInput(value: string | null) {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60_000);

  return local.toISOString().slice(0, 16);
}

function fromLocalInput(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function formFromTournament(tournament: OrganizerTournament): TournamentFormState {
  return {
    allowSubstitutes: tournament.settings.allowSubstitutes,
    bestOf: String(tournament.settings.bestOf),
    checkInClosesAt: toLocalInput(tournament.checkInClosesAt),
    checkInEnabled: tournament.settings.checkInEnabled,
    checkInOpensAt: toLocalInput(tournament.checkInOpensAt),
    description: tournament.description ?? "",
    endsAt: toLocalInput(tournament.endsAt),
    format: tournament.format,
    maxTeams: String(tournament.maxTeams),
    minTeams: String(tournament.settings.minTeams),
    prizePool: tournament.prizePool ?? "",
    registrationClosesAt: toLocalInput(tournament.registrationClosesAt),
    registrationOpensAt: toLocalInput(tournament.registrationOpensAt),
    rules: tournament.rules ?? "",
    slug: tournament.slug,
    startsAt: toLocalInput(tournament.startsAt),
    teamSize: String(tournament.settings.teamSize),
    timezone: tournament.timezone,
    title: tournament.title
  };
}

function nullableText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function nullableNumber(value: string) {
  if (!value.trim()) {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function payloadFromForm(form: TournamentFormState): OrganizerTournamentPayload {
  const maxTeams = nullableNumber(form.maxTeams) ?? 8;

  return {
    checkInClosesAt: fromLocalInput(form.checkInClosesAt),
    checkInOpensAt: fromLocalInput(form.checkInOpensAt),
    description: nullableText(form.description),
    endsAt: fromLocalInput(form.endsAt),
    format: form.format,
    maxTeams,
    prizePool: nullableText(form.prizePool),
    registrationClosesAt: fromLocalInput(form.registrationClosesAt),
    registrationOpensAt: fromLocalInput(form.registrationOpensAt),
    rules: nullableText(form.rules),
    settings: {
      allowSubstitutes: form.allowSubstitutes,
      bestOf: nullableNumber(form.bestOf) ?? 3,
      checkInEnabled: form.checkInEnabled,
      format: form.format,
      maxTeams,
      minTeams: nullableNumber(form.minTeams) ?? 2,
      teamSize: nullableNumber(form.teamSize) ?? 5
    },
    slug: nullableText(form.slug),
    startsAt: fromLocalInput(form.startsAt),
    timezone: nullableText(form.timezone),
    title: form.title.trim()
  };
}

function statusLabel(status: string) {
  return status
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function formatLabel(format: OrganizerTournamentFormat | string) {
  return formatOptions.find((option) => option.value === format)?.label ?? statusLabel(format);
}

function formatDate(value: string | null) {
  if (!value) {
    return "Not scheduled";
  }

  return new Intl.DateTimeFormat("en", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function statusClass(status: string) {
  return `org-tournament-status-${status.replace(/_/g, "-")}`;
}

function canPublish(status: OrganizerTournamentStatus) {
  return status === "draft" || status === "registration" || status === "published";
}

function canArchive(status: OrganizerTournamentStatus) {
  return status !== "archived";
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Request failed.";
}

function panelError(error: unknown, fallback: string): OrganizerPanelError {
  if (error instanceof ApiRequestError) {
    const detail =
      error.status === 403
        ? "Permission denied. Your account is not allowed to access this tournament operation."
        : error.status === 404
          ? "The requested tournament or match resource was not found."
          : error.message || fallback;

    return {
      errors: error.errors,
      message: detail,
      status: error.status
    };
  }

  return {
    errors: [],
    message: errorMessage(error) || fallback,
    status: null
  };
}

function fieldError(errors: ApiFieldError[], field: string) {
  return errors.find((error) => error.field === field || error.field?.endsWith(`.${field}`))?.message ?? null;
}

function dashboardCounts(tournaments: OrganizerTournament[]) {
  return {
    archived: tournaments.filter((tournament) => tournament.status === "archived").length,
    drafts: tournaments.filter((tournament) => tournament.status === "draft").length,
    finished: tournaments.filter((tournament) => tournament.status === "finished").length,
    live: tournaments.filter((tournament) => tournament.status === "live").length,
    published: tournaments.filter((tournament) => tournament.status === "published" || tournament.status === "registration").length
  };
}

function registrationCounts(registrations: TournamentRegistration[]) {
  return {
    approved: registrations.filter(
      (registration) => registration.status === "approved" && registration.displayStatus !== "checked-in"
    ).length,
    checkedIn: registrations.filter((registration) => registration.displayStatus === "checked-in").length,
    pending: registrations.filter((registration) => registration.status === "pending").length,
    rejected: registrations.filter((registration) => registration.status === "rejected").length,
    waitlisted: registrations.filter((registration) => registration.status === "waitlisted").length
  };
}

function canAccessOrganizer(role?: ProfileRole | null) {
  return role === "organizer" || role === "admin";
}

function detailViewFromParam(view?: string): TournamentDetailView {
  switch (view) {
    case "registrations":
    case "groups":
    case "bracket":
    case "matches":
    case "import":
    case "staff":
    case "match-controls":
    case "overview":
      return view;
    default:
      return "overview";
  }
}

function supportsGroupsView(tournament: OrganizerTournament) {
  return tournament.format === "groups_playoff";
}

function detailViewForTournament(
  tournament: OrganizerTournament,
  view: TournamentDetailView
): TournamentDetailView {
  return view === "groups" && !supportsGroupsView(tournament) ? "overview" : view;
}

function organizerDetailHref(tournament: OrganizerTournament, view: TournamentDetailView) {
  return `/organizator?tournamentId=${encodeURIComponent(tournament.id)}&slug=${encodeURIComponent(tournament.slug)}&view=${view}`;
}

function updateOrganizerDetailHistory(
  tournament: OrganizerTournament,
  view: TournamentDetailView,
  mode: "push" | "replace" = "push"
) {
  if (typeof window === "undefined") {
    return;
  }

  const href = organizerDetailHref(tournament, view);

  if (mode === "replace") {
    window.history.replaceState(null, "", href);
    return;
  }

  window.history.pushState(null, "", href);
}

function updateOrganizerDashboardHistory(mode: "push" | "replace" = "push") {
  if (typeof window === "undefined") {
    return;
  }

  if (mode === "replace") {
    window.history.replaceState(null, "", "/organizator");
    return;
  }

  window.history.pushState(null, "", "/organizator");
}

export function OrganizerCommandPage({
  initialSlug,
  initialTournamentId,
  initialView
}: {
  initialSlug?: string;
  initialTournamentId?: string;
  initialView?: string;
}) {
  const [actionError, setActionError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [form, setForm] = useState<TournamentFormState>(emptyForm);
  const [formMode, setFormMode] = useState<FormMode>("create");
  const [isMutating, setIsMutating] = useState(false);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [matchFlowError, setMatchFlowError] = useState<OrganizerPanelError | null>(null);
  const [organizerMatches, setOrganizerMatches] = useState<OrganizerMatch[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [currentProfile, setCurrentProfile] = useState<CurrentUserProfile | null>(null);
  const [detailView, setDetailView] = useState<TournamentDetailView>(() => detailViewFromParam(initialView));
  const [registrations, setRegistrations] = useState<TournamentRegistration[]>([]);
  const [selectedTournament, setSelectedTournament] = useState<OrganizerTournament | null>(null);
  const [tournaments, setTournaments] = useState<OrganizerTournament[]>([]);
  const [view, setView] = useState<OrganizerView>("dashboard");

  const loadTournaments = useCallback(async () => {
    setLoadState("loading");
    setActionError(null);

    try {
      const nextTournaments = await listOrganizerTournaments();
      setTournaments(nextTournaments);
      setLoadState("ready");
      return nextTournaments;
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        setLoadState("login");
        setActionError("Login session expired. Please log in again.");
        return [];
      }

      if (error instanceof ApiRequestError && error.status === 403) {
        setLoadState("permission");
        setActionError("Organizer access is required for this tournament interface.");
        return [];
      }

      setLoadState("error");
      setActionError(errorMessage(error));
      return [];
    }
  }, []);

  const loadOrganizerMatchFlow = useCallback(async (tournamentId: string) => {
    setMatchFlowError(null);

    try {
      const nextMatches = await listOrganizerTournamentMatches(tournamentId);
      setOrganizerMatches(nextMatches);
    } catch (error) {
      setOrganizerMatches([]);
      setMatchFlowError(panelError(error, "Organizer match control API is unavailable."));
    }
  }, []);

  const loadOrganizerAccess = useCallback(async () => {
    setLoadState("loading");
    setActionError(null);
    setFieldErrors([]);
    setNotice(null);

    try {
      const profile = await getCurrentUserProfile();
      setCurrentProfile(profile);

      if (!profile) {
        setTournaments([]);
        setOrganizerMatches([]);
        setLoadState("login");
        setActionError("Login is required before opening organizer tournament controls.");
        return;
      }

      if (!canAccessOrganizer(profile.role)) {
        setTournaments([]);
        setOrganizerMatches([]);
        setLoadState("permission");
        setActionError("This section is only available to tournament organizers.");
        return;
      }

      const nextTournaments = await listOrganizerTournaments();
      setTournaments(nextTournaments);
      setLoadState("ready");

      if (initialTournamentId || initialSlug) {
        setDetailView(detailViewFromParam(initialView));

        const matchedTournament = nextTournaments.find(
          (tournament) =>
            (initialTournamentId && tournament.id === initialTournamentId) ||
            (initialSlug && tournament.slug === initialSlug)
        );

        if (!matchedTournament) {
          setActionError("Tournament not found in organizer workspace.");
          setView("dashboard");
          return;
        }

        const [tournament, registrationList] = await Promise.all([
          getOrganizerTournament(matchedTournament.id),
          listOrganizerTournamentRegistrations(matchedTournament.id)
        ]);
        const requestedDetailView = detailViewFromParam(initialView);
        const resolvedDetailView = detailViewForTournament(tournament, requestedDetailView);

        if (resolvedDetailView !== requestedDetailView) {
          updateOrganizerDetailHistory(tournament, resolvedDetailView, "replace");
        }
        setSelectedTournament(tournament);
        setDetailView(resolvedDetailView);
        setRegistrations(registrationList);
        await loadOrganizerMatchFlow(tournament.id);
        setView("detail");
      }
    } catch (error) {
      setTournaments([]);
      setOrganizerMatches([]);

      if (error instanceof ApiRequestError && error.status === 401) {
        setLoadState("login");
        setActionError("Login session expired. Please log in again.");
        return;
      }

      if (error instanceof ApiRequestError && error.status === 403) {
        setLoadState("permission");
        setActionError("Organizer access is required for this tournament interface.");
        return;
      }

      setLoadState("error");
      setActionError(errorMessage(error));
    }
  }, [initialSlug, initialTournamentId, initialView, loadOrganizerMatchFlow]);

  const loadDetail = useCallback(async (tournamentId: string, options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setIsMutating(true);
      setActionError(null);
      setFieldErrors([]);
      setMatchFlowError(null);
    }

    try {
      const [tournament, registrationList] = await Promise.all([
        getOrganizerTournament(tournamentId),
        listOrganizerTournamentRegistrations(tournamentId)
      ]);

      setSelectedTournament(tournament);
      setDetailView((currentDetailView) => detailViewForTournament(tournament, currentDetailView));
      setRegistrations(registrationList);
      await loadOrganizerMatchFlow(tournamentId);
      setView("detail");
    } catch (error) {
      if (!options?.silent) {
        setActionError(errorMessage(error));
      }
      throw error;
    } finally {
      if (!options?.silent) {
        setIsMutating(false);
      }
    }
  }, [loadOrganizerMatchFlow]);

  const refreshSelectedDetail = useCallback(async () => {
    if (!selectedTournament) {
      return;
    }

    await loadDetail(selectedTournament.id, { silent: true });
  }, [loadDetail, selectedTournament]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadOrganizerAccess(), 0);

    return () => window.clearTimeout(timeout);
  }, [loadOrganizerAccess]);

  useEffect(() => {
    function handleBrowserNavigation() {
      const params = new URLSearchParams(window.location.search);
      const nextTournamentId = params.get("tournamentId");
      const nextSlug = params.get("slug");
      const nextView = detailViewFromParam(params.get("view") ?? undefined);

      if (!nextTournamentId && !nextSlug) {
        setDetailView(nextView);
        setView("dashboard");
        return;
      }

      if (
        selectedTournament &&
        (selectedTournament.id === nextTournamentId || selectedTournament.slug === nextSlug)
      ) {
        const resolvedDetailView = detailViewForTournament(selectedTournament, nextView);

        if (resolvedDetailView !== nextView) {
          updateOrganizerDetailHistory(selectedTournament, resolvedDetailView, "replace");
        }
        setDetailView(resolvedDetailView);
        setView("detail");
        return;
      }

      const targetTournament = tournaments.find(
        (tournament) =>
          (nextTournamentId && tournament.id === nextTournamentId) ||
          (nextSlug && tournament.slug === nextSlug)
      );

      if (targetTournament) {
        const resolvedDetailView = detailViewForTournament(targetTournament, nextView);

        if (resolvedDetailView !== nextView) {
          updateOrganizerDetailHistory(targetTournament, resolvedDetailView, "replace");
        }
        setDetailView(resolvedDetailView);
        void loadDetail(targetTournament.id, { silent: true })
          .catch((error) => {
            setActionError(errorMessage(error));
            setView("dashboard");
          });
      }
    }

    window.addEventListener("popstate", handleBrowserNavigation);

    return () => window.removeEventListener("popstate", handleBrowserNavigation);
  }, [loadDetail, selectedTournament, tournaments]);

  const counts = useMemo(() => dashboardCounts(tournaments), [tournaments]);
  useTournamentLiveRefresh({
    enabled: view === "detail" && Boolean(selectedTournament),
    hiddenIntervalMs: 60_000,
    intervalMs: 15_000,
    label: "organizer tournament detail",
    onRefresh: refreshSelectedDetail
  });

  function clearMessages() {
    setActionError(null);
    setFieldErrors([]);
    setNotice(null);
  }

  function openCreate() {
    clearMessages();
    setForm(emptyForm());
    setFormMode("create");
    setSelectedTournament(null);
    setView("form");
  }

  function openEdit(tournament: OrganizerTournament) {
    clearMessages();
    setForm(formFromTournament(tournament));
    setFormMode("edit");
    setSelectedTournament(tournament);
    setView("form");
  }

  async function openDetail(tournament: OrganizerTournament) {
    clearMessages();
    setDetailView("overview");
    setSelectedTournament(tournament);
    setRegistrations([]);
    setOrganizerMatches([]);
    setView("detail");
    updateOrganizerDetailHistory(tournament, "overview");

    try {
      await loadDetail(tournament.id);
    } catch {
      updateOrganizerDashboardHistory("replace");
      setView("dashboard");
    }
  }

  function closeDetail() {
    clearMessages();
    setView("dashboard");
    updateOrganizerDashboardHistory();
  }

  function changeDetailView(nextView: TournamentDetailView) {
    if (!selectedTournament || nextView === detailView) {
      return;
    }

    const resolvedDetailView = detailViewForTournament(selectedTournament, nextView);

    if (resolvedDetailView !== nextView) {
      return;
    }

    clearMessages();
    setDetailView(resolvedDetailView);
    updateOrganizerDetailHistory(selectedTournament, resolvedDetailView);
  }

  function updateForm<K extends keyof TournamentFormState>(key: K, value: TournamentFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function handleApiError(error: unknown, fallback: string) {
    if (error instanceof ApiRequestError) {
      setFieldErrors(error.errors);
      setActionError(error.message || fallback);
      return;
    }

    setActionError(errorMessage(error) || fallback);
  }

  async function saveTournament(options?: { publishAfterSave?: boolean }) {
    clearMessages();
    setIsMutating(true);

    try {
      const payload = payloadFromForm(form);
      const saved =
        formMode === "create"
          ? await createOrganizerTournament(payload)
          : await updateOrganizerTournament(selectedTournament?.id ?? "", payload);
      const finalTournament = options?.publishAfterSave
        ? await publishOrganizerTournament(saved.id)
        : saved;

      setSelectedTournament(finalTournament);
      setForm(formFromTournament(finalTournament));
      setNotice(options?.publishAfterSave ? "Tournament saved and published." : "Tournament saved successfully.");
      await loadTournaments();
      setView(options?.publishAfterSave ? "detail" : "form");

      if (options?.publishAfterSave) {
        await loadDetail(finalTournament.id);
      }
    } catch (error) {
      handleApiError(error, "Tournament could not be saved.");
    } finally {
      setIsMutating(false);
    }
  }

  async function mutateTournament(
    tournament: OrganizerTournament,
    action: "archive" | "publish"
  ) {
    clearMessages();
    setIsMutating(true);

    try {
      const updated = action === "publish"
        ? await publishOrganizerTournament(tournament.id)
        : await archiveOrganizerTournament(tournament.id);
      setSelectedTournament(updated);
      setNotice(action === "publish" ? "Tournament published successfully." : "Tournament archived successfully.");
      await loadTournaments();

      if (view === "detail") {
        await loadDetail(updated.id);
      }
    } catch (error) {
      handleApiError(error, action === "publish" ? "Tournament could not be published." : "Tournament could not be archived.");
    } finally {
      setIsMutating(false);
    }
  }

  async function reviewRegistration(
    registration: TournamentRegistration,
    action: "approve" | "check-in" | "reject" | "waitlist"
  ) {
    if (!selectedTournament) {
      return;
    }

    clearMessages();
    setIsMutating(true);

    try {
      if (action === "check-in") {
        await checkInTournamentRegistration(selectedTournament.id, registration.id);
      } else {
        await reviewTournamentRegistration(selectedTournament.id, registration.id, action);
      }

      setNotice(
        action === "approve"
          ? "Registration approved."
          : action === "check-in"
            ? "Registration checked in."
          : action === "reject"
            ? "Registration rejected."
            : "Registration waitlisted."
      );
      setRegistrations(await listOrganizerTournamentRegistrations(selectedTournament.id));
      setSelectedTournament(await getOrganizerTournament(selectedTournament.id));
      await loadTournaments();
    } catch (error) {
      handleApiError(error, "Registration review action failed.");
    } finally {
      setIsMutating(false);
    }
  }

  if (loadState === "loading") {
    return <OrganizerWorkspaceLoading />;
  }

  if (loadState === "login" || loadState === "permission" || loadState === "error") {
    return (
      <section className="org-tournament-state ops-panel">
        <p className="ops-label">Organizer tournament access</p>
        <h1>{loadState === "login" ? "Login required" : loadState === "permission" ? "Organizer access required" : "Organizer API unavailable"}</h1>
        <p>{actionError}</p>
        <div>
          {loadState === "login" ? (
            <Link className="button ops-button-primary" href="/login">
              Login
            </Link>
          ) : null}
          {loadState === "permission" ? (
            <Link className="button ops-button-primary" href="/dashboard">
              Back to Dashboard
            </Link>
          ) : null}
          {loadState === "error" ? (
            <button className="button ops-button-secondary" onClick={loadOrganizerAccess} type="button">
              Retry
            </button>
          ) : null}
          <Link className="button ops-button-secondary" href="/turnirji">
            View Tournaments
          </Link>
        </div>
      </section>
    );
  }

  return (
    <div className="org-tournament-page">
      {notice ? <p className="org-tournament-message org-tournament-notice">{notice}</p> : null}
      {actionError ? <p className="org-tournament-message org-tournament-error">{actionError}</p> : null}

      {view === "dashboard" ? (
        <TournamentDashboard
          counts={counts}
          isMutating={isMutating}
          onArchive={(tournament) => mutateTournament(tournament, "archive")}
          onCreate={openCreate}
          onDetail={(tournament) => void openDetail(tournament)}
          onEdit={openEdit}
          onPublish={(tournament) => mutateTournament(tournament, "publish")}
          tournaments={tournaments}
        />
      ) : null}

      {view === "form" ? (
        <TournamentForm
          fieldErrors={fieldErrors}
          form={form}
          isMutating={isMutating}
          mode={formMode}
          onBack={() => {
            clearMessages();
            setView(selectedTournament ? "detail" : "dashboard");
          }}
          onChange={updateForm}
          onPublish={() => saveTournament({ publishAfterSave: true })}
          onSave={() => saveTournament()}
          selectedTournament={selectedTournament}
        />
      ) : null}

      {view === "detail" && selectedTournament ? (
        <TournamentDetail
          currentProfile={currentProfile}
          detailView={detailView}
          isMutating={isMutating}
          matchFlowError={matchFlowError}
          matches={organizerMatches}
          onArchive={() => mutateTournament(selectedTournament, "archive")}
          onBack={closeDetail}
          onDetailViewChange={changeDetailView}
          onEdit={() => openEdit(selectedTournament)}
          onPublish={() => mutateTournament(selectedTournament, "publish")}
          onRefresh={() => loadDetail(selectedTournament.id)}
          onReviewRegistration={reviewRegistration}
          registrations={registrations}
          tournament={selectedTournament}
        />
      ) : null}
    </div>
  );
}

function TournamentDashboard({
  counts,
  isMutating,
  onArchive,
  onCreate,
  onDetail,
  onEdit,
  onPublish,
  tournaments
}: {
  counts: ReturnType<typeof dashboardCounts>;
  isMutating: boolean;
  onArchive: (tournament: OrganizerTournament) => void;
  onCreate: () => void;
  onDetail: (tournament: OrganizerTournament) => void;
  onEdit: (tournament: OrganizerTournament) => void;
  onPublish: (tournament: OrganizerTournament) => void;
  tournaments: OrganizerTournament[];
}) {
  return (
    <>
      <section className="org-tournament-hero ops-panel">
        <div>
          <p className="ops-label">Tournament Command Center</p>
          <h1>Organizer Tournament Dashboard</h1>
          <p>Manage tournament lifecycle, public visibility, registration pressure, and publish/archive workflows from one protected organizer interface.</p>
        </div>
        <button className="org-tournament-primary" onClick={onCreate} type="button">
          <Plus size={18} />
          Create Tournament
        </button>
      </section>

      <section className="org-tournament-panel ops-panel">
        <div className="org-tournament-panel-title">
          <div>
            <p className="ops-label">Tournament Registry</p>
            <h2>Managed Tournaments</h2>
          </div>
          <span>{tournaments.length} records</span>
        </div>

        {tournaments.length === 0 ? (
          <div className="org-tournament-empty">
            <h3>No organizer tournaments yet</h3>
            <p>Create the first tournament draft to start the FE-04 workflow.</p>
            <button className="org-tournament-primary" onClick={onCreate} type="button">
              <Plus size={18} />
              Create Tournament
            </button>
          </div>
        ) : (
          <div className="org-tournament-table-wrap">
            <table className="org-tournament-table">
              <thead>
                <tr>
                  <th>Tournament</th>
                  <th>Status</th>
                  <th>Format</th>
                  <th>Schedule</th>
                  <th>Teams</th>
                  <th>Visibility</th>
                  <th>Updated</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {tournaments.map((tournament) => (
                  <tr key={tournament.id}>
                    <td>
                      <strong>{tournament.title}</strong>
                      <span>{tournament.slug}</span>
                    </td>
                    <td>
                      <StatusBadge status={tournament.status} />
                    </td>
                    <td>{formatLabel(tournament.format)}</td>
                    <td>{formatDate(tournament.startsAt)}</td>
                    <td>
                      {tournament.registrationsCount}/{tournament.maxTeams}
                    </td>
                    <td>{tournament.publicVisible ? "Public" : "Private"}</td>
                    <td>{formatDate(tournament.updatedAt)}</td>
                    <td>
                      <div className="org-tournament-actions">
                        <button onClick={() => onDetail(tournament)} type="button">
                          <Eye size={15} />
                          Details
                        </button>
                        <button onClick={() => onEdit(tournament)} type="button">
                          <Edit3 size={15} />
                          Edit
                        </button>
                        <button disabled={isMutating || !canPublish(tournament.status)} onClick={() => onPublish(tournament)} type="button">
                          <Rocket size={15} />
                          Publish
                        </button>
                        <button disabled={isMutating || !canArchive(tournament.status)} onClick={() => onArchive(tournament)} type="button">
                          <Archive size={15} />
                          Archive
                        </button>
                        {tournament.slug ? (
                          <Link href={`/turnirji/${tournament.slug}`}>
                            <Globe2 size={15} />
                            Public
                          </Link>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="org-tournament-summary">
        <SummaryCard icon={FileText} label="Drafts" value={counts.drafts} />
        <SummaryCard icon={RadioTower} label="Published" tone="cyan" value={counts.published} />
        <SummaryCard icon={Trophy} label="Live" tone="red" value={counts.live} />
        <SummaryCard icon={ShieldCheck} label="Finished" tone="green" value={counts.finished} />
        <SummaryCard icon={Archive} label="Archived" tone="muted" value={counts.archived} />
      </section>
    </>
  );
}

function TournamentForm({
  fieldErrors,
  form,
  isMutating,
  mode,
  onBack,
  onChange,
  onPublish,
  onSave,
  selectedTournament
}: {
  fieldErrors: ApiFieldError[];
  form: TournamentFormState;
  isMutating: boolean;
  mode: FormMode;
  onBack: () => void;
  onChange: <K extends keyof TournamentFormState>(key: K, value: TournamentFormState[K]) => void;
  onPublish: () => void;
  onSave: () => void;
  selectedTournament: OrganizerTournament | null;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSave();
  }

  return (
    <section className="org-tournament-form-shell">
      <div className="org-tournament-form-hero ops-panel">
        <button onClick={onBack} type="button">
          <ArrowLeft size={16} />
          Back
        </button>
        <div>
          <p className="ops-label">{mode === "create" ? "Systems Deployment" : "Tournament Configuration"}</p>
          <h1>{mode === "create" ? "New Tournament Initialization" : selectedTournament?.title}</h1>
          <p>Configure only backend-supported tournament fields. Unsupported branding, region, and visibility flags are intentionally not sent.</p>
        </div>
        {selectedTournament ? <StatusBadge status={selectedTournament.status} /> : null}
      </div>

      {fieldErrors.length > 0 ? (
        <ValidationPanel errors={fieldErrors} />
      ) : null}

      <form className="org-tournament-form ops-panel" onSubmit={submit}>
        <div className="org-tournament-form-grid">
          <FormField error={fieldError(fieldErrors, "title")} label="Tournament Name">
            <input onChange={(event) => onChange("title", event.target.value)} required value={form.title} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "slug")} label="Slug">
            <input onChange={(event) => onChange("slug", event.target.value)} placeholder="auto-generated-if-empty" value={form.slug} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "format")} label="Format">
            <select onChange={(event) => onChange("format", event.target.value as OrganizerTournamentFormat)} value={form.format}>
              {formatOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </FormField>
          <FormField error={fieldError(fieldErrors, "maxTeams")} label="Max Teams">
            <input max={128} min={2} onChange={(event) => onChange("maxTeams", event.target.value)} type="number" value={form.maxTeams} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "startsAt")} label="Tournament Start">
            <input onChange={(event) => onChange("startsAt", event.target.value)} required type="datetime-local" value={form.startsAt} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "endsAt")} label="Tournament End">
            <input onChange={(event) => onChange("endsAt", event.target.value)} type="datetime-local" value={form.endsAt} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "registrationOpensAt")} label="Registration Opens">
            <input onChange={(event) => onChange("registrationOpensAt", event.target.value)} type="datetime-local" value={form.registrationOpensAt} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "registrationClosesAt")} label="Registration Closes">
            <input onChange={(event) => onChange("registrationClosesAt", event.target.value)} type="datetime-local" value={form.registrationClosesAt} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "timezone")} label="Timezone">
            <input onChange={(event) => onChange("timezone", event.target.value)} value={form.timezone} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "prizePool")} label="Prize Pool / Label">
            <input onChange={(event) => onChange("prizePool", event.target.value)} placeholder="€10,000 / Invite-only" value={form.prizePool} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "description")} label="Description" wide>
            <textarea onChange={(event) => onChange("description", event.target.value)} rows={4} value={form.description} />
          </FormField>
          <FormField error={fieldError(fieldErrors, "rules")} label="Rules / Notes" wide>
            <textarea onChange={(event) => onChange("rules", event.target.value)} rows={5} value={form.rules} />
          </FormField>
        </div>

        <section className="org-tournament-settings-panel">
          <h2>Operational Settings</h2>
          <div className="org-tournament-form-grid">
            <FormField error={fieldError(fieldErrors, "settings.minTeams")} label="Minimum Teams">
              <input min={1} onChange={(event) => onChange("minTeams", event.target.value)} type="number" value={form.minTeams} />
            </FormField>
            <FormField error={fieldError(fieldErrors, "settings.teamSize")} label="Team Size">
              <input min={1} onChange={(event) => onChange("teamSize", event.target.value)} type="number" value={form.teamSize} />
            </FormField>
            <FormField error={fieldError(fieldErrors, "settings.bestOf")} label="Best Of">
              <select onChange={(event) => onChange("bestOf", event.target.value)} value={form.bestOf}>
                <option value="1">Best of 1</option>
                <option value="3">Best of 3</option>
                <option value="5">Best of 5</option>
              </select>
            </FormField>
            <FormField error={fieldError(fieldErrors, "checkInOpensAt")} label="Check-in Opens">
              <input disabled={!form.checkInEnabled} onChange={(event) => onChange("checkInOpensAt", event.target.value)} type="datetime-local" value={form.checkInOpensAt} />
            </FormField>
            <FormField error={fieldError(fieldErrors, "checkInClosesAt")} label="Check-in Closes">
              <input disabled={!form.checkInEnabled} onChange={(event) => onChange("checkInClosesAt", event.target.value)} type="datetime-local" value={form.checkInClosesAt} />
            </FormField>
          </div>
          <div className="org-tournament-toggle-row">
            <label>
              <input checked={form.checkInEnabled} onChange={(event) => onChange("checkInEnabled", event.target.checked)} type="checkbox" />
              Check-in enabled
            </label>
            <label>
              <input checked={form.allowSubstitutes} onChange={(event) => onChange("allowSubstitutes", event.target.checked)} type="checkbox" />
              Allow substitutes
            </label>
          </div>
        </section>

        <div className="org-tournament-form-actions">
          <button className="org-tournament-secondary" onClick={onBack} type="button">
            Cancel
          </button>
          <button className="org-tournament-secondary" disabled={isMutating} onClick={onPublish} type="button">
            <Rocket size={17} />
            Publish Tournament
          </button>
          <button className="org-tournament-primary" disabled={isMutating} type="submit">
            <Save size={17} />
            {mode === "create" ? "Save Draft" : "Save Changes"}
          </button>
        </div>
      </form>
    </section>
  );
}

function TournamentDetail({
  currentProfile,
  detailView,
  isMutating,
  matchFlowError,
  matches,
  onArchive,
  onBack,
  onDetailViewChange,
  onEdit,
  onPublish,
  onRefresh,
  onReviewRegistration,
  registrations,
  tournament
}: {
  currentProfile: CurrentUserProfile | null;
  detailView: TournamentDetailView;
  isMutating: boolean;
  matchFlowError: OrganizerPanelError | null;
  matches: OrganizerMatch[];
  onArchive: () => void;
  onBack: () => void;
  onDetailViewChange: (view: TournamentDetailView) => void;
  onEdit: () => void;
  onPublish: () => void;
  onRefresh: () => void;
  onReviewRegistration: (registration: TournamentRegistration, action: "approve" | "check-in" | "reject" | "waitlist") => void;
  registrations: TournamentRegistration[];
  tournament: OrganizerTournament;
}) {
  const [filter, setFilter] = useState<RegistrationStatus | "all">("all");
  const activeDetailView = detailViewForTournament(tournament, detailView);
  const availableDetailNavigation = tournamentDetailNavigation.filter(
    (item) => item.view !== "groups" || supportsGroupsView(tournament)
  );
  const counts = useMemo(() => registrationCounts(registrations), [registrations]);
  const filteredRegistrations = useMemo(
    () => registrations.filter((registration) => filter === "all" || registration.displayStatus === filter),
    [filter, registrations]
  );
  const hasSidePanel = activeDetailView === "overview" || activeDetailView === "registrations";

  return (
    <>
      <section className="org-tournament-detail-hero ops-panel">
        <button onClick={onBack} type="button">
          <ArrowLeft size={16} />
          Registry
        </button>
        <div>
          <p className="ops-label">Tournament Management Detail</p>
          <h1>{tournament.title}</h1>
          <p>{tournament.description ?? "Tournament description is not set yet."}</p>
        </div>
        <StatusBadge status={tournament.status} />
      </section>

      {tournament.status === "archived" ? (
        <section className="org-tournament-warning ops-panel">
          <AlertTriangle size={18} />
          Archived tournament warning: archived tournaments cannot be edited or published again.
        </section>
      ) : null}

      <nav className="org-tournament-section-shortcuts ops-panel" aria-label="Tournament detail sections">
        {availableDetailNavigation.map((item) => (
          <button
            aria-pressed={activeDetailView === item.view}
            className={classNames(activeDetailView === item.view && "is-active")}
            key={item.view}
            onClick={() => onDetailViewChange(item.view)}
            type="button"
          >
            {item.label}
          </button>
        ))}
      </nav>

      <section className={classNames("org-tournament-detail-grid", !hasSidePanel && "is-full-width")}>
        <main className="org-tournament-detail-main">
          {activeDetailView === "overview" ? (
            <section className="org-tournament-meta-grid" id="overview">
              <MetaCard icon={ClipboardList} label="Format" value={formatLabel(tournament.format)} />
              <MetaCard icon={Globe2} label="Timezone" value={tournament.timezone} />
              <MetaCard icon={CalendarDays} label="Registration Deadline" value={formatDate(tournament.registrationClosesAt)} />
              <MetaCard icon={CalendarDays} label="Date Range" value={`${formatDate(tournament.startsAt)} - ${formatDate(tournament.endsAt)}`} />
              <MetaCard icon={UsersRound} label="Max Teams" value={String(tournament.maxTeams)} />
              <MetaCard icon={Trophy} label="Registered Teams" value={String(tournament.registrationsCount)} />
            </section>
          ) : null}

          {activeDetailView === "registrations" ? (
            <section className="org-tournament-panel ops-panel" id="registration-review">
              <div className="org-tournament-panel-title">
                <div>
                  <p className="ops-label">Registration Review</p>
                  <h2>Team Submissions</h2>
                </div>
                <button className="org-tournament-secondary" disabled={isMutating} onClick={onRefresh} type="button">
                  Refresh
                </button>
              </div>
              <div className="org-registration-review-summary">
                <RegistrationCount label="Total" value={registrations.length} />
                <RegistrationCount label="Pending" value={counts.pending} />
                <RegistrationCount label="Approved" value={counts.approved} />
                <RegistrationCount label="Rejected" value={counts.rejected} />
                <RegistrationCount label="Waitlisted" value={counts.waitlisted} />
                <RegistrationCount label="Checked-in" value={counts.checkedIn} />
              </div>
              <div className="org-registration-filters">
                {(["all", "pending", "approved", "rejected", "waitlisted", "checked-in"] as const).map((status) => (
                  <button
                    className={classNames(filter === status && "is-active")}
                    key={status}
                    onClick={() => setFilter(status)}
                    type="button"
                  >
                    {statusLabel(status)}
                  </button>
                ))}
              </div>
              {registrations.length === 0 ? (
                <p className="org-tournament-muted">No registrations are available for this tournament yet.</p>
              ) : (
                <div className="org-tournament-registration-list">
                  {filteredRegistrations.map((registration) => (
                    <article className="org-tournament-registration-card" key={registration.id}>
                      <div>
                        <strong>{registration.teamName}</strong>
                        <span>
                          {registration.teamTag ?? registration.teamSlug ?? "No team tag"} - Captain: {registration.captainNickname ?? "Unknown"} - Roster {registration.members.length || "?"}
                        </span>
                      </div>
                      <StatusBadge status={registration.displayStatus} />
                      <span>{formatDate(registration.createdAt)}</span>
                      <div className="org-tournament-actions">
                        <button disabled={isMutating || registration.displayStatus === "checked-in" || registration.status === "approved"} onClick={() => onReviewRegistration(registration, "approve")} type="button">
                          <Check size={15} />
                          Approve
                        </button>
                        <button disabled={isMutating || registration.displayStatus === "checked-in" || registration.status === "waitlisted"} onClick={() => onReviewRegistration(registration, "waitlist")} type="button">
                          Waitlist
                        </button>
                        <button disabled={isMutating || registration.displayStatus === "checked-in" || registration.status === "rejected"} onClick={() => onReviewRegistration(registration, "reject")} type="button">
                          <X size={15} />
                          Reject
                        </button>
                        <button disabled={isMutating || registration.status !== "approved" || registration.displayStatus === "checked-in"} onClick={() => onReviewRegistration(registration, "check-in")} type="button">
                          Check-in
                        </button>
                        <button disabled type="button">
                          View Team
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          ) : null}

          {activeDetailView === "groups" ? (
            <OrganizerGroupManagementPanel
              matches={matches}
              registrations={registrations}
              tournament={tournament}
            />
          ) : null}

          {activeDetailView === "bracket" ? (
            <OrganizerBracketManagementPanel
              onRefresh={onRefresh}
              tournament={tournament}
            />
          ) : null}

          {activeDetailView === "matches" ? (
            <OrganizerMatchResultsPanel
              onRefresh={onRefresh}
              tournament={tournament}
            />
          ) : null}

          {activeDetailView === "import" ? (
            <div className="org-tournament-panel organizer-import-shell ops-panel" id="match-import">
              <MatchImportPanel />
            </div>
          ) : null}

          {activeDetailView === "staff" ? (
            <StaffOfficialsPanel
              currentProfile={currentProfile}
              isMutating={isMutating}
              onRefresh={onRefresh}
              tournament={tournament}
            />
          ) : null}

          {activeDetailView === "match-controls" ? (
            <MatchOperationsControlPanel
              error={matchFlowError}
              matches={matches}
              tournament={tournament}
            />
          ) : null}
        </main>

        {hasSidePanel ? (
          <aside className="org-tournament-detail-side">
            {activeDetailView === "overview" ? (
              <section className="org-tournament-panel ops-panel">
                <div className="org-tournament-panel-title">
                  <h2>Quick Actions</h2>
                </div>
                <div className="org-tournament-side-actions">
                  <button className="org-tournament-primary" disabled={tournament.status === "archived"} onClick={onEdit} type="button">
                    <Edit3 size={17} />
                    Edit Tournament
                  </button>
                  <button disabled={isMutating || !canPublish(tournament.status)} onClick={onPublish} type="button">
                    <Rocket size={17} />
                    Publish
                  </button>
                  <button disabled={isMutating || !canArchive(tournament.status)} onClick={onArchive} type="button">
                    <Archive size={17} />
                    Archive
                  </button>
                  <Link href={`/turnirji/${tournament.slug}`}>
                    <Globe2 size={17} />
                    View Public Page
                  </Link>
                </div>
              </section>
            ) : null}

            {activeDetailView === "registrations" ? (
              <section className="org-tournament-panel ops-panel">
                <div className="org-tournament-panel-title">
                  <h2>Registration Commands</h2>
                </div>
                <p className="org-tournament-muted">Export, close registration, and bulk action endpoints are not available yet.</p>
                <div className="org-tournament-side-actions">
                  <button className="org-tournament-secondary" disabled={isMutating} onClick={onRefresh} type="button">
                    Refresh Registrations
                  </button>
                  <button disabled type="button">Export List unavailable</button>
                  <button disabled type="button">Close Registration unavailable</button>
                  <button disabled type="button">Bulk Actions unavailable</button>
                </div>
                <div className="org-tournament-audit-row">
                  <span>Last Update</span>
                  <strong>{formatDate(tournament.updatedAt)}</strong>
                </div>
                <div className="org-tournament-audit-row">
                  <span>Published</span>
                  <strong>{formatDate(tournament.publishedAt)}</strong>
                </div>
              </section>
            ) : null}
          </aside>
        ) : null}
      </section>
    </>
  );
}

interface StaffRow {
  actions: string;
  id: string;
  name: string;
  permissions: string[];
  role: "Owner" | "Organizer";
  scope: string;
  status: string;
  subtitle: string;
}

function staffRowsForTournament(
  tournament: OrganizerTournament,
  currentProfile: CurrentUserProfile | null
): StaffRow[] {
  const rows: StaffRow[] = [];
  const ownerName =
    tournament.organizerNickname ??
    (currentProfile?.profileId && currentProfile.profileId === tournament.organizerProfileId
      ? currentProfile.nickname
      : null) ??
    "Tournament owner / organizer";

  rows.push({
    actions: "Locked in current role model",
    id: tournament.organizerProfileId ?? "tournament-owner",
    name: ownerName,
    permissions: ["Manage registrations", "Schedule matches", "Enter results"],
    role: "Owner",
    scope: "Tournament-wide",
    status: "Active",
    subtitle: tournament.organizerProfileId ? "Primary organizer profile" : "Backend owner fallback"
  });

  if (
    currentProfile?.profileId &&
    currentProfile.profileId !== tournament.organizerProfileId
  ) {
    rows.push({
      actions: "Session-only visibility",
      id: currentProfile.profileId,
      name: currentProfile.nickname,
      permissions: currentProfile.role === "admin"
        ? ["Manage registrations", "Schedule matches", "Enter results", "View only"]
        : ["Organizer workspace"],
      role: "Organizer",
      scope: currentProfile.role === "admin" ? "Admin override" : "Current session",
      status: "Active session",
      subtitle: currentProfile.role === "admin" ? "Admin account" : "Organizer account"
    });
  }

  return rows;
}

function StaffOfficialsPanel({
  currentProfile,
  isMutating,
  onRefresh,
  tournament
}: {
  currentProfile: CurrentUserProfile | null;
  isMutating: boolean;
  onRefresh: () => void;
  tournament: OrganizerTournament;
}) {
  const staffRows = staffRowsForTournament(tournament, currentProfile);
  const organizers = staffRows.filter((row) => row.role === "Owner" || row.role === "Organizer").length;
  const summary = [
    { label: "Operators", tone: "red", value: staffRows.length },
    { label: "Organizer Controls", tone: "cyan", value: organizers },
    { label: "Backend Staff API", tone: "gold", value: 0 },
    { label: "Locked Actions", tone: "muted", value: 5 }
  ];

  return (
    <section className="org-tournament-panel org-staff-panel ops-panel" id="staff-officials">
      <div className="org-tournament-panel-title">
        <div>
          <p className="ops-label">Organizer Operations</p>
          <h2>Organizer Operations Staff</h2>
          <p>
            Organizer/admin currently controls tournament operations. Separate
            referee, analyst, or score reporter account roles are not enabled in
            this project scope.
          </p>
        </div>
        <span className="ops-badge">
          <ShieldAlert size={14} />
          API pending
        </span>
      </div>

      <div className="org-staff-summary-grid">
        {summary.map((item) => (
          <article className={classNames("org-staff-summary-card", `org-staff-tone-${item.tone}`)} key={item.label}>
            <span className="ops-label">{item.label}</span>
            <strong className="ops-data">{String(item.value).padStart(2, "0")}</strong>
          </article>
        ))}
      </div>

      <div className="org-staff-layout">
        <div className="org-staff-table-wrap">
          <table className="org-staff-table">
            <thead>
              <tr>
                <th>Operator</th>
                <th>Workspace role</th>
                <th>Scope</th>
                <th>Organizer controls</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {staffRows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <div className="org-staff-member">
                      <span>
                        <UserRoundCheck size={16} />
                      </span>
                      <div>
                        <strong>{row.name}</strong>
                        <small>{row.subtitle}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <StaffRoleBadge role={row.role} />
                  </td>
                  <td>{row.scope}</td>
                  <td>
                    <div className="org-staff-permissions">
                      {row.permissions.map((permission) => (
                        <span key={permission}>{permission}</span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <span className="org-staff-status">
                      <CheckCircle2 size={14} />
                      {row.status}
                    </span>
                  </td>
                  <td>{row.actions}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <aside className="org-staff-command-rail">
          <button disabled type="button">
            <Plus size={16} />
            Add Staff
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <UserCog size={16} />
            Change Role
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <X size={16} />
            Remove Staff
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <Archive size={16} />
            Audit History
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <ShieldCheck size={16} />
            Permission Rules
            <Lock size={14} />
          </button>
          <button className="org-tournament-secondary" disabled={isMutating} onClick={onRefresh} type="button">
            <RadioTower size={16} />
            Refresh Staff
          </button>
        </aside>
      </div>

      <PanelWarning
        title="Organizer/admin controlled"
        detail="Dedicated staff management is not part of the current role model. Organizer/admin handles these operations, and disabled controls need backend staff endpoints before they can be enabled."
      />
    </section>
  );
}

function StaffRoleBadge({ role }: { role: StaffRow["role"] }) {
  return (
    <span className={classNames("org-staff-role-badge", role === "Owner" ? "is-owner" : "is-organizer")}>
      {role}
    </span>
  );
}

type PermissionState = "allowed" | "restricted" | "backend" | "unavailable";

interface PermissionRow {
  action: string;
  backendRequired: PermissionState;
  currentSupport: PermissionState;
  note: string;
  organizer: PermissionState;
}

const permissionRows: PermissionRow[] = [
  { action: "Schedule match", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer endpoint exists.", organizer: "allowed" },
  { action: "Start match", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer endpoint exists.", organizer: "allowed" },
  { action: "Enter result", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer result endpoint exists.", organizer: "allowed" },
  { action: "Confirm result", backendRequired: "backend", currentSupport: "backend", note: "Approval workflow is not active.", organizer: "backend" },
  { action: "Finish match", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer endpoint exists.", organizer: "allowed" },
  { action: "Cancel match", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer endpoint exists.", organizer: "allowed" },
  { action: "Handle dispute", backendRequired: "backend", currentSupport: "unavailable", note: "Dispute process is outside current scope.", organizer: "unavailable" },
  { action: "Import match data", backendRequired: "unavailable", currentSupport: "allowed", note: "Organizer import flow exists where enabled.", organizer: "allowed" }
];

function MatchOperationsControlPanel({
  error,
  matches,
  tournament
}: {
  error: OrganizerPanelError | null;
  matches: OrganizerMatch[];
  tournament: OrganizerTournament;
}) {
  const selectedMatch = matches[0] ?? null;

  function scrollToFlow() {
    document.getElementById("match-operations-flow")?.scrollIntoView({
      behavior: "smooth",
      block: "start"
    });
  }

  return (
    <section className="org-tournament-panel org-referee-panel ops-panel" id="match-operations-flow">
      <div className="org-tournament-panel-title">
        <div>
          <p className="ops-label">Organizer Match Control Flow</p>
          <h2>Organizer Match Operations</h2>
          <p>
            Only organizer/admin has access to this workspace. Referee or
            analyst-specific accounts are not used in the current implementation.
          </p>
        </div>
        <span className="ops-badge">
          <DatabaseZap size={14} />
          {matches.length} matches
        </span>
      </div>

      <div className="org-referee-grid">
        <div className="org-referee-main">
          {error ? <PanelError error={error} title="Match control API unavailable" /> : null}

          {selectedMatch ? (
            <article className="org-referee-match-card">
              <div>
                <p className="ops-label">Selected match</p>
                <h3>
                  {selectedMatch.teamAName ?? "TBD"} <span>vs</span> {selectedMatch.teamBName ?? "TBD"}
                </h3>
                <p className="ops-mono">
                  Match {selectedMatch.id} / {selectedMatch.stageName ?? tournament.title}
                </p>
              </div>
              <div className="org-referee-match-meta">
                <StatusBadge status={selectedMatch.status} />
                <span className="ops-mono">BO{selectedMatch.bestOf}</span>
                <span className="ops-mono">{selectedMatch.roundName ?? `Round ${selectedMatch.roundNumber || 1}`}</span>
                <strong className="ops-data">
                  {selectedMatch.scoreA}:{selectedMatch.scoreB}
                </strong>
              </div>
            </article>
          ) : !error ? (
            <div className="org-referee-empty">
              <ShieldAlert size={18} />
              <div>
                <strong>No organizer match records loaded</strong>
                <p>The match endpoint returned no records for this tournament yet.</p>
              </div>
            </div>
          ) : null}

          <div className="org-referee-matrix-wrap">
            <table className="org-referee-matrix">
              <thead>
                <tr>
                  <th>Process</th>
                  <th>Organizer/Admin</th>
                  <th>Current Support</th>
                  <th>Backend Required</th>
                  <th>Notes</th>
                </tr>
              </thead>
              <tbody>
                {permissionRows.map((row) => (
                  <tr key={row.action}>
                    <td>{row.action}</td>
                    <td><PermissionStateBadge state={row.organizer} /></td>
                    <td><PermissionStateBadge state={row.currentSupport} /></td>
                    <td><PermissionStateBadge state={row.backendRequired} /></td>
                    <td>{row.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="org-referee-limitations">
            <article>
              <strong>Organizer/admin workspace</strong>
              <p>All active tournament operations are controlled by organizer/admin accounts.</p>
            </article>
            <article>
              <strong>No separate official login</strong>
              <p>Referee, analyst, and score reporter account flows are not part of the current project scope.</p>
            </article>
            <article>
              <strong>Backend-gated controls</strong>
              <p>Unsupported operations stay locked until organizer-focused backend endpoints exist.</p>
            </article>
          </div>
        </div>

        <aside className="org-referee-command-panel">
          <button disabled type="button">
            <Plus size={16} />
            Assign Operator
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <X size={16} />
            Remove Assignment
            <Lock size={14} />
          </button>
          <button disabled={!selectedMatch} onClick={scrollToFlow} type="button">
            <Eye size={16} />
            View Match Flow
          </button>
          <button disabled type="button">
            <Edit3 size={16} />
            Enter Result
            <Lock size={14} />
          </button>
          <button disabled type="button">
            <DatabaseZap size={16} />
            Import Match Data
            <Lock size={14} />
          </button>

          <PanelWarning
            title="Backend status"
            detail="Dedicated official assignment is not part of the current role model. Organizer/admin remains responsible for match operations."
          />
        </aside>
      </div>
    </section>
  );
}

function PermissionStateBadge({ state }: { state: PermissionState }) {
  const labels: Record<PermissionState, string> = {
    allowed: "Organizer controlled",
    backend: "Backend required",
    restricted: "Planned limit",
    unavailable: "Not active"
  };

  return <span className={classNames("org-permission-state", `is-${state}`)}>{labels[state]}</span>;
}

function PanelWarning({ detail, title }: { detail: string; title: string }) {
  return (
    <div className="org-panel-warning">
      <AlertTriangle size={17} />
      <div>
        <strong>{title}</strong>
        <p>{detail}</p>
      </div>
    </div>
  );
}

function PanelError({ error, title }: { error: OrganizerPanelError; title: string }) {
  return (
    <div className="org-panel-error">
      <AlertTriangle size={17} />
      <div>
        <strong>
          {error.status ? `${error.status} ` : null}
          {title}
        </strong>
        <p>{error.message}</p>
        {error.errors.length > 0 ? (
          <ul>
            {error.errors.map((fieldErrorItem, index) => (
              <li key={`${fieldErrorItem.field ?? "request"}-${index}`}>
                <span>{fieldErrorItem.field ?? "request"}</span>
                <em>{fieldErrorItem.message ?? "Invalid value"}</em>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </div>
  );
}

function SummaryCard({
  icon: Icon,
  label,
  tone = "gold",
  value
}: {
  icon: LucideIcon;
  label: string;
  tone?: "cyan" | "gold" | "green" | "muted" | "red";
  value: number;
}) {
  return (
    <article className={classNames("org-tournament-summary-card ops-panel", `org-tournament-tone-${tone}`)}>
      <div>
        <span>{label}</span>
        <Icon size={18} />
      </div>
      <strong>{String(value).padStart(2, "0")}</strong>
    </article>
  );
}

function RegistrationCount({ label, value }: { label: string; value: number }) {
  return (
    <article>
      <span>{label}</span>
      <strong>{String(value).padStart(2, "0")}</strong>
    </article>
  );
}

function MetaCard({
  icon: Icon,
  label,
  value
}: {
  icon: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <article className="org-tournament-meta-card ops-panel">
      <Icon size={18} />
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function StatusBadge({ status }: { status: string }) {
  return <span className={classNames("org-tournament-status", statusClass(status))}>{statusLabel(status)}</span>;
}

function ValidationPanel({ errors }: { errors: ApiFieldError[] }) {
  return (
    <section className="org-tournament-validation ops-panel">
      <AlertTriangle size={18} />
      <div>
        <h2>Backend validation failed</h2>
        <ul>
          {errors.map((error, index) => (
            <li key={`${error.field ?? "general"}-${index}`}>
              <strong>{error.field ?? "request"}</strong>
              <span>{error.message ?? "Invalid value"}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function FormField({
  children,
  error,
  label,
  wide = false
}: {
  children: ReactNode;
  error: string | null;
  label: string;
  wide?: boolean;
}) {
  return (
    <label className={classNames("org-tournament-field", wide && "org-tournament-field-wide")}>
      <span>{label}</span>
      {children}
      {error ? <em>{error}</em> : null}
    </label>
  );
}
