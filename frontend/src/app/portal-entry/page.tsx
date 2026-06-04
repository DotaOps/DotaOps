import { PortalEntryExperience } from "@/components/auth/portal-entry-experience";

interface PortalEntryPageProps {
  searchParams?: Promise<{
    next?: string | string[];
  }>;
}

export default async function PortalEntryPage({ searchParams }: PortalEntryPageProps) {
  const params = await searchParams;
  const nextPath = Array.isArray(params?.next) ? params.next[0] : params?.next;

  return <PortalEntryExperience nextPath={nextPath} />;
}
