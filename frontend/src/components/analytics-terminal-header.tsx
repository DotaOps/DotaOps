import { Activity, DatabaseZap, RadioTower } from "lucide-react";

import { StatusBadge } from "@/components/status-badge";

interface AnalyticsTerminalHeaderProps {
  importedMatches: number;
  totalMatches: number;
  topHero: string;
  topTeam: string;
}

export function AnalyticsTerminalHeader({
  importedMatches,
  totalMatches,
  topHero,
  topTeam
}: AnalyticsTerminalHeaderProps) {
  return (
    <section className="analytics-terminal-header ops-panel">
      <div className="analytics-terminal-copy">
        <p className="ops-label">DotaOps Analytics Terminal</p>
        <h1>Analytics Terminal</h1>
        <p className="ops-mono">
          OpenDota match_id flow, team and hero comparisons, and telemetry
          metrics for tournament decisions.
        </p>
      </div>

      <div className="analytics-terminal-status">
        <div>
          <RadioTower size={18} />
          <span className="ops-label">Telemetry</span>
          <strong className="ops-data">ONLINE</strong>
        </div>
        <div>
          <DatabaseZap size={18} />
          <span className="ops-label">Match imports</span>
          <strong className="ops-data">
            {importedMatches}/{totalMatches}
          </strong>
        </div>
        <div>
          <Activity size={18} />
          <span className="ops-label">Top signal</span>
          <strong className="ops-data">{topHero}</strong>
        </div>
      </div>

      <div className="analytics-terminal-strip">
        <article>
          <span className="ops-label">Primary team</span>
          <strong>{topTeam}</strong>
        </article>
        <article>
          <span className="ops-label">Hero model</span>
          <strong>{topHero}</strong>
        </article>
        <article>
          <span className="ops-label">Pipeline</span>
          <StatusBadge status={importedMatches > 0 ? "ready" : "idle"} />
        </article>
      </div>
    </section>
  );
}
