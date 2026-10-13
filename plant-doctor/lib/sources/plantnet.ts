import { z } from "zod";
import { NonRetryableError } from "../adapter";
import type { PhotoSlot, PlantNetCandidate } from "../types";

// Pl@ntNet identify API: https://my.plantnet.org/doc/openapi
const ENDPOINT = "https://my-api.plantnet.org/v2/identify/all";

const ORGAN_BY_SLOT: Record<PhotoSlot, string> = {
  whole: "auto",
  closeup: "leaf",
  underside: "leaf",
  soil: "auto",
};

const NameSchema = z.object({ scientificNameWithoutAuthor: z.string() });
const ResponseSchema = z.object({
  results: z.array(
    z.object({
      score: z.number(),
      species: z.object({
        scientificNameWithoutAuthor: z.string(),
        genus: NameSchema,
        family: NameSchema,
        commonNames: z.array(z.string()).default([]),
      }),
    }),
  ),
});

export async function identifyWithPlantNet(
  images: Array<{ slot: PhotoSlot; blob: Blob }>,
  signal: AbortSignal,
): Promise<PlantNetCandidate[]> {
  const apiKey = process.env.PLANTNET_API_KEY;
  if (!apiKey) throw new NonRetryableError("PLANTNET_API_KEY is not set");

  const form = new FormData();
  for (const { slot, blob } of images) {
    form.append("images", blob, `${slot}.jpg`);
    form.append("organs", ORGAN_BY_SLOT[slot]);
  }

  const url = `${ENDPOINT}?api-key=${encodeURIComponent(apiKey)}&nb-results=5&lang=en`;
  const res = await fetch(url, { method: "POST", body: form, signal });

  // 404 means "no species matched", which is an answer, not a failure.
  if (res.status === 404) return [];
  if (!res.ok) {
    const message = `HTTP ${res.status}: ${(await res.text().catch(() => "")).slice(0, 200)}`;
    if (res.status >= 400 && res.status < 500 && res.status !== 429) {
      throw new NonRetryableError(message);
    }
    throw new Error(message);
  }

  const parsed = ResponseSchema.parse(await res.json());
  return parsed.results.map((r) => ({
    scientificName: r.species.scientificNameWithoutAuthor,
    genus: r.species.genus.scientificNameWithoutAuthor,
    family: r.species.family.scientificNameWithoutAuthor,
    commonNames: r.species.commonNames.slice(0, 3),
    score: r.score,
  }));
}
