import { RoleDashboard } from "@/components/dashboard/role-dashboard";
import { normalizeProductionDashboardRole } from "@/lib/dashboard-production-data";

interface DashboardPageProps {
  searchParams?: Promise<{
    role?: string | string[];
  }>;
}

export default async function DashboardPage({ searchParams }: DashboardPageProps) {
  const params = await searchParams;
  const role = params?.role ? normalizeProductionDashboardRole(params.role) : undefined;

  return <RoleDashboard role={role} />;
}
