import { z } from "zod";

// The contract Claude must return. Sent to the API as a structured-output
// schema, then normalized and validated on our side before reaching the UI.

const TREATMENT_KINDS = ["organic", "chemical", "care"] as const;
const CONDITION_STATUSES = ["ok", "warn", "unknown"] as const;
const CATEGORIES = [
  "nutrient",
  "water",
  "light",
  "pest",
  "disease",
  "temperature",
  "transplant",
  "roots",
  "other",
] as const;
const TOXICITY = ["safe", "mild", "toxic", "unknown"] as const;

const Percent = z.number().int().describe("Integer from 0 to 100");

const ConditionSchema = z.object({
  ideal: z.string().describe("Ideal value for this species, in Hebrew"),
  current: z
    .string()
    .describe("What we know about the user's current situation, in Hebrew. 'לא ידוע' if unknown"),
  status: z.enum(CONDITION_STATUSES),
});

const TreatmentStepSchema = z.object({
  action: z.string().describe("Short imperative instruction in Hebrew"),
  details: z.string().describe("How exactly to do it, quantities, in Hebrew"),
  kind: z.enum(TREATMENT_KINDS),
});

export const DiagnosisSchema = z.object({
  plant: z.object({
    hebrewName: z.string(),
    scientificName: z.string(),
    family: z.string(),
    confidence: Percent,
    alternatives: z.array(
      z.object({ hebrewName: z.string(), scientificName: z.string(), confidence: Percent }),
    ),
  }),
  healthScore: Percent,
  summary: z.string().describe("Two warm, plain-Hebrew sentences: what is going on and what matters most"),
  diagnosisConfidence: Percent,
  needsMoreInfo: z.boolean(),
  followUpQuestions: z.array(z.string()).describe("At most 3 questions"),
  requestedPhoto: z
    .string()
    .nullable()
    .describe("A specific extra photo that would settle the diagnosis, or null"),
  diagnoses: z.array(
    z.object({
      issue: z.string(),
      category: z.enum(CATEGORIES),
      likelihood: Percent,
      visualEvidence: z.array(z.string()),
      reasoning: z.string(),
      differential: z.array(z.string()),
    }),
  ),
  treatmentPlan: z.object({
    now: z.array(TreatmentStepSchema),
    thisWeek: z.array(TreatmentStepSchema),
    thisMonth: z.array(TreatmentStepSchema),
  }),
  idealConditions: z.object({
    light: ConditionSchema,
    watering: ConditionSchema,
    temperature: ConditionSchema,
    humidity: ConditionSchema,
    soil: ConditionSchema,
    ph: ConditionSchema,
    fertilizer: ConditionSchema,
    potAndDrainage: ConditionSchema,
    pruning: ConditionSchema,
  }),
  weatherAdvice: z.array(z.string()),
  toxicity: z.object({
    pets: z.enum(TOXICITY),
    children: z.enum(TOXICITY),
    notes: z.string(),
  }),
  warningSigns: z.array(z.string()),
  sources: z.array(z.string()),
});

export type Diagnosis = z.infer<typeof DiagnosisSchema>;
export type ConditionKey = keyof Diagnosis["idealConditions"];
export type TreatmentStep = z.infer<typeof TreatmentStepSchema>;

// JSON Schema for output_config.format. Built here rather than with the SDK's
// zod helper because that helper (as of 0.133) demotes `enum` to a description,
// leaving the allowed values unenforced. Keywords the API does not support
// (numeric ranges, array sizes, string lengths) are dropped.
const UNSUPPORTED_KEYS = new Set([
  "$schema",
  "minimum",
  "maximum",
  "exclusiveMinimum",
  "exclusiveMaximum",
  "multipleOf",
  "minLength",
  "maxLength",
  "pattern",
  "maxItems",
  "minItems",
  "uniqueItems",
]);

function toApiSchema(node: unknown): unknown {
  if (Array.isArray(node)) return node.map(toApiSchema);
  if (!node || typeof node !== "object") return node;
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(node)) {
    if (UNSUPPORTED_KEYS.has(key)) continue;
    if (key === "properties" && value && typeof value === "object") {
      out.properties = Object.fromEntries(
        Object.entries(value).map(([k, v]) => [k, toApiSchema(v)]),
      );
    } else {
      out[key] = toApiSchema(value);
    }
  }
  if (out.type === "object") {
    out.additionalProperties = false;
    out.required = Object.keys((out.properties as object) ?? {});
  }
  return out;
}

export const DIAGNOSIS_JSON_SCHEMA = toApiSchema(z.toJSONSchema(DiagnosisSchema)) as Record<
  string,
  unknown
>;

// Small deviations (a score of 105, a fourth question, "Organic") should not
// throw away an otherwise good diagnosis, so fix them up before validating.
function clampPercent(v: unknown): unknown {
  if (typeof v !== "number" || Number.isNaN(v)) return v;
  const n = v > 0 && v <= 1 && !Number.isInteger(v) ? v * 100 : v;
  return Math.round(Math.min(100, Math.max(0, n)));
}

function fixEnum<T extends readonly string[]>(v: unknown, allowed: T, fallback: T[number]): unknown {
  if (typeof v !== "string") return fallback;
  const lower = v.trim().toLowerCase();
  return allowed.includes(lower) ? lower : fallback;
}

type Json = Record<string, unknown>;
const obj = (v: unknown): Json => (v && typeof v === "object" && !Array.isArray(v) ? (v as Json) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);

function normalize(raw: unknown): unknown {
  const d = obj(raw);
  const plant = obj(d.plant);
  const tox = obj(d.toxicity);
  const plan = obj(d.treatmentPlan);
  const conditions = obj(d.idealConditions);
  const steps = (v: unknown) =>
    arr(v).map((s) => ({ ...obj(s), kind: fixEnum(obj(s).kind, TREATMENT_KINDS, "care") }));

  return {
    ...d,
    plant: {
      ...plant,
      confidence: clampPercent(plant.confidence),
      alternatives: arr(plant.alternatives).map((a) => ({ ...obj(a), confidence: clampPercent(obj(a).confidence) })),
    },
    healthScore: clampPercent(d.healthScore),
    diagnosisConfidence: clampPercent(d.diagnosisConfidence),
    followUpQuestions: arr(d.followUpQuestions).slice(0, 3),
    requestedPhoto: typeof d.requestedPhoto === "string" && d.requestedPhoto.trim() ? d.requestedPhoto : null,
    diagnoses: arr(d.diagnoses).map((x) => ({
      ...obj(x),
      category: fixEnum(obj(x).category, CATEGORIES, "other"),
      likelihood: clampPercent(obj(x).likelihood),
      visualEvidence: arr(obj(x).visualEvidence),
      differential: arr(obj(x).differential),
    })),
    treatmentPlan: { now: steps(plan.now), thisWeek: steps(plan.thisWeek), thisMonth: steps(plan.thisMonth) },
    idealConditions: Object.fromEntries(
      Object.entries(conditions).map(([k, c]) => [k, { ...obj(c), status: fixEnum(obj(c).status, CONDITION_STATUSES, "unknown") }]),
    ),
    weatherAdvice: arr(d.weatherAdvice),
    toxicity: {
      ...tox,
      pets: fixEnum(tox.pets, TOXICITY, "unknown"),
      children: fixEnum(tox.children, TOXICITY, "unknown"),
      notes: typeof tox.notes === "string" ? tox.notes : "",
    },
    warningSigns: arr(d.warningSigns),
    sources: arr(d.sources),
  };
}

export function parseDiagnosis(raw: unknown) {
  return DiagnosisSchema.safeParse(normalize(raw));
}
