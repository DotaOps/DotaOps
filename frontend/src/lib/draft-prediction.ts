import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

const UNKNOWN_HERO_ID = 0;
const DEFAULT_MODEL_PATH = path.join(process.cwd(), "public", "models", "draft_best.json");
const LOCAL_WORKSPACE_MODEL_PATH = path.join(
  process.cwd(),
  "..",
  "DotaOps-prediction",
  "models",
  "draft_best.json"
);
const HERO_ICON_PUBLIC_DIR = path.join(process.cwd(), "public", "dota2_hero_icons");
const HERO_ICON_ALIASES: Record<string, string> = {
  "Outworld Devourer": "Outworld Destroyer"
};

export type DraftTeamSide = "radiant" | "dire" | "neutral";

export interface DraftHero {
  iconUrl: string | null;
  id: number;
  name: string;
  normalizedName: string;
}

export interface DraftPredictionInput {
  teamAHeroIds: number[];
  teamBHeroIds: number[];
  teamASide?: DraftTeamSide;
}

export interface DraftPredictionResult {
  modelCreatedAt: string | null;
  modelThreshold: number;
  teamASide: DraftTeamSide;
  teamAWinProbability: number;
  teamBWinProbability: number;
}

interface Calibration {
  offset: number;
  scale: number;
}

interface FeatureConfig {
  name?: string;
  num_features?: number;
  role_feature_names?: string[];
  role_profiles?: number[][];
  use_ally_pairs: boolean;
  use_counters: boolean;
  use_heroes: boolean;
  use_roles: boolean;
}

interface SparseDraftModel {
  bias: number;
  calibration?: Calibration | null;
  component_weight?: number;
  created_at_utc?: string;
  feature_config?: FeatureConfig;
  hero_ids: number[];
  hero_names: Record<string, string>;
  model_type: "sparse_logistic_regression";
  model_version: number;
  threshold?: number;
  weights: number[];
}

interface EnsembleDraftModel {
  components: SparseDraftModel[];
  created_at_utc?: string;
  hero_ids: number[];
  hero_names: Record<string, string>;
  model_type: "ensemble";
  model_version: number;
  threshold?: number;
}

type DraftModel = SparseDraftModel | EnsembleDraftModel;

interface FeatureSpace {
  allyPairOffset: number;
  config: FeatureConfig;
  counterOffset: number;
  heroIds: number[];
  heroOffset: number;
  heroToPosition: Map<number, number>;
  roleFeatureNames: string[];
  roleOffset: number;
  roleProfiles: number[][];
}

let cachedModel: DraftModel | null = null;
let cachedHeroes: DraftHero[] | null = null;

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function modelPathCandidates() {
  return [
    process.env.DOTAOPS_DRAFT_MODEL_PATH,
    DEFAULT_MODEL_PATH,
    LOCAL_WORKSPACE_MODEL_PATH
  ].filter((candidate): candidate is string => Boolean(candidate));
}

function loadJsonModel(pathname: string) {
  return JSON.parse(readFileSync(pathname, "utf-8")) as unknown;
}

function asDraftModel(value: unknown): DraftModel {
  if (!isRecord(value)) {
    throw new Error("Draft prediction model is malformed.");
  }

  const modelVersion = Number(value.model_version);

  if (modelVersion !== 1 && modelVersion !== 2) {
    throw new Error("Draft prediction model version is unsupported.");
  }

  if (value.model_type === "ensemble") {
    if (!Array.isArray(value.components)) {
      throw new Error("Draft prediction ensemble is missing components.");
    }

    return value as unknown as EnsembleDraftModel;
  }

  return value as unknown as SparseDraftModel;
}

function getDraftModel(): DraftModel {
  if (cachedModel) {
    return cachedModel;
  }

  const failures: string[] = [];

  for (const candidate of modelPathCandidates()) {
    try {
      cachedModel = asDraftModel(loadJsonModel(candidate));
      return cachedModel;
    } catch (error) {
      failures.push(`${candidate}: ${error instanceof Error ? error.message : "failed"}`);
    }
  }

  throw new Error(`Draft prediction model could not be loaded. ${failures.join(" | ")}`);
}

function normalizeHeroName(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/^npc_dota_hero_/, "")
    .replace(/[^a-z0-9]/g, "");
}

function publicHeroIconUrl(heroName: string) {
  const lookupName = HERO_ICON_ALIASES[heroName] ?? heroName;
  const candidates = [
    `${lookupName} icon dota2 gameasset.png`,
    `${lookupName} hero icon progress.png`,
    `${lookupName} mapicon dota2 gameasset.png`
  ];
  const iconFilename = candidates.find((candidate) =>
    existsSync(path.join(HERO_ICON_PUBLIC_DIR, candidate))
  );

  return iconFilename ? `/dota2_hero_icons/${encodeURIComponent(iconFilename)}` : null;
}

export function listDraftHeroes(): DraftHero[] {
  if (cachedHeroes) {
    return cachedHeroes;
  }

  const model = getDraftModel();
  cachedHeroes = Object.entries(model.hero_names)
    .map(([rawId, name]) => ({
      iconUrl: publicHeroIconUrl(name),
      id: Number(rawId),
      name,
      normalizedName: normalizeHeroName(name)
    }))
    .filter((hero) => hero.id !== UNKNOWN_HERO_ID && Number.isFinite(hero.id))
    .sort((a, b) => a.name.localeCompare(b.name));

  return cachedHeroes;
}

export function getDraftModelMetadata() {
  const model = getDraftModel();

  return {
    createdAt: model.created_at_utc ?? null,
    heroCount: listDraftHeroes().length,
    modelType: model.model_type,
    threshold: Number(model.threshold ?? 0.5),
    version: model.model_version
  };
}

function sigmoid(value: number) {
  const clipped = Math.max(-40, Math.min(40, value));
  return 1 / (1 + Math.exp(-clipped));
}

function calibrateScore(score: number, calibration?: Calibration | null) {
  if (!calibration) {
    return sigmoid(score);
  }

  return sigmoid(calibration.scale * score + calibration.offset);
}

function pairFeatureCount(heroCount: number) {
  return (heroCount * (heroCount - 1)) / 2;
}

function unorderedPairIndex(first: number, second: number, heroCount: number) {
  const low = Math.min(first, second);
  const high = Math.max(first, second);

  if (low === high) {
    throw new Error("Pair features require two different heroes.");
  }

  return (low * (2 * heroCount - low - 1)) / 2 + (high - low - 1);
}

function parseFeatureConfig(name: string): FeatureConfig {
  if (name === "heroes") {
    return {
      use_ally_pairs: false,
      use_counters: false,
      use_heroes: true,
      use_roles: false
    };
  }

  if (name === "pairs") {
    return {
      use_ally_pairs: true,
      use_counters: true,
      use_heroes: true,
      use_roles: false
    };
  }

  if (name === "pairs_roles") {
    return {
      use_ally_pairs: true,
      use_counters: true,
      use_heroes: true,
      use_roles: true
    };
  }

  throw new Error(`Unsupported draft prediction feature set: ${name}`);
}

function modelToFeatureSpace(model: SparseDraftModel): FeatureSpace {
  const config = model.feature_config ?? parseFeatureConfig("heroes");
  const heroIds = model.hero_ids.map(Number);
  const heroToPosition = new Map(heroIds.map((heroId, index) => [heroId, index]));
  const heroCount = heroIds.length;
  let offset = 0;

  const heroOffset = offset;
  if (config.use_heroes) {
    offset += heroCount;
  }

  const allyPairOffset = offset;
  if (config.use_ally_pairs) {
    offset += pairFeatureCount(heroCount);
  }

  const counterOffset = offset;
  if (config.use_counters) {
    offset += heroCount * heroCount;
  }

  const roleOffset = offset;
  const roleFeatureNames = config.role_feature_names ?? [];
  const roleProfiles = config.role_profiles ?? heroIds.map(() => []);

  return {
    allyPairOffset,
    config,
    counterOffset,
    heroIds,
    heroOffset,
    heroToPosition,
    roleFeatureNames,
    roleOffset,
    roleProfiles
  };
}

function draftFeatureEntries(
  teamA: number[],
  teamB: number[],
  space: FeatureSpace
): Array<[number, number]> {
  const heroCount = space.heroIds.length;
  const unknownPosition = space.heroToPosition.get(UNKNOWN_HERO_ID) ?? 0;
  const teamAPositions = teamA.map((heroId) => space.heroToPosition.get(heroId) ?? unknownPosition);
  const teamBPositions = teamB.map((heroId) => space.heroToPosition.get(heroId) ?? unknownPosition);
  const entries: Array<[number, number]> = [];

  if (space.config.use_heroes) {
    for (const position of teamAPositions) {
      entries.push([space.heroOffset + position, 1]);
    }

    for (const position of teamBPositions) {
      entries.push([space.heroOffset + position, -1]);
    }
  }

  if (space.config.use_ally_pairs) {
    for (let first = 0; first < teamAPositions.length; first += 1) {
      for (let second = first + 1; second < teamAPositions.length; second += 1) {
        entries.push([
          space.allyPairOffset + unorderedPairIndex(teamAPositions[first], teamAPositions[second], heroCount),
          1
        ]);
      }
    }

    for (let first = 0; first < teamBPositions.length; first += 1) {
      for (let second = first + 1; second < teamBPositions.length; second += 1) {
        entries.push([
          space.allyPairOffset + unorderedPairIndex(teamBPositions[first], teamBPositions[second], heroCount),
          -1
        ]);
      }
    }
  }

  if (space.config.use_counters) {
    for (const teamAPosition of teamAPositions) {
      for (const teamBPosition of teamBPositions) {
        entries.push([space.counterOffset + teamAPosition * heroCount + teamBPosition, 1]);
        entries.push([space.counterOffset + teamBPosition * heroCount + teamAPosition, -1]);
      }
    }
  }

  if (space.config.use_roles) {
    const roleValues = new Array(space.roleFeatureNames.length).fill(0) as number[];

    for (const position of teamAPositions) {
      const profile = space.roleProfiles[position] ?? [];
      for (let index = 0; index < roleValues.length; index += 1) {
        roleValues[index] += profile[index] ?? 0;
      }
    }

    for (const position of teamBPositions) {
      const profile = space.roleProfiles[position] ?? [];
      for (let index = 0; index < roleValues.length; index += 1) {
        roleValues[index] -= profile[index] ?? 0;
      }
    }

    roleValues.forEach((value, index) => {
      if (value !== 0) {
        entries.push([space.roleOffset + index, value]);
      }
    });
  }

  return entries;
}

function dotEntries(weights: number[], entries: Array<[number, number]>) {
  return entries.reduce((total, [index, value]) => total + (weights[index] ?? 0) * value, 0);
}

function sparseModelProbability(
  model: SparseDraftModel,
  teamA: number[],
  teamB: number[],
  teamASide: DraftTeamSide
): number {
  if (!model.feature_config || model.model_version === 1) {
    const heroToColumn = new Map(model.hero_ids.map((heroId, index) => [Number(heroId), index]));
    const unknownColumn = heroToColumn.get(UNKNOWN_HERO_ID) ?? 0;
    const features = new Array(model.hero_ids.length).fill(0) as number[];

    for (const heroId of teamA) {
      features[heroToColumn.get(heroId) ?? unknownColumn] += 1;
    }

    for (const heroId of teamB) {
      features[heroToColumn.get(heroId) ?? unknownColumn] -= 1;
    }

    const rawScore = features.reduce(
      (total, value, index) => total + value * (model.weights[index] ?? 0),
      0
    );

    if (teamASide === "radiant") {
      return sigmoid(rawScore + model.bias);
    }

    if (teamASide === "dire") {
      return 1 - sigmoid(-rawScore + model.bias);
    }

    return sigmoid(rawScore);
  }

  const space = modelToFeatureSpace(model);
  const score = dotEntries(model.weights, draftFeatureEntries(teamA, teamB, space)) + model.bias;
  const probability = calibrateScore(score, model.calibration);

  if (teamASide === "radiant") {
    return probability;
  }

  if (teamASide === "dire") {
    const reverseScore = dotEntries(model.weights, draftFeatureEntries(teamB, teamA, space)) + model.bias;
    return 1 - calibrateScore(reverseScore, model.calibration);
  }

  return calibrateScore(score - model.bias, null);
}

function modelProbability(
  model: DraftModel,
  teamA: number[],
  teamB: number[],
  teamASide: DraftTeamSide
): number {
  if (model.model_type !== "ensemble") {
    return sparseModelProbability(model, teamA, teamB, teamASide);
  }

  let weightedProbability = 0;
  let totalWeight = 0;

  for (const component of model.components) {
    const componentWeight = Number(component.component_weight ?? 1);
    weightedProbability += componentWeight * sparseModelProbability(component, teamA, teamB, teamASide);
    totalWeight += componentWeight;
  }

  if (totalWeight <= 0) {
    throw new Error("Draft prediction ensemble has no usable component weights.");
  }

  return weightedProbability / totalWeight;
}

function validateDraft(teamA: number[], teamB: number[]) {
  if (teamA.length !== 5) {
    throw new Error(`Team A must contain 5 heroes, got ${teamA.length}.`);
  }

  if (teamB.length !== 5) {
    throw new Error(`Team B must contain 5 heroes, got ${teamB.length}.`);
  }

  if (new Set(teamA).size !== 5) {
    throw new Error("Team A contains duplicate heroes.");
  }

  if (new Set(teamB).size !== 5) {
    throw new Error("Team B contains duplicate heroes.");
  }

  const overlap = teamA.filter((heroId) => teamB.includes(heroId));

  if (overlap.length > 0) {
    throw new Error(`Heroes cannot appear on both teams: ${overlap.join(", ")}.`);
  }
}

function normalizeHeroIds(value: unknown, fieldName: string) {
  if (!Array.isArray(value)) {
    throw new Error(`${fieldName} must be an array of hero IDs.`);
  }

  return value.map((item) => {
    const heroId = Number(item);

    if (!Number.isInteger(heroId) || heroId <= 0) {
      throw new Error(`${fieldName} contains an invalid hero ID.`);
    }

    return heroId;
  });
}

function normalizeTeamSide(value: unknown): DraftTeamSide {
  if (value === "dire" || value === "neutral" || value === "radiant" || value === undefined) {
    return value ?? "radiant";
  }

  throw new Error("teamASide must be radiant, dire, or neutral.");
}

export function predictDraft(input: DraftPredictionInput): DraftPredictionResult {
  const model = getDraftModel();
  const teamAHeroIds = normalizeHeroIds(input.teamAHeroIds, "teamAHeroIds");
  const teamBHeroIds = normalizeHeroIds(input.teamBHeroIds, "teamBHeroIds");
  const teamASide = normalizeTeamSide(input.teamASide);

  validateDraft(teamAHeroIds, teamBHeroIds);

  const teamAWinProbability = modelProbability(model, teamAHeroIds, teamBHeroIds, teamASide);

  return {
    modelCreatedAt: model.created_at_utc ?? null,
    modelThreshold: Number(model.threshold ?? 0.5),
    teamASide,
    teamAWinProbability,
    teamBWinProbability: 1 - teamAWinProbability
  };
}
