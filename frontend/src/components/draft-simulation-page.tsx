"use client";

import {
  ArrowLeftRight,
  BrainCircuit,
  Loader2,
  RotateCcw,
  Search,
  Shield,
  Sparkles,
  Swords,
  X
} from "lucide-react";
import Image from "next/image";
import { useEffect, useMemo, useState } from "react";

import { SectionHeader } from "@/components/section-header";
import { classNames } from "@/lib/utils";

type DraftTeam = "teamA" | "teamB";
type DraftTeamSide = "radiant" | "dire" | "neutral";

interface DraftHero {
  iconUrl: string | null;
  id: number;
  name: string;
  normalizedName: string;
}

interface DraftModelMetadata {
  createdAt: string | null;
  heroCount: number;
  modelType: string;
  threshold: number;
  version: number;
}

interface DraftHeroesResponse {
  heroes: DraftHero[];
  model: DraftModelMetadata;
}

interface DraftPredictionResult {
  modelCreatedAt: string | null;
  modelThreshold: number;
  teamASide: DraftTeamSide;
  teamAWinProbability: number;
  teamBWinProbability: number;
}

const teamOptions: Array<{ id: DraftTeam; label: string }> = [
  { id: "teamA", label: "Team A" },
  { id: "teamB", label: "Team B" }
];

const sideOptions: Array<{ id: DraftTeamSide; label: string; detail: string }> = [
  { id: "radiant", label: "Radiant", detail: "Team A uses Radiant side bias" },
  { id: "dire", label: "Dire", detail: "Team A uses Dire side bias" },
  { id: "neutral", label: "Neutral", detail: "Removes learned side bias" }
];

function percent(value: number, digits = 1) {
  return `${(value * 100).toFixed(digits)}%`;
}

function modelDateLabel(value: string | null) {
  if (!value) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function normalizeQuery(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function opposingSide(side: DraftTeamSide): DraftTeamSide {
  if (side === "radiant") {
    return "dire";
  }

  if (side === "dire") {
    return "radiant";
  }

  return "neutral";
}

function sideLabel(side: DraftTeamSide) {
  return side.charAt(0).toUpperCase() + side.slice(1);
}

async function readResponseMessage(response: Response, fallback: string) {
  try {
    const payload = (await response.json()) as { message?: unknown };

    return typeof payload.message === "string" ? payload.message : fallback;
  } catch {
    return fallback;
  }
}

export function DraftSimulationPage() {
  const [activeTeam, setActiveTeam] = useState<DraftTeam>("teamA");
  const [error, setError] = useState<string | null>(null);
  const [heroes, setHeroes] = useState<DraftHero[]>([]);
  const [isLoadingHeroes, setIsLoadingHeroes] = useState(true);
  const [isPredicting, setIsPredicting] = useState(false);
  const [model, setModel] = useState<DraftModelMetadata | null>(null);
  const [prediction, setPrediction] = useState<DraftPredictionResult | null>(null);
  const [query, setQuery] = useState("");
  const [teamA, setTeamA] = useState<number[]>([]);
  const [teamASide, setTeamASide] = useState<DraftTeamSide>("radiant");
  const [teamB, setTeamB] = useState<number[]>([]);

  useEffect(() => {
    let isMounted = true;

    async function loadHeroes() {
      setIsLoadingHeroes(true);
      setError(null);

      try {
        const response = await fetch("/api/draft-simulation/heroes", { cache: "no-store" });

        if (!response.ok) {
          throw new Error(await readResponseMessage(response, "Draft heroes are unavailable."));
        }

        const payload = (await response.json()) as DraftHeroesResponse;

        if (isMounted) {
          setHeroes(payload.heroes);
          setModel(payload.model);
        }
      } catch (caught) {
        if (isMounted) {
          setError(caught instanceof Error ? caught.message : "Draft heroes are unavailable.");
        }
      } finally {
        if (isMounted) {
          setIsLoadingHeroes(false);
        }
      }
    }

    void loadHeroes();

    return () => {
      isMounted = false;
    };
  }, []);

  const heroById = useMemo(
    () => new Map(heroes.map((hero) => [hero.id, hero])),
    [heroes]
  );

  const selectedHeroIds = useMemo(
    () => new Set([...teamA, ...teamB]),
    [teamA, teamB]
  );

  const filteredHeroes = useMemo(() => {
    const normalizedQuery = normalizeQuery(query);

    if (!normalizedQuery) {
      return heroes;
    }

    return heroes.filter(
      (hero) =>
        hero.normalizedName.includes(normalizedQuery) ||
        hero.id.toString().includes(normalizedQuery)
    );
  }, [heroes, query]);

  const teamAHeroes = useMemo(
    () => teamA.map((heroId) => heroById.get(heroId)).filter((hero): hero is DraftHero => Boolean(hero)),
    [heroById, teamA]
  );

  const teamBHeroes = useMemo(
    () => teamB.map((heroId) => heroById.get(heroId)).filter((hero): hero is DraftHero => Boolean(hero)),
    [heroById, teamB]
  );

  const canPredict = teamA.length === 5 && teamB.length === 5 && !isPredicting;
  const activeTeamCount = activeTeam === "teamA" ? teamA.length : teamB.length;
  const activeTeamLabel = activeTeam === "teamA" ? "Team A" : "Team B";
  const teamBSide = opposingSide(teamASide);

  function resetPredictionState() {
    setPrediction(null);
    setError(null);
  }

  function addHero(heroId: number) {
    if (selectedHeroIds.has(heroId)) {
      return;
    }

    const updateTeam = activeTeam === "teamA" ? setTeamA : setTeamB;

    if (activeTeamCount >= 5) {
      setError(`${activeTeamLabel} already has 5 heroes.`);
      return;
    }

    updateTeam((current) => [...current, heroId]);
    resetPredictionState();

    if (activeTeam === "teamA" && teamA.length === 4 && teamB.length < 5) {
      setActiveTeam("teamB");
    }

    if (activeTeam === "teamB" && teamB.length === 4 && teamA.length < 5) {
      setActiveTeam("teamA");
    }
  }

  function removeHero(team: DraftTeam, heroId: number) {
    const updateTeam = team === "teamA" ? setTeamA : setTeamB;

    updateTeam((current) => current.filter((currentHeroId) => currentHeroId !== heroId));
    setActiveTeam(team);
    resetPredictionState();
  }

  function clearDraft() {
    setTeamA([]);
    setTeamB([]);
    setActiveTeam("teamA");
    resetPredictionState();
  }

  function swapTeams() {
    setTeamA(teamB);
    setTeamB(teamA);
    setActiveTeam((current) => (current === "teamA" ? "teamB" : "teamA"));
    resetPredictionState();
  }

  async function runPrediction() {
    if (!canPredict) {
      setError("Select 5 unique heroes for each team before running the model.");
      return;
    }

    setIsPredicting(true);
    setError(null);

    try {
      const response = await fetch("/api/draft-simulation/predict", {
        body: JSON.stringify({
          teamAHeroIds: teamA,
          teamASide,
          teamBHeroIds: teamB
        }),
        headers: {
          "Content-Type": "application/json"
        },
        method: "POST"
      });

      if (!response.ok) {
        throw new Error(await readResponseMessage(response, "Draft prediction failed."));
      }

      setPrediction((await response.json()) as DraftPredictionResult);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Draft prediction failed.");
    } finally {
      setIsPredicting(false);
    }
  }

  const favoriteTeam = prediction
    ? prediction.teamAWinProbability >= prediction.teamBWinProbability
      ? "Team A"
      : "Team B"
    : null;

  return (
    <div className="draft-sim-shell">
      <section className="draft-sim-hero ops-panel ops-command-grid">
        <div className="draft-sim-hero-copy">
          <p className="ops-label">DotaOps prediction model</p>
          <h1>Draft Simulation</h1>
          <p>
            Build two five-hero drafts and run the trained draft model for a Team A win-rate
            estimate. The model accounts for hero strength, same-team pairs, counter matchups,
            and side selection.
          </p>
        </div>

        <div className="draft-sim-model-panel">
          <div>
            <BrainCircuit size={20} />
            <span className="ops-label">Model</span>
            <strong>DotaOps model</strong>
          </div>
          <div>
            <Shield size={20} />
            <span className="ops-label">Hero pool</span>
            <strong>{model?.heroCount ?? (heroes.length > 0 ? heroes.length : "Loading")}</strong>
          </div>
          <div>
            <Sparkles size={20} />
            <span className="ops-label">Created</span>
            <strong>{modelDateLabel(model?.createdAt ?? null)}</strong>
          </div>
        </div>
      </section>

      <section className="draft-sim-toolbar ops-panel">
        <div className="draft-sim-control-group">
          <span className="ops-label">Picking for</span>
          <div className="draft-sim-segmented" role="group" aria-label="Active draft team">
            {teamOptions.map((option) => (
              <button
                className={classNames(activeTeam === option.id && "is-active")}
                key={option.id}
                onClick={() => setActiveTeam(option.id)}
                type="button"
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <label className="draft-sim-search">
          <Search size={17} />
          <input
            placeholder="Search heroes"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>

        <div className="draft-sim-control-group draft-sim-side-group">
          <span className="ops-label">Team A side</span>
          <div className="draft-sim-side-options" role="group" aria-label="Team A side">
            {sideOptions.map((option) => (
              <button
                className={classNames(teamASide === option.id && "is-active")}
                key={option.id}
                onClick={() => {
                  setTeamASide(option.id);
                  resetPredictionState();
                }}
                title={option.detail}
                type="button"
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>
      </section>

      {error ? (
        <section className="draft-sim-alert ops-panel">
          <strong>Draft simulation unavailable.</strong>
          <p>{error}</p>
        </section>
      ) : null}

      <section className="draft-sim-layout">
        <div className="draft-sim-team-stack">
          <DraftTeamPanel
            heroes={teamAHeroes}
            isActive={activeTeam === "teamA"}
            label="Team A"
            onActivate={() => setActiveTeam("teamA")}
            onRemove={(heroId) => removeHero("teamA", heroId)}
            side={teamASide}
          />
          <DraftTeamPanel
            heroes={teamBHeroes}
            isActive={activeTeam === "teamB"}
            label="Team B"
            onActivate={() => setActiveTeam("teamB")}
            onRemove={(heroId) => removeHero("teamB", heroId)}
            side={teamBSide}
          />

          <div className="draft-sim-actions ops-panel">
            <button
              className="button ops-button-primary"
              disabled={!canPredict}
              onClick={() => void runPrediction()}
              type="button"
            >
              {isPredicting ? <Loader2 className="draft-sim-spin" size={16} /> : <Swords size={16} />}
              {isPredicting ? "Running..." : "Run Prediction"}
            </button>
            <button className="button ops-button-secondary" onClick={swapTeams} type="button">
              <ArrowLeftRight size={16} />
              Swap Teams
            </button>
            <button className="button draft-sim-ghost-button" onClick={clearDraft} type="button">
              <RotateCcw size={16} />
              Clear Draft
            </button>
          </div>
        </div>

        <div className="draft-sim-pool ops-panel">
          <SectionHeader
            eyebrow="Hero selection"
            title="Hero Pool"
            description={`Pick heroes for ${activeTeamLabel}. Selected heroes are locked out of the opposite draft.`}
          />

          {isLoadingHeroes ? (
            <div className="draft-sim-state">
              <Loader2 className="draft-sim-spin" size={20} />
              <strong>Loading model heroes...</strong>
            </div>
          ) : null}

          {!isLoadingHeroes && filteredHeroes.length === 0 ? (
            <div className="draft-sim-state">
              <strong>No heroes match this search.</strong>
              <p>Try another hero name or hero ID.</p>
            </div>
          ) : null}

          {!isLoadingHeroes && filteredHeroes.length > 0 ? (
            <div className="draft-sim-hero-grid">
              {filteredHeroes.map((hero) => {
                const isSelected = selectedHeroIds.has(hero.id);

                return (
                  <button
                    className={classNames("draft-sim-hero-button", isSelected && "is-selected")}
                    disabled={isSelected}
                    key={hero.id}
                    onClick={() => addHero(hero.id)}
                    type="button"
                  >
                    <HeroIcon hero={hero} size="small" />
                    <span className="draft-sim-hero-name">{hero.name}</span>
                    <small className="ops-mono">#{hero.id}</small>
                  </button>
                );
              })}
            </div>
          ) : null}
        </div>

        <PredictionPanel
          favoriteTeam={favoriteTeam}
          prediction={prediction}
          teamAComplete={teamA.length === 5}
          teamASide={teamASide}
          teamBComplete={teamB.length === 5}
        />
      </section>
    </div>
  );
}

function DraftTeamPanel({
  heroes,
  isActive,
  label,
  onActivate,
  onRemove,
  side
}: {
  heroes: DraftHero[];
  isActive: boolean;
  label: string;
  onActivate: () => void;
  onRemove: (heroId: number) => void;
  side: DraftTeamSide;
}) {
  const emptySlots = Math.max(0, 5 - heroes.length);

  return (
    <section
      className={classNames(
        "draft-sim-team-panel ops-panel",
        isActive && "is-active",
        `is-${side}`
      )}
    >
      <button className="draft-sim-team-header" onClick={onActivate} type="button">
        <span>
          <span className="ops-label">{isActive ? "Active draft" : "Draft squad"}</span>
          <strong>{label}</strong>
        </span>
        <span className="draft-sim-team-header-meta">
          <span className="draft-sim-side-badge">{sideLabel(side)}</span>
          <span className="ops-mono">{heroes.length}/5</span>
        </span>
      </button>

      <div className="draft-sim-slots">
        {heroes.map((hero) => (
          <div className="draft-sim-slot is-filled" key={hero.id}>
            <HeroIcon hero={hero} />
            <span className="draft-sim-slot-copy">
              <strong>{hero.name}</strong>
              <small className="ops-mono">Hero #{hero.id}</small>
            </span>
            <button aria-label={`Remove ${hero.name}`} onClick={() => onRemove(hero.id)} type="button">
              <X size={14} />
            </button>
          </div>
        ))}

        {Array.from({ length: emptySlots }, (_, index) => (
          <div className="draft-sim-slot" key={`${label}-empty-${index}`}>
            <span className="draft-sim-hero-icon is-empty" aria-hidden="true" />
            <span className="draft-sim-slot-copy">
              <strong>Empty pick</strong>
              <small>Choose from hero pool</small>
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}

function HeroIcon({
  hero,
  size = "default"
}: {
  hero: DraftHero;
  size?: "default" | "small";
}) {
  return (
    <span className={classNames("draft-sim-hero-icon", size === "small" && "is-small")}>
      {hero.iconUrl ? (
        <Image
          alt={`${hero.name} icon`}
          height={42}
          sizes={size === "small" ? "34px" : "42px"}
          src={hero.iconUrl}
          width={42}
        />
      ) : (
        <span aria-hidden="true">{hero.name.slice(0, 2).toUpperCase()}</span>
      )}
    </span>
  );
}

function PredictionPanel({
  favoriteTeam,
  prediction,
  teamAComplete,
  teamASide,
  teamBComplete
}: {
  favoriteTeam: string | null;
  prediction: DraftPredictionResult | null;
  teamAComplete: boolean;
  teamASide: DraftTeamSide;
  teamBComplete: boolean;
}) {
  const ready = teamAComplete && teamBComplete;
  const teamAWidth = prediction ? `${Math.round(prediction.teamAWinProbability * 1000) / 10}%` : "50%";

  return (
    <aside className="draft-sim-result-panel ops-panel">
      <SectionHeader
        eyebrow="Model output"
        title="Win Rate"
        description="The score is the model-estimated Team A win probability for the selected draft."
      />

      {prediction ? (
        <>
          <div className="draft-sim-win-card">
            <span className="ops-label">Team A win rate</span>
            <strong>{percent(prediction.teamAWinProbability)}</strong>
            <p>
              Team B: {percent(prediction.teamBWinProbability)}. Favorite: {favoriteTeam}.
            </p>
          </div>

          <div className="draft-sim-win-meter" aria-label="Team A win-rate meter">
            <span style={{ width: teamAWidth }} />
          </div>

          <div className="draft-sim-result-grid">
            <article>
              <span className="ops-label">Team A side</span>
              <strong>{prediction.teamASide}</strong>
            </article>
            <article>
              <span className="ops-label">Threshold</span>
              <strong>{prediction.modelThreshold.toFixed(3)}</strong>
            </article>
            <article>
              <span className="ops-label">Model date</span>
              <strong>{modelDateLabel(prediction.modelCreatedAt)}</strong>
            </article>
          </div>
        </>
      ) : (
        <div className="draft-sim-result-empty">
          <Swords size={28} />
          <strong>{ready ? "Draft ready." : "Complete both drafts."}</strong>
          <p>
            {ready
              ? "Run the prediction model to calculate Team A's win-rate estimate."
              : `Team A side is set to ${teamASide}. Fill all 10 hero slots before running the model.`}
          </p>
        </div>
      )}
    </aside>
  );
}
