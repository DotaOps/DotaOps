import {
  BarChart3,
  DatabaseZap,
  FileInput,
  Trophy,
  UserRound,
  UsersRound
} from "lucide-react";

import type {
  OrganizerDashboardData,
  PlayerDashboardData
} from "@/lib/role-dashboard-data";

export type ProductionDashboardRole = "admin" | "organizer" | "player" | "public";

const unavailable = "—";

export const playerProductionDashboardData: PlayerDashboardData = {
  topbar: {
    activeTeam: "No active team data",
    rank: "Not available yet",
    primaryAction: {
      href: "/ekipe",
      icon: UsersRound,
      label: "Open My Team"
    }
  },
  hero: {
    description:
      "Review your team workspace, browse tournaments, and open public analytics. Personal match metrics will appear when imported match data is available.",
    label: "Player workspace",
    name: "Player Hub",
    recentForm: [],
    role: "Player"
  },
  performance: [
    { detail: "No data yet", featured: true, label: "KDA Ratio", tone: "cyan", value: unavailable },
    { detail: "No data yet", featured: true, label: "Win Rate", tone: "red", value: unavailable },
    { detail: "No data yet", featured: true, label: "Matches", tone: "muted", value: unavailable },
    { detail: "Not available yet", featured: true, label: "Rank Tier", tone: "gold", value: unavailable },
    { detail: "No data yet", label: "AVG GPM", tone: "green", value: unavailable },
    { detail: "No data yet", label: "AVG XPM", tone: "muted", value: unavailable },
    { detail: "No data yet", label: "Last Hits @10", tone: "red", value: unavailable },
    { detail: "No data yet", label: "Kill Participation", tone: "green", value: unavailable }
  ],
  roster: [],
  matchLog: []
};

const organizerProductionDashboardData: OrganizerDashboardData = {
  topbar: {
    activeTournament: "No active tournament selected",
    primaryAction: {
      href: "/organizator",
      icon: Trophy,
      label: "Organizer Panel"
    }
  },
  hero: {
    description:
      "Create and manage tournaments from the organizer workspace. Tournament activity will appear after registrations, schedules, and match imports are available.",
    integrity: "Not available yet",
    registeredTeams: unavailable,
    stage: "Organizer workspace",
    status: "No active operations",
    title: "Tournament Operations"
  },
  kpis: [
    { detail: "No data yet", icon: Trophy, label: "Active Tournaments", tone: "red", value: unavailable },
    { detail: "No data yet", icon: UsersRound, label: "Registered Teams", tone: "gold", value: unavailable },
    { detail: "No data yet", icon: FileInput, label: "Pending Approvals", tone: "red", value: unavailable },
    { detail: "No data yet", icon: DatabaseZap, label: "Live Matches", tone: "cyan", value: unavailable },
    { detail: "No data yet", icon: BarChart3, label: "Match Data Ready", tone: "red", value: unavailable }
  ],
  matrix: [],
  quickActions: [
    { href: "/organizator", icon: Trophy, label: "Manage Tournaments", tone: "red" },
    { href: "/turnirji", icon: UsersRound, label: "View Public Tournaments", tone: "gold" },
    { href: "/analitika", icon: BarChart3, label: "Analytics", tone: "cyan" },
    { href: "/profile", icon: UserRound, label: "Profile", tone: "muted" }
  ],
  squads: [],
  alerts: [],
  pipeline: []
};

export function getOperationsDashboardData(
  role: "admin" | "organizer"
): OrganizerDashboardData {
  if (role === "admin") {
    return {
      ...organizerProductionDashboardData,
      hero: {
        ...organizerProductionDashboardData.hero,
        description:
          "Open the organizer workspace to manage tournaments and review public analytics. Operational activity will appear when backend dashboard metrics are available.",
        stage: "Admin workspace",
        title: "Admin Overview"
      }
    };
  }

  return organizerProductionDashboardData;
}

export function normalizeProductionDashboardRole(
  value?: string | string[]
): ProductionDashboardRole {
  const role = Array.isArray(value) ? value[0] : value;

  if (role === "player" || role === "organizer" || role === "public") {
    return role;
  }

  return "player";
}
