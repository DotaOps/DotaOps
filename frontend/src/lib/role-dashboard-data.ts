import {
  BarChart3,
  Brackets,
  CalendarDays,
  DatabaseZap,
  FileInput,
  Gamepad2,
  ListChecks,
  RadioTower,
  Sparkles,
  Swords,
  Trophy,
  UserPlus,
  UsersRound,
  Zap
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type DashboardRole = "captain" | "player" | "organizer" | "public";
export type DashboardTone = "red" | "gold" | "cyan" | "green" | "muted";

export interface DashboardKpi {
  label: string;
  value: string;
  detail?: string;
  tone: DashboardTone;
  icon: LucideIcon;
  progress?: number;
}

export interface DashboardAction {
  label: string;
  href?: string;
  disabled?: boolean;
  icon: LucideIcon;
  tone?: DashboardTone;
}

export interface DashboardAlert {
  title: string;
  detail: string;
  tone: DashboardTone;
}

export interface RosterPlayer {
  id: string;
  name: string;
  role: string;
  hero: string;
  status?: "online" | "in-game" | "offline";
  avatarCode: string;
}

export interface CaptainMatch {
  tournament: string;
  detail: string;
  opponent: string;
  opponentCode: string;
  time: string;
  status: "ready" | "queued" | "locked";
}

export interface CaptainDashboardData {
  topbar: {
    activeTeam: string;
    rank: string;
    primaryAction: DashboardAction;
  };
  kpis: DashboardKpi[];
  hero: {
    team: string;
    status: string;
    opponent: string;
    opponentCode: string;
    scheduledStart: string;
    recentForm: Array<"W" | "L">;
  };
  upcomingMatches: CaptainMatch[];
  quickActions: DashboardAction[];
  roster: RosterPlayer[];
  alerts: DashboardAlert[];
  performance: DashboardKpi[];
}

export interface PlayerMetric {
  label: string;
  value: string;
  detail?: string;
  tone: DashboardTone;
  featured?: boolean;
}

export interface PlayerMatchLog {
  hero: string;
  result: "WIN" | "LOSS";
  type: string;
  kda: string;
  duration: string;
  gpmXpm: string;
  analyzed: "Analyzing" | "Completed";
}

export interface PlayerDashboardData {
  topbar: {
    activeTeam: string;
    rank: string;
    primaryAction: DashboardAction;
  };
  hero: {
    name: string;
    role: string;
    label: string;
    description: string;
    recentForm: Array<"W" | "L">;
  };
  performance: PlayerMetric[];
  roster: RosterPlayer[];
  matchLog: PlayerMatchLog[];
}

export interface OrganizerMatrixRow {
  matchId: string;
  series: string;
  competitors: string;
  progress: string;
  status: "stable" | "queued" | "locked";
}

export interface OrganizerSquad {
  name: string;
  status: "approved" | "locked" | "pending";
  action: string;
  avatarCode: string;
}

export interface PipelineItem {
  label: string;
  value: string;
  tone: DashboardTone;
}

export interface OrganizerDashboardData {
  topbar: {
    activeTournament: string;
    primaryAction: DashboardAction;
  };
  hero: {
    title: string;
    stage: string;
    description: string;
    registeredTeams: string;
    integrity: string;
    status: string;
  };
  kpis: DashboardKpi[];
  matrix: OrganizerMatrixRow[];
  quickActions: DashboardAction[];
  squads: OrganizerSquad[];
  alerts: DashboardAlert[];
  pipeline: PipelineItem[];
}

export const captainDashboardData: CaptainDashboardData = {
  topbar: {
    activeTeam: "Team Liquid",
    rank: "Immortal",
    primaryAction: {
      href: "/turnirji",
      icon: Trophy,
      label: "Join Tournament"
    }
  },
  kpis: [
    {
      icon: Trophy,
      label: "Active Tournaments",
      progress: 32,
      tone: "red",
      value: "02"
    },
    {
      icon: CalendarDays,
      label: "Upcoming Matches",
      progress: 52,
      tone: "gold",
      value: "03"
    },
    {
      icon: BarChart3,
      label: "Team Win Rate",
      progress: 64,
      tone: "cyan",
      value: "64%"
    },
    {
      icon: DatabaseZap,
      label: "Match Data Ready",
      progress: 78,
      tone: "red",
      value: "12"
    }
  ],
  hero: {
    opponent: "Gaimin Gladiators",
    opponentCode: "GG",
    recentForm: ["L", "W", "W", "L", "W"],
    scheduledStart: "14:30 UTC",
    status: "Active",
    team: "Team Liquid"
  },
  upcomingMatches: [
    {
      detail: "Group Stage B",
      opponent: "Team OG",
      opponentCode: "OG",
      status: "ready",
      time: "18:00 UTC",
      tournament: "DreamLeague S22"
    },
    {
      detail: "Main Event",
      opponent: "Team Spirit",
      opponentCode: "TS",
      status: "queued",
      time: "Tomorrow",
      tournament: "Elite League"
    },
    {
      detail: "Qualifier",
      opponent: "Azure Ray",
      opponentCode: "AZ",
      status: "locked",
      time: "Feb 24",
      tournament: "Dota OPS Closed"
    }
  ],
  quickActions: [
    { href: "/turnirji", icon: UserPlus, label: "Join New Tournament", tone: "red" },
    { href: "/ekipe", icon: UsersRound, label: "View Team Roster", tone: "gold" },
    { disabled: true, icon: FileInput, label: "Submit Match Result", tone: "cyan" },
    { disabled: true, icon: DatabaseZap, label: "Import Match ID", tone: "red" }
  ],
  roster: [
    { avatarCode: "MP", hero: "Puck", id: "midpulse", name: "MidPulse", role: "Mid" },
    { avatarCode: "SM", hero: "Juggernaut", id: "safemode", name: "SafeMode", role: "Carry" },
    { avatarCode: "OG", hero: "Mars", id: "offgrid", name: "OffGrid", role: "Offlane" },
    { avatarCode: "VI", hero: "Crystal Maiden", id: "vision", name: "Vision", role: "Support" },
    { avatarCode: "SC", hero: "Rubick", id: "shotcall", name: "Shotcall", role: "Captain" }
  ],
  alerts: [
    {
      detail: "DreamLeague roster locks in 04h 12m. One player signature pending.",
      title: "Roster Lock Imminent",
      tone: "red"
    },
    {
      detail: "Gaimin Gladiators recently played 3 matches with Terrorblade.",
      title: "Scouting Update",
      tone: "gold"
    }
  ],
  performance: [
    {
      detail: "+5%",
      icon: BarChart3,
      label: "Win Rate Last 20",
      tone: "cyan",
      value: "72%"
    },
    {
      detail: "Stable",
      icon: Swords,
      label: "Avg Team KDA",
      tone: "gold",
      value: "4.2"
    },
    {
      detail: "core pool",
      icon: Sparkles,
      label: "Most Played Hero",
      tone: "red",
      value: "Rubick"
    }
  ]
};

export const playerDashboardData: PlayerDashboardData = {
  topbar: {
    activeTeam: "Team Liquid",
    rank: "Immortal",
    primaryAction: {
      disabled: true,
      icon: Gamepad2,
      label: "View Match Briefing"
    }
  },
  hero: {
    description:
      "Dominating the central lane with pinpoint accuracy and elite tactical awareness. Welcome to your command center, Commander.",
    label: "Pro Series / Live Event",
    name: "MidPulse Elite",
    recentForm: ["W", "W", "L", "W", "W"],
    role: "Mid / Position 2"
  },
  performance: [
    { detail: "+0.42 vs Avg", featured: true, label: "KDA Ratio", tone: "cyan", value: "4.82" },
    { featured: true, label: "Win Rate", tone: "red", value: "68.5%" },
    { detail: "Elite Veteran", featured: true, label: "Matches", tone: "muted", value: "1,240" },
    { detail: "92% to Immortal", featured: true, label: "Rank Tier", tone: "gold", value: "Divine IV" },
    { detail: "+12% last 10", label: "AVG GPM", tone: "green", value: "642" },
    { detail: "Stable trend", label: "AVG XPM", tone: "muted", value: "718" },
    { detail: "-3% vs avg", label: "Last Hits @10", tone: "red", value: "54.2" },
    { detail: "+5% high impact", label: "Kill Participation", tone: "green", value: "72.4%" }
  ],
  roster: [
    { avatarCode: "MC", hero: "Lifestealer", id: "micke", name: "miCKe", role: "Carry / Pos 1", status: "online" },
    { avatarCode: "NI", hero: "Puck", id: "nisha", name: "Nisha", role: "Mid / Pos 2", status: "online" },
    { avatarCode: "33", hero: "Beastmaster", id: "33", name: "33", role: "Offlane / Pos 3", status: "in-game" },
    { avatarCode: "BX", hero: "Tusk", id: "boxi", name: "Boxi", role: "Support / Pos 4", status: "online" },
    { avatarCode: "IN", hero: "Oracle", id: "insania", name: "Insania", role: "Hard Support / Pos 5", status: "offline" }
  ],
  matchLog: [
    {
      analyzed: "Analyzing",
      duration: "34:12",
      gpmXpm: "742 / 812",
      hero: "Shadow Fiend",
      kda: "14 / 2 / 8",
      result: "WIN",
      type: "Ranked Solo"
    },
    {
      analyzed: "Completed",
      duration: "41:05",
      gpmXpm: "512 / 640",
      hero: "Puck",
      kda: "9 / 10 / 12",
      result: "LOSS",
      type: "Matchmaking"
    },
    {
      analyzed: "Completed",
      duration: "28:55",
      gpmXpm: "890 / 920",
      hero: "Invoker",
      kda: "22 / 1 / 15",
      result: "WIN",
      type: "Pro Scrim"
    }
  ]
};

export const organizerDashboardData: OrganizerDashboardData = {
  topbar: {
    activeTournament: "TI Global Finals",
    primaryAction: {
      href: "/organizator",
      icon: Trophy,
      label: "New Tournament"
    }
  },
  hero: {
    description:
      "Group Stage | Round 4. Monitoring real-time telemetry from all 16 active instances. Integrity status remains stable across all clusters.",
    integrity: "Stable",
    registeredTeams: "16/16",
    stage: "Pro Series / Live Feed: Arena A-01",
    status: "Live Operations",
    title: "TI Global Finals"
  },
  kpis: [
    { icon: Trophy, label: "Active Tournaments", progress: 34, tone: "red", value: "02" },
    { icon: UsersRound, label: "Registered Teams", progress: 100, tone: "gold", value: "16/16" },
    { icon: ListChecks, label: "Pending Approvals", progress: 4, tone: "red", value: "00" },
    { icon: Zap, label: "Live Matches", progress: 70, tone: "cyan", value: "03" },
    { icon: DatabaseZap, label: "Match Data Ready", progress: 48, tone: "red", value: "12" }
  ],
  matrix: [
    {
      competitors: "Spirit vs Gaimin",
      matchId: "#7710242",
      progress: "34:21",
      series: "Grand Finals BO5",
      status: "stable"
    },
    {
      competitors: "Liquid vs Azure Ray",
      matchId: "#7710250",
      progress: "Upcoming",
      series: "Lower Bracket BO3",
      status: "queued"
    },
    {
      competitors: "BetBoom vs Xtreme",
      matchId: "#7710258",
      progress: "Upcoming",
      series: "Lower Bracket BO3",
      status: "locked"
    }
  ],
  quickActions: [
    { disabled: true, icon: Brackets, label: "Generate Bracket", tone: "gold" },
    { disabled: true, icon: RadioTower, label: "Publish Schedule", tone: "cyan" },
    { disabled: true, icon: FileInput, label: "Enter Results", tone: "red" },
    { disabled: true, icon: DatabaseZap, label: "Import Match Data", tone: "cyan" }
  ],
  squads: [
    { action: "View Roster", avatarCode: "TS", name: "Team Spirit", status: "approved" },
    { action: "View Roster", avatarCode: "TL", name: "Team Liquid", status: "locked" },
    { action: "Review Data", avatarCode: "GG", name: "Gaimin Gladiators", status: "pending" }
  ],
  alerts: [
    {
      detail: "DreamLeague roster locks in 04h 12m. One player signature pending.",
      title: "Roster Lock Imminent",
      tone: "red"
    },
    {
      detail: "Cluster SG-04 reporting 120ms+ latency. Redirecting local traffic to HK-01.",
      title: "Server Latency Spike in SEA",
      tone: "gold"
    },
    {
      detail: "Team Xtreme submitted a technical review request for Game 2.",
      title: "Pending Protest: Match M-2024-001",
      tone: "red"
    }
  ],
  pipeline: [
    { label: "OpenDota Sync", tone: "cyan", value: "99.9% Active" },
    { label: "Telemetry Flow", tone: "cyan", value: "Healthy" },
    { label: "API Relay (EU-W)", tone: "red", value: "Stable" }
  ]
};

export function normalizeDashboardRole(value?: string | string[]): DashboardRole {
  const role = Array.isArray(value) ? value[0] : value;

  if (role === "player" || role === "organizer" || role === "public") {
    return role;
  }

  return "player";
}
