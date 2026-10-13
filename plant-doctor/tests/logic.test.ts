import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { callSource, NonRetryableError } from "../lib/adapter";
import { findConflict } from "../lib/conflict";
import { estimateCostUsd } from "../lib/cost";
import { findIsraeliCity } from "../lib/israelCities";
import { checkRateLimit } from "../lib/rateLimit";
import { DIAGNOSIS_JSON_SCHEMA, parseDiagnosis, type Diagnosis } from "../lib/schema";
import { DiagnoseRequestSchema, EMPTY_QUESTIONNAIRE } from "../lib/types";

const condition = { ideal: "x", current: "y", status: "ok" };
function validDiagnosis(): Record<string, unknown> {
  return {
    plant: { hebrewName: "פיקוס", scientificName: "Ficus lyrata", family: "Moraceae", confidence: 90, alternatives: [] },
    healthScore: 60,
    summary: "s",
    diagnosisConfidence: 80,
    needsMoreInfo: false,
    followUpQuestions: [],
    requestedPhoto: null,
    diagnoses: [{ issue: "i", category: "water", likelihood: 70, visualEvidence: ["e"], reasoning: "r", differential: [] }],
    treatmentPlan: { now: [{ action: "a", details: "d", kind: "care" }], thisWeek: [], thisMonth: [] },
    idealConditions: Object.fromEntries(
      ["light", "watering", "temperature", "humidity", "soil", "ph", "fertilizer", "potAndDrainage", "pruning"].map((k) => [k, condition]),
    ),
    weatherAdvice: [],
    toxicity: { pets: "safe", children: "safe", notes: "" },
    warningSigns: [],
    sources: [],
  };
}

function walk(node: unknown, visit: (n: Record<string, unknown>) => void) {
  if (Array.isArray(node)) node.forEach((n) => walk(n, visit));
  else if (node && typeof node === "object") {
    visit(node as Record<string, unknown>);
    Object.values(node).forEach((n) => walk(n, visit));
  }
}

describe("structured-output schema sent to the API", () => {
  it("keeps enums as real constraints", () => {
    const enums: unknown[] = [];
    walk(DIAGNOSIS_JSON_SCHEMA, (n) => n.enum && enums.push(n.enum));
    assert.ok(enums.some((e) => JSON.stringify(e) === JSON.stringify(["organic", "chemical", "care"])));
    assert.ok(enums.some((e) => JSON.stringify(e).includes("transplant")));
  });

  it("contains no keywords the API rejects, and every object is closed and fully required", () => {
    walk(DIAGNOSIS_JSON_SCHEMA, (n) => {
      for (const bad of ["minimum", "maximum", "maxItems", "minLength", "maxLength", "$schema"]) {
        assert.equal(bad in n, false, `found ${bad}`);
      }
      if (n.type === "object") {
        assert.equal(n.additionalProperties, false);
        assert.deepEqual(n.required, Object.keys(n.properties as object));
      }
    });
  });
});

describe("parseDiagnosis", () => {
  it("accepts a valid diagnosis unchanged", () => {
    const r = parseDiagnosis(validDiagnosis());
    assert.ok(r.success);
    assert.equal(r.data.plant.confidence, 90);
  });

  it("repairs small deviations instead of failing the whole diagnosis", () => {
    const raw = validDiagnosis();
    raw.healthScore = 105;
    raw.diagnosisConfidence = 0.65;
    raw.followUpQuestions = ["a", "b", "c", "d"];
    raw.requestedPhoto = "  ";
    (raw.treatmentPlan as Diagnosis["treatmentPlan"]).now[0].kind = "Organic" as "organic";
    (raw.diagnoses as Diagnosis["diagnoses"])[0].category = "fungus" as "disease";
    (raw.toxicity as Record<string, string>).pets = "TOXIC";
    const r = parseDiagnosis(raw);
    assert.ok(r.success, r.success ? "" : r.error.message);
    assert.equal(r.data.healthScore, 100);
    assert.equal(r.data.diagnosisConfidence, 65);
    assert.equal(r.data.followUpQuestions.length, 3);
    assert.equal(r.data.requestedPhoto, null);
    assert.equal(r.data.treatmentPlan.now[0].kind, "organic");
    assert.equal(r.data.diagnoses[0].category, "other");
    assert.equal(r.data.toxicity.pets, "toxic");
  });

  it("still rejects output that is missing whole sections", () => {
    const raw = validDiagnosis();
    delete raw.plant;
    assert.equal(parseDiagnosis(raw).success, false);
    assert.equal(parseDiagnosis(null).success, false);
  });
});

describe("request validation", () => {
  const base = {
    slots: ["whole"],
    questionnaire: EMPTY_QUESTIONNAIRE,
    location: null,
    followUp: null,
    forceFinal: false,
    confirmedSpecies: null,
    deep: false,
  };

  it("trims over-long text instead of rejecting the request", () => {
    const r = DiagnoseRequestSchema.safeParse({
      ...base,
      questionnaire: { ...EMPTY_QUESTIONNAIRE, wateringFrequency: "א".repeat(500), description: "ב".repeat(5000) },
      followUp: [{ question: "q".repeat(3000), answer: "a".repeat(3000) }],
    });
    assert.ok(r.success);
    assert.equal(r.data.questionnaire.wateringFrequency?.length, 200);
    assert.equal(r.data.questionnaire.description?.length, 2000);
    assert.equal(r.data.followUp?.[0].question.length, 1000);
  });

  it("accepts the extra follow-up photo slot and rejects unknown slots", () => {
    assert.ok(DiagnoseRequestSchema.safeParse({ ...base, slots: ["whole", "closeup", "underside", "soil", "extra"] }).success);
    assert.equal(DiagnoseRequestSchema.safeParse({ ...base, slots: ["selfie"] }).success, false);
    assert.equal(DiagnoseRequestSchema.safeParse({ ...base, slots: [] }).success, false);
  });
});

describe("callSource", () => {
  it("does not retry after a timeout", async () => {
    // AbortSignal.timeout does not keep Node alive on its own; a server does.
    const keepAlive = setTimeout(() => {}, 5_000);
    let calls = 0;
    const started = Date.now();
    const r = await callSource(
      "slow",
      (signal) => {
        calls++;
        return new Promise((_, reject) => signal.addEventListener("abort", () => reject(signal.reason)));
      },
      { timeoutMs: 200 },
    );
    assert.equal(r.ok, false);
    assert.equal(calls, 1);
    assert.ok(Date.now() - started < 1000);
    clearTimeout(keepAlive);
  });

  it("retries once on a quick failure and returns the data", async () => {
    let calls = 0;
    const r = await callSource("flaky", async () => {
      if (++calls === 1) throw new Error("boom");
      return 42;
    }, { timeoutMs: 1000 });
    assert.deepEqual(r, { ok: true, data: 42 });
  });

  it("does not retry non-retryable errors", async () => {
    let calls = 0;
    const r = await callSource("bad-key", async () => {
      calls++;
      throw new NonRetryableError("HTTP 401");
    }, { timeoutMs: 1000 });
    assert.equal(r.ok, false);
    assert.equal(calls, 1);
  });
});

describe("findIsraeliCity", () => {
  it("resolves common spellings", () => {
    assert.equal(findIsraeliCity("חיפה")?.name, "חיפה");
    assert.equal(findIsraeliCity("  תל-אביב  ")?.name, "תל אביב");
    assert.equal(findIsraeliCity('ת"א')?.name, "תל אביב");
    assert.equal(findIsraeliCity("קרית ביאליק")?.name, "קריית ביאליק");
    assert.equal(findIsraeliCity("Springfield"), null);
  });
});

describe("findConflict", () => {
  const diagnosis = parseDiagnosis(validDiagnosis());
  assert.ok(diagnosis.success);
  const candidate = (scientificName: string, score: number) => ({
    scientificName,
    genus: scientificName.split(" ")[0],
    family: "",
    commonNames: [],
    score,
  });

  it("ignores species-level disagreement within the same genus", () => {
    assert.equal(findConflict(diagnosis.data, [candidate("Ficus elastica", 0.9)]), null);
  });
  it("flags a confident genus-level disagreement", () => {
    assert.equal(findConflict(diagnosis.data, [candidate("Monstera deliciosa", 0.6)])?.plantnet.scientificName, "Monstera deliciosa");
  });
  it("ignores weak Pl@ntNet matches and empty results", () => {
    assert.equal(findConflict(diagnosis.data, [candidate("Monstera deliciosa", 0.1)]), null);
    assert.equal(findConflict(diagnosis.data, []), null);
  });
});

describe("rate limit and cost", () => {
  it("blocks after the limit within the window", () => {
    const key = `test-${Math.random()}`;
    for (let i = 0; i < 15; i++) assert.ok(checkRateLimit(key).ok);
    assert.equal(checkRateLimit(key).ok, false);
  });
  it("estimates cost from token usage", () => {
    assert.equal(estimateCostUsd("claude-sonnet-5-5", { input_tokens: 1_000_000, output_tokens: 0 }), 2);
    assert.equal(estimateCostUsd("unknown-model", { input_tokens: 5, output_tokens: 5 }), 0);
  });
});
