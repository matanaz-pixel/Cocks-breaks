// Live end-to-end check with real photos and real API keys.
//
//   npm run build && npm start            (in another terminal, with .env.local filled in)
//   node scripts/smoke-test.mjs photo1.jpg [photo2.jpg ...] [--city חיפה] [--url http://localhost:3000]
//
// Exits non-zero if no valid diagnosis arrives. Prints each stage with its
// timing, the missing sources, the duration against the 20-second target, and the cost.
import { readFileSync } from "node:fs";
import { basename } from "node:path";

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(name);
  return i === -1 ? fallback : args.splice(i, 2)[1];
};
const url = flag("--url", "http://localhost:3000");
const city = flag("--city", "תל אביב");
const deep = args.includes("--deep") && args.splice(args.indexOf("--deep"), 1);
const photos = args;
if (photos.length === 0 || photos.length > 4) {
  console.error("usage: node scripts/smoke-test.mjs <1-4 photos> [--city name] [--url base] [--deep]");
  process.exit(2);
}

const slots = ["whole", "closeup", "underside", "soil"].slice(0, photos.length);
const questionnaire = {
  placement: "indoor", exposure: "east", container: "pot", potSize: "medium", drainageHole: "yes",
  wateringFrequency: "פעם בשבוע", lastWatered: "לפני 3 ימים", lastFertilized: null, recentChanges: [],
  problemDuration: "שבועיים", spreading: "unknown", water: "tap", petsOrKids: "pets",
  description: "העלים מצהיבים בקצוות",
};
const form = new FormData();
form.append("meta", JSON.stringify({
  slots, questionnaire, location: { kind: "city", name: city },
  followUp: null, forceFinal: true, confirmedSpecies: null, deep: Boolean(deep),
}));
for (const p of photos) form.append("images", new Blob([readFileSync(p)], { type: "image/jpeg" }), basename(p));

const started = Date.now();
const t = () => `${((Date.now() - started) / 1000).toFixed(1)}s`;
const res = await fetch(`${url}/api/diagnose`, { method: "POST", body: form });
if (!res.ok) {
  console.error(`HTTP ${res.status}:`, await res.text());
  process.exit(1);
}

let result = null;
let buffer = "";
const decoder = new TextDecoder();
for await (const chunk of res.body) {
  buffer += decoder.decode(chunk, { stream: true });
  const lines = buffer.split("\n");
  buffer = lines.pop() ?? "";
  for (const line of lines.filter(Boolean)) {
    const e = JSON.parse(line);
    if (e.type === "stage") console.log(`[${t()}] ${e.stage}: ${e.status}${e.note ? ` (${e.note})` : ""}`);
    else if (e.type === "error") console.log(`[${t()}] ERROR: ${e.message}`);
    else result = e;
  }
}

if (!result) {
  console.error("FAIL: no diagnosis received");
  process.exit(1);
}
const { data: d, meta } = result;
const required = ["plant", "healthScore", "diagnoses", "treatmentPlan", "idealConditions", "toxicity"];
const missingFields = required.filter((k) => !(k in d));
console.log(`\nPlant: ${d.plant.hebrewName} (${d.plant.scientificName}), confidence ${d.plant.confidence}%`);
console.log(`Health ${d.healthScore}/100 · ${d.diagnoses.length} diagnoses · first: ${d.diagnoses[0]?.issue ?? "-"}`);
console.log(`Weather advice: ${d.weatherAdvice.length} items · Pl@ntNet candidates: ${meta.plantnet.length}`);
console.log(`Model ${meta.model} · ${(meta.durationMs / 1000).toFixed(1)}s (target < 20s) · ~$${meta.estimatedCostUsd}`);
console.log(`Missing sources: ${meta.missingSources.join(", ") || "none"}`);
if (missingFields.length) {
  console.error(`FAIL: result missing ${missingFields.join(", ")}`);
  process.exit(1);
}
console.log(meta.missingSources.length ? "PASS (with missing sources)" : "PASS");
