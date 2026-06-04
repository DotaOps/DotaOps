import type { ReactNode } from "react";

import { StatusBadge } from "@/components/status-badge";
import type { TournamentStatus } from "@/lib/types";

interface TournamentCommandHeaderProps {
  eyebrow: string;
  title: string;
  description: string;
  status?: TournamentStatus;
  actions?: ReactNode;
  children?: ReactNode;
}

export function TournamentCommandHeader({
  eyebrow,
  title,
  description,
  status,
  actions,
  children
}: TournamentCommandHeaderProps) {
  return (
    <section className="tournament-command-header ops-panel">
      <div className="tournament-command-copy">
        <p className="ops-label">{eyebrow}</p>
        <div className="tournament-command-title-row">
          <h1>{title}</h1>
          {status ? <StatusBadge status={status} /> : null}
        </div>
        <p className="ops-mono">{description}</p>
      </div>

      {actions ? <div className="tournament-command-actions">{actions}</div> : null}
      {children ? <div className="tournament-command-header-grid">{children}</div> : null}
    </section>
  );
}
