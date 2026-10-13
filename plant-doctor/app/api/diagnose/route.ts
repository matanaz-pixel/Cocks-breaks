import Anthropic from "@anthropic-ai/sdk";
import { callSource } from "@/lib/adapter";
import { checkRateLimit } from "@/lib/rateLimit";
import { diagnoseWithClaude, pickModel } from "@/lib/sources/claude";
import { getWeather } from "@/lib/sources/openMeteo";
import { identifyWithPlantNet } from "@/lib/sources/plantnet";
import {
  DiagnoseRequestSchema,
  type DiagnoseEvent,
  type PhotoSlot,
  type PlantNetCandidate,
  type SourceName,
  type SpeciesConflict,
} from "@/lib/types";
import type { Diagnosis } from "@/lib/schema";

export const maxDuration = 60;

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
type AllowedType = (typeof ALLOWED_TYPES)[number];

function errorResponse(status: number, message: string, headers?: HeadersInit) {
  return Response.json({ error: message }, { status, headers });
}

// Genus-level comparison: species-level disagreement between two good
// sources is common and not worth interrupting the user for.
function findConflict(diagnosis: Diagnosis, plantnet: PlantNetCandidate[]): SpeciesConflict | null {
  const top = plantnet[0];
  if (!top || top.score < 0.25) return null;
  const claudeGenus = diagnosis.plant.scientificName.trim().split(/\s+/)[0]?.toLowerCase();
  if (!claudeGenus || claudeGenus === top.genus.toLowerCase()) return null;
  const alt = diagnosis.plant.alternatives.find((a) =>
    a.scientificName.toLowerCase().startsWith(top.genus.toLowerCase()),
  );
  return {
    claude: {
      name: diagnosis.plant.hebrewName,
      scientificName: diagnosis.plant.scientificName,
      confidence: diagnosis.plant.confidence,
    },
    plantnet: {
      name: alt?.hebrewName ?? top.commonNames[0] ?? top.scientificName,
      scientificName: top.scientificName,
      confidence: Math.round(top.score * 100),
    },
  };
}

export async function POST(request: Request) {
  if (!process.env.ANTHROPIC_API_KEY) {
    return errorResponse(500, "חסר מפתח ANTHROPIC_API_KEY בהגדרות השרת. ראה README.");
  }

  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "local";
  const limit = checkRateLimit(ip);
  if (!limit.ok) {
    return errorResponse(429, `יותר מדי בדיקות ברצף. נסה שוב בעוד ${Math.ceil(limit.retryAfterSec / 60)} דקות.`, {
      "Retry-After": String(limit.retryAfterSec),
    });
  }

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return errorResponse(400, "הבקשה לא תקינה.");
  }

  let rawMeta: unknown;
  try {
    rawMeta = JSON.parse(String(form.get("meta") ?? "null"));
  } catch {
    rawMeta = null;
  }
  const meta = DiagnoseRequestSchema.safeParse(rawMeta);
  if (!meta.success) return errorResponse(400, "נתוני השאלון לא תקינים.");
  const req = meta.data;

  const files = form.getAll("images").filter((f): f is File => f instanceof File);
  if (files.length === 0 || files.length !== req.slots.length) {
    return errorResponse(400, "צריך לפחות תמונה אחת.");
  }
  const images: Array<{ slot: PhotoSlot; blob: Blob; base64: string; mediaType: AllowedType }> = [];
  for (const [i, file] of files.entries()) {
    if (!ALLOWED_TYPES.includes(file.type as AllowedType)) {
      return errorResponse(415, "אפשר להעלות רק תמונות JPG, PNG או WebP.");
    }
    if (file.size > MAX_IMAGE_BYTES) {
      return errorResponse(413, "אחת התמונות גדולה מדי (מעל 5MB).");
    }
    const buffer = Buffer.from(await file.arrayBuffer());
    images.push({
      slot: req.slots[i],
      blob: file,
      base64: buffer.toString("base64"),
      mediaType: file.type as AllowedType,
    });
  }

  const started = Date.now();
  const encoder = new TextEncoder();
  const abort = new AbortController();
  request.signal.addEventListener("abort", () => abort.abort());

  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const send = (event: DiagnoseEvent) => {
        try {
          controller.enqueue(encoder.encode(JSON.stringify(event) + "\n"));
        } catch {
          // Client went away; nothing to do.
        }
      };
      const missing: SourceName[] = [];

      send({ type: "stage", stage: "plantnet", status: "start" });
      send({ type: "stage", stage: "weather", status: "start" });

      const plantnetTask = callSource("plantnet", (signal) => identifyWithPlantNet(images, signal), {
        timeoutMs: 12_000,
      }).then((r) => {
        send({ type: "stage", stage: "plantnet", status: r.ok ? "done" : "failed" });
        if (!r.ok) missing.push("plantnet");
        return r.ok ? r.data : null;
      });

      const location = req.location;
      const weatherTask = location
        ? callSource("weather", (signal) => getWeather(location, signal), { timeoutMs: 8_000 }).then((r) => {
            send({ type: "stage", stage: "weather", status: r.ok ? "done" : "failed" });
            if (!r.ok) missing.push("weather");
            return r.ok ? r.data : null;
          })
        : Promise.resolve(null).then(() => {
            send({ type: "stage", stage: "weather", status: "failed", note: "no-location" });
            missing.push("weather");
            return null;
          });

      const [plantnet, weather] = await Promise.all([plantnetTask, weatherTask]);

      send({ type: "stage", stage: "claude", status: "start" });
      try {
        const result = await diagnoseWithClaude(
          { images, request: req, plantnet, weather },
          abort.signal,
          () => send({ type: "stage", stage: "claude", status: "start", note: "writing" }),
        );
        send({ type: "stage", stage: "claude", status: "done" });
        const durationMs = Date.now() - started;
        console.info(
          `[diagnose] ${durationMs}ms model=${result.model} cost~$${result.estimatedCostUsd} missing=${missing.join(",") || "none"}`,
        );
        send({
          type: "result",
          data: result.diagnosis,
          meta: {
            model: result.model,
            durationMs,
            estimatedCostUsd: result.estimatedCostUsd,
            missingSources: missing,
            plantnet: plantnet ?? [],
            weather,
            conflict: req.confirmedSpecies || !plantnet ? null : findConflict(result.diagnosis, plantnet),
          },
        });
      } catch (err) {
        console.error("[diagnose] claude failed:", err);
        send({ type: "stage", stage: "claude", status: "failed" });
        let message = `האבחון נכשל (${pickModel(req.deep).model}). נסה שוב בעוד רגע.`;
        if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
          message = "מפתח ה-Anthropic API לא תקין או שאין לו הרשאה. בדוק את ANTHROPIC_API_KEY.";
        } else if (err instanceof Anthropic.RateLimitError) {
          message = "הגעת למגבלת השימוש של Anthropic. נסה שוב בעוד דקה.";
        } else if (err instanceof Anthropic.BadRequestError && /credit|billing/i.test(err.message)) {
          message = "נגמר הקרדיט בחשבון Anthropic. אפשר להטעין ב-console.anthropic.com.";
        }
        send({ type: "error", message });
      } finally {
        controller.close();
      }
    },
    cancel() {
      abort.abort();
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "application/x-ndjson; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Accel-Buffering": "no",
    },
  });
}
