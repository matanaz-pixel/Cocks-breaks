import { z } from "zod";

// The contract Claude must return. Sent to the API as a structured-output
// schema and validated again on our side before anything reaches the UI.

const ConditionSchema = z.object({
  ideal: z.string().describe("Ideal value for this species, in Hebrew"),
  current: z
    .string()
    .describe("What we know about the user's current situation, in Hebrew. 'לא ידוע' if unknown"),
  status: z.enum(["ok", "warn", "unknown"]),
});

const TreatmentStepSchema = z.object({
  action: z.string().describe("Short imperative instruction in Hebrew"),
  details: z.string().describe("How exactly to do it, quantities, in Hebrew"),
  kind: z.enum(["organic", "chemical", "care"]),
});

export const DiagnosisSchema = z.object({
  plant: z.object({
    hebrewName: z.string(),
    scientificName: z.string(),
    family: z.string(),
    confidence: z.number().int().min(0).max(100),
    alternatives: z.array(
      z.object({
        hebrewName: z.string(),
        scientificName: z.string(),
        confidence: z.number().int().min(0).max(100),
      }),
    ),
  }),
  healthScore: z.number().int().min(0).max(100),
  summary: z.string().describe("Two warm, plain-Hebrew sentences: what is going on and what matters most"),
  diagnosisConfidence: z.number().int().min(0).max(100),
  needsMoreInfo: z.boolean(),
  followUpQuestions: z.array(z.string()).max(3),
  requestedPhoto: z
    .string()
    .nullable()
    .describe("A specific extra photo that would settle the diagnosis, or null"),
  diagnoses: z.array(
    z.object({
      issue: z.string(),
      category: z.enum([
        "nutrient",
        "water",
        "light",
        "pest",
        "disease",
        "temperature",
        "transplant",
        "roots",
        "other",
      ]),
      likelihood: z.number().int().min(0).max(100),
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
    pets: z.enum(["safe", "mild", "toxic", "unknown"]),
    children: z.enum(["safe", "mild", "toxic", "unknown"]),
    notes: z.string(),
  }),
  warningSigns: z.array(z.string()),
  sources: z.array(z.string()),
});

export type Diagnosis = z.infer<typeof DiagnosisSchema>;
export type ConditionKey = keyof Diagnosis["idealConditions"];
export type TreatmentStep = z.infer<typeof TreatmentStepSchema>;
