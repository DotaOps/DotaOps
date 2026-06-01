"use client";

import {
  CheckCircle2,
  Clock3,
  ListFilter,
  RadioTower,
  Trophy,
  UsersRound
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useMemo, useState } from "react";

import { TournamentCard } from "@/components/tournament-card";
import { tournamentPhase, type TournamentFilter } from "@/lib/tournament-phase";
import type { Tournament } from "@/lib/types";
import { classNames } from "@/lib/utils";

const filterOptions: Array<{
  icon: LucideIcon;
  label: string;
  value: TournamentFilter;
}> = [
  { icon: ListFilter, label: "All", value: "all" },
  { icon: UsersRound, label: "Registrations open", value: "registration" },
  { icon: Clock3, label: "Upcoming", value: "upcoming" },
  { icon: RadioTower, label: "Live", value: "live" },
  { icon: CheckCircle2, label: "Finished", value: "finished" }
];

const initialFilterCounts: Record<TournamentFilter, number> = {
  all: 0,
  finished: 0,
  live: 0,
  registration: 0,
  upcoming: 0
};

interface TournamentCatalogueProps {
  referenceTime: number;
  tournaments: Tournament[];
}

export function TournamentCatalogue({ referenceTime, tournaments }: TournamentCatalogueProps) {
  const [activeFilter, setActiveFilter] = useState<TournamentFilter>("all");

  const filterCounts = useMemo(
    () =>
      tournaments.reduce<Record<TournamentFilter, number>>(
        (counts, tournament) => {
          counts.all += 1;
          counts[tournamentPhase(tournament, referenceTime)] += 1;

          return counts;
        },
        { ...initialFilterCounts }
      ),
    [referenceTime, tournaments]
  );

  const filteredTournaments = useMemo(
    () =>
      activeFilter === "all"
        ? tournaments
        : tournaments.filter(
            (tournament) => tournamentPhase(tournament, referenceTime) === activeFilter
          ),
    [activeFilter, referenceTime, tournaments]
  );

  const activeFilterLabel =
    filterOptions.find((filter) => filter.value === activeFilter)?.label ?? "All";

  return (
    <div className="tournament-catalogue">
      <div className="tournament-catalogue-toolbar">
        <div className="tournament-catalogue-summary">
          <span className="ops-label">Catalogue view</span>
          <strong className="ops-data">{filteredTournaments.length} events</strong>
          <p className="ops-mono">{activeFilterLabel}</p>
        </div>

        <div aria-label="Tournament filters" className="tournament-filter-tabs">
          {filterOptions.map((filter) => (
            <button
              aria-pressed={activeFilter === filter.value}
              className={classNames(
                "tournament-filter-button",
                activeFilter === filter.value && "is-active"
              )}
              key={filter.value}
              onClick={() => setActiveFilter(filter.value)}
              type="button"
            >
              <filter.icon size={16} />
              <span>{filter.label}</span>
              <strong className="ops-mono">{filterCounts[filter.value]}</strong>
            </button>
          ))}
        </div>
      </div>

      {filteredTournaments.length === 0 ? (
        <div className="tournament-registry-state">
          <Trophy size={22} />
          <div>
            <h3>No tournaments match this filter.</h3>
            <p>There are no published events in this phase yet.</p>
          </div>
        </div>
      ) : (
        <div aria-live="polite" className="tournament-card-grid">
          {filteredTournaments.map((tournament) => (
            <TournamentCard key={tournament.id} tournament={tournament} />
          ))}
        </div>
      )}
    </div>
  );
}
