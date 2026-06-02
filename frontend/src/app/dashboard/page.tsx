import { RoleDashboard } from "@/components/dashboard/role-dashboard";
import type { MeDashboardRole } from "@/lib/me-dashboard-data";

type DashboardRoleHint = MeDashboardRole | "public";

interface DashboardPageProps {
  searchParams?: Promise<{
    role?: string | string[];
  }>;
}

export default async function DashboardPage({ searchParams }: DashboardPageProps) {
  const params = await searchParams;
  const role = params?.role ? normalizeDashboardRoleHint(params.role) : undefined;

  return <RoleDashboard role={role} />;
}

function normalizeDashboardRoleHint(value: string | string[]): DashboardRoleHint {
  const role = Array.isArray(value) ? value[0] : value;

  if (role === "admin" || role === "organizer" || role === "player" || role === "public") {
    return role;
  }

  return "player";
}
