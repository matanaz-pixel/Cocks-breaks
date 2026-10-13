import { z } from "zod";
import type { Diagnosis } from "./schema";

// Shared between the browser and the API route.

export const PHOTO_SLOTS = ["whole", "closeup", "underside", "soil"] as const;
export type PhotoSlot = (typeof PHOTO_SLOTS)[number];

export const QuestionnaireSchema = z.object({
  placement: z.enum(["indoor", "outdoor", "balcony"]).nullable(),
  exposure: z.enum(["north", "south", "east", "west"]).nullable(),
  container: z.enum(["pot", "ground"]).nullable(),
  potSize: z.enum(["small", "medium", "large"]).nullable(),
  drainageHole: z.enum(["yes", "no", "unknown"]).nullable(),
  wateringFrequency: z.string().max(200).nullable(),
  lastWatered: z.string().max(200).nullable(),
  lastFertilized: z.string().max(200).nullable(),
  recentChanges: z.array(z.enum(["moved", "repotted", "pruned", "heatwave", "cold"])),
  problemDuration: z.string().max(200).nullable(),
  spreading: z.enum(["yes", "no", "unknown"]).nullable(),
  water: z.enum(["tap", "filtered", "rain"]).nullable(),
  petsOrKids: z.enum(["pets", "kids", "both", "none"]).nullable(),
  description: z.string().max(2000).nullable(),
});
export type Questionnaire = z.infer<typeof QuestionnaireSchema>;

export const EMPTY_QUESTIONNAIRE: Questionnaire = {
  placement: null,
  exposure: null,
  container: null,
  potSize: null,
  drainageHole: null,
  wateringFrequency: null,
  lastWatered: null,
  lastFertilized: null,
  recentChanges: [],
  problemDuration: null,
  spreading: null,
  water: null,
  petsOrKids: null,
  description: null,
};

export const LocationSchema = z.union([
  z.object({ kind: z.literal("coords"), lat: z.number(), lon: z.number() }),
  z.object({ kind: z.literal("city"), name: z.string().min(1).max(100) }),
]);
export type UserLocation = z.infer<typeof LocationSchema>;

export const DiagnoseRequestSchema = z.object({
  slots: z.array(z.enum(PHOTO_SLOTS)).min(1).max(4),
  questionnaire: QuestionnaireSchema,
  location: LocationSchema.nullable(),
  followUp: z
    .array(z.object({ question: z.string().max(500), answer: z.string().max(1000) }))
    .max(3)
    .nullable(),
  // Set after one follow-up round, or when the user asks to skip questions.
  forceFinal: z.boolean(),
  confirmedSpecies: z.string().max(200).nullable(),
  deep: z.boolean(),
});
export type DiagnoseRequest = z.infer<typeof DiagnoseRequestSchema>;

export type SourceName = "plantnet" | "weather" | "claude";

export interface PlantNetCandidate {
  scientificName: string;
  genus: string;
  family: string;
  commonNames: string[];
  score: number; // 0..1
}

export interface WeatherSummary {
  placeName: string | null;
  current: {
    temperature: number;
    humidity: number;
    precipitation: number;
    uvIndex: number | null;
    windSpeed: number;
  };
  daily: Array<{
    date: string;
    tMax: number;
    tMin: number;
    precipitation: number;
    precipitationProbability: number | null;
    uvIndexMax: number | null;
    et0: number | null;
  }>;
}

export interface SpeciesConflict {
  claude: { name: string; scientificName: string; confidence: number };
  plantnet: { name: string; scientificName: string; confidence: number };
}

export interface DiagnoseMeta {
  model: string;
  durationMs: number;
  estimatedCostUsd: number;
  missingSources: SourceName[];
  plantnet: PlantNetCandidate[];
  weather: WeatherSummary | null;
  conflict: SpeciesConflict | null;
}

// Newline-delimited JSON events streamed from /api/diagnose.
export type DiagnoseEvent =
  | { type: "stage"; stage: SourceName; status: "start" | "done" | "failed"; note?: string }
  | { type: "result"; data: Diagnosis; meta: DiagnoseMeta }
  | { type: "error"; message: string };
