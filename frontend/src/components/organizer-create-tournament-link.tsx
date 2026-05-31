"use client";

import { Plus } from "lucide-react";
import Link from "next/link";

import { useCurrentUserProfile } from "@/components/current-user-profile-context";
import { isOrganizerRole } from "@/lib/route-access";

export function OrganizerCreateTournamentLink() {
  const profile = useCurrentUserProfile();

  if (!isOrganizerRole(profile?.role)) {
    return null;
  }

  return (
    <Link className="button ops-button-primary" href="/organizator">
      <Plus size={18} />
      <span>New Tournament</span>
    </Link>
  );
}
