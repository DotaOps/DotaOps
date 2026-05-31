"use client";

import { Settings } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { getCurrentUserProfile } from "@/lib/auth";
import { listOrganizerTournaments } from "@/lib/organizer-tournament-data";
import { isOrganizerRole } from "@/lib/route-access";

interface PublicTournamentManageLinkProps {
  className?: string;
  label?: string;
  note?: boolean;
  slug: string;
  tournamentId: string;
}

export function PublicTournamentManageLink({
  className = "button ops-button-primary",
  label = "Manage Groups",
  note = false,
  slug,
  tournamentId
}: PublicTournamentManageLinkProps) {
  const [href, setHref] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function resolveManageLink() {
      try {
        const profile = await getCurrentUserProfile();

        if (!isMounted || !isOrganizerRole(profile?.role)) {
          return;
        }

        const tournaments = await listOrganizerTournaments();
        const manageableTournament = tournaments.find(
          (tournament) =>
            tournament.format === "groups_playoff" &&
            (tournament.id === tournamentId || tournament.slug === slug)
        );

        if (isMounted && manageableTournament) {
          setHref(
            `/organizator?tournamentId=${encodeURIComponent(manageableTournament.id)}&slug=${encodeURIComponent(manageableTournament.slug)}&view=groups`
          );
        }
      } catch {
        if (isMounted) {
          setHref(null);
        }
      }
    }

    const timeout = window.setTimeout(() => void resolveManageLink(), 0);

    return () => {
      isMounted = false;
      window.clearTimeout(timeout);
    };
  }, [slug, tournamentId]);

  if (!href) {
    return null;
  }

  if (note) {
    return (
      <div className="groups-standings-management-note">
        <span>Group management is available in Organizer workspace.</span>
        <Link className={className} href={href}>
          <Settings size={16} />
          <span>{label}</span>
        </Link>
      </div>
    );
  }

  return (
    <Link className={className} href={href}>
      <Settings size={16} />
      <span>{label}</span>
    </Link>
  );
}
