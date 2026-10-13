// Sliding-window limiter kept in memory. On serverless each instance has its
// own window, so this is a guard against runaway loops and casual abuse, not a
// hard quota. Stage 2 replaces the IP key with the signed-in user.

const WINDOW_MS = 10 * 60 * 1000;
const hits = new Map<string, number[]>();

export function checkRateLimit(key: string): { ok: true } | { ok: false; retryAfterSec: number } {
  const limit = Number(process.env.DIAGNOSE_RATE_LIMIT ?? 15);
  const now = Date.now();
  const recent = (hits.get(key) ?? []).filter((t) => now - t < WINDOW_MS);
  if (recent.length >= limit) {
    hits.set(key, recent);
    return { ok: false, retryAfterSec: Math.ceil((recent[0] + WINDOW_MS - now) / 1000) };
  }
  recent.push(now);
  hits.set(key, recent);
  return { ok: true };
}
