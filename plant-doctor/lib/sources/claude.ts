import Anthropic from "@anthropic-ai/sdk";
import { estimateCostUsd } from "../cost";
import { DIAGNOSIS_JSON_SCHEMA, parseDiagnosis, type Diagnosis } from "../schema";
import { SYSTEM_PROMPT } from "../systemPrompt";
import type {
  DiagnoseRequest,
  PhotoSlot,
  PlantNetCandidate,
  Questionnaire,
  WeatherSummary,
} from "../types";

const SLOT_LABEL: Record<PhotoSlot, string> = {
  whole: "Photo: the whole plant",
  closeup: "Photo: close-up of the affected leaf",
  underside: "Photo: underside of the leaf",
  soil: "Photo: soil / stem base",
  extra: "Photo: the extra photo you asked for in your follow-up",
};

export function pickModel(deep: boolean): { model: string; effort: "low" | "medium" | "high" } {
  // Medium keeps the deep check inside the function's time limit.
  if (deep) return { model: process.env.CLAUDE_DEEP_MODEL || "claude-opus-5-5", effort: "medium" };
  const effort = process.env.CLAUDE_EFFORT;
  return {
    model: process.env.CLAUDE_MODEL || "claude-sonnet-5-5",
    effort: effort === "low" || effort === "high" ? effort : "medium",
  };
}

export interface ClaudeInput {
  images: Array<{ slot: PhotoSlot; base64: string; mediaType: "image/jpeg" | "image/png" | "image/webp" }>;
  request: DiagnoseRequest;
  plantnet: PlantNetCandidate[] | null; // null = source unavailable
  weather: WeatherSummary | null;
}

export interface ClaudeOutput {
  diagnosis: Diagnosis;
  model: string;
  estimatedCostUsd: number;
}

function describeQuestionnaire(q: Questionnaire): string {
  const lines = Object.entries(q)
    .filter(([, v]) => v !== null && !(Array.isArray(v) && v.length === 0))
    .map(([k, v]) => `- ${k}: ${Array.isArray(v) ? v.join(", ") : v}`);
  return lines.length ? lines.join("\n") : "(the user did not fill in the questionnaire)";
}

function buildContext({ request, plantnet, weather }: ClaudeInput): string {
  const parts: string[] = [];
  parts.push(`Today: ${new Date().toISOString().slice(0, 10)}`);
  parts.push(`Questionnaire (values are enum keys or the user's own Hebrew words):\n${describeQuestionnaire(request.questionnaire)}`);

  if (plantnet === null) {
    parts.push("Pl@ntNet: unavailable for this request. Identify from the photos alone.");
  } else if (plantnet.length === 0) {
    parts.push("Pl@ntNet: no species matched these photos.");
  } else {
    const rows = plantnet.map(
      (c) => `- ${c.scientificName} (${c.family}), score ${(c.score * 100).toFixed(0)}%${c.commonNames.length ? `, a.k.a. ${c.commonNames.join(" / ")}` : ""}`,
    );
    parts.push(`Pl@ntNet candidates:\n${rows.join("\n")}`);
  }

  if (weather) {
    const days = weather.daily
      .map(
        (d) =>
          `- ${d.date}: ${d.tMin}–${d.tMax}°C, rain ${d.precipitation}mm${d.precipitationProbability !== null ? ` (${d.precipitationProbability}%)` : ""}, UV ${d.uvIndexMax ?? "?"}, ET0 ${d.et0 ?? "?"}mm`,
      )
      .join("\n");
    parts.push(
      `Weather now${weather.placeName ? ` in ${weather.placeName}` : ""}: ${weather.current.temperature}°C, humidity ${weather.current.humidity}%, wind ${weather.current.windSpeed} km/h, UV ${weather.current.uvIndex ?? "?"}.\n7-day forecast:\n${days}`,
    );
  } else {
    parts.push("Weather: unavailable. Give general seasonal advice and say that live weather was not available.");
  }

  if (request.confirmedSpecies) {
    parts.push(`The user confirmed the species is: ${request.confirmedSpecies}.`);
  }
  if (request.followUp?.length) {
    const qa = request.followUp.map((f) => `Q: ${f.question}\nA: ${f.answer || "(no answer)"}`).join("\n");
    parts.push(`Answers to your earlier follow-up questions:\n${qa}`);
  }
  parts.push(
    request.forceFinal
      ? "This is the final round: do not ask more questions; give your best assessment."
      : "You may ask follow-up questions if your confidence is below 70.",
  );
  return parts.join("\n\n");
}

export class RefusalError extends Error {}

// A second attempt is only worth starting if it can plausibly finish in time.
const MIN_MS_FOR_RETRY = 25_000;

export async function diagnoseWithClaude(
  input: ClaudeInput,
  signal: AbortSignal,
  deadline: number,
  onWriting: () => void,
): Promise<ClaudeOutput> {
  const client = new Anthropic({ maxRetries: 1 });
  const { model, effort } = pickModel(input.request.deep);

  const content: Anthropic.Beta.BetaContentBlockParam[] = [];
  for (const img of input.images) {
    content.push({ type: "text", text: SLOT_LABEL[img.slot] });
    content.push({ type: "image", source: { type: "base64", media_type: img.mediaType, data: img.base64 } });
  }
  content.push({ type: "text", text: buildContext(input) });

  let totalCost = 0;
  let lastError: unknown;
  // One extra attempt if the output fails validation.
  for (let attempt = 0; attempt < 2; attempt++) {
    const remaining = deadline - Date.now();
    if (attempt > 0 && remaining < MIN_MS_FOR_RETRY) break;
    try {
      const stream = client.beta.messages.stream(
        {
          model,
          max_tokens: 16000,
          system: SYSTEM_PROMPT,
          messages: [{ role: "user", content }],
          output_config: { effort, format: { type: "json_schema", schema: DIAGNOSIS_JSON_SCHEMA } },
          // On a safety decline, the API re-runs the request on a fallback model.
          betas: ["server-side-fallback-2026-07-01"],
          fallbacks: "default",
        },
        { signal: AbortSignal.any([signal, AbortSignal.timeout(Math.max(remaining, 1_000))]) },
      );

      let announced = false;
      stream.on("streamEvent", (event) => {
        if (!announced && event.type === "content_block_start" && event.content_block.type === "text") {
          announced = true;
          onWriting();
        }
      });

      const message = await stream.finalMessage();
      totalCost += estimateCostUsd(model, message.usage);

      if (message.stop_reason === "refusal") {
        throw new RefusalError("Claude declined to answer this request");
      }
      const text = message.content
        .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
        .map((b) => b.text)
        .join("");
      if (message.stop_reason === "max_tokens") {
        throw new Error("output was cut off at max_tokens");
      }
      const parsed = parseDiagnosis(JSON.parse(text));
      if (!parsed.success) {
        throw new Error(`schema validation failed: ${parsed.error.message.slice(0, 300)}`);
      }
      console.info(`[claude] ${model} ok, cost ~$${totalCost}`);
      return { diagnosis: parsed.data, model: message.model, estimatedCostUsd: totalCost };
    } catch (err) {
      lastError = err;
      // Auth, permission and bad-request errors will not fix themselves.
      if (
        err instanceof Anthropic.AuthenticationError ||
        err instanceof Anthropic.PermissionDeniedError ||
        err instanceof Anthropic.BadRequestError ||
        err instanceof RefusalError ||
        signal.aborted
      ) {
        break;
      }
      console.warn(`[claude] attempt ${attempt + 1} failed:`, err instanceof Error ? err.message : err);
    }
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}
