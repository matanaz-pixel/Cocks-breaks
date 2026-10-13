// Every external source goes through here: a hard timeout, one retry, and a
// result object instead of a throw, so one failing API never sinks a diagnosis.

export type SourceResult<T> = { ok: true; data: T } | { ok: false; error: string };

export class NonRetryableError extends Error {}

interface Options {
  timeoutMs: number;
  retries?: number;
}

export async function callSource<T>(
  name: string,
  fn: (signal: AbortSignal) => Promise<T>,
  { timeoutMs, retries = 1 }: Options,
): Promise<SourceResult<T>> {
  let lastError = "unknown error";
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const data = await fn(AbortSignal.timeout(timeoutMs));
      return { ok: true, data };
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
      console.warn(`[source:${name}] attempt ${attempt + 1} failed: ${lastError}`);
      if (err instanceof NonRetryableError) break;
    }
  }
  return { ok: false, error: lastError };
}

// fetch that turns HTTP errors into exceptions; 4xx (except 429) are not worth retrying.
export async function fetchJson<T>(url: string, init: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const body = (await res.text().catch(() => "")).slice(0, 300);
    const message = `HTTP ${res.status}: ${body}`;
    if (res.status >= 400 && res.status < 500 && res.status !== 429) {
      throw new NonRetryableError(message);
    }
    throw new Error(message);
  }
  return (await res.json()) as T;
}
