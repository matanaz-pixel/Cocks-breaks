// USD per million tokens. Update when Anthropic pricing changes.
const PRICES: Record<string, { input: number; output: number; cacheRead: number }> = {
  "claude-sonnet-5-5": { input: 2, output: 10, cacheRead: 0.2 },
  "claude-opus-5-5": { input: 4, output: 20, cacheRead: 0.2 },
  "claude-haiku-5-5": { input: 0.1, output: 0.5, cacheRead: 0.01 },
};

export interface TokenUsage {
  input_tokens: number;
  output_tokens: number;
  cache_read_input_tokens?: number | null;
  cache_creation_input_tokens?: number | null;
}

export function estimateCostUsd(model: string, usage: TokenUsage): number {
  const price = PRICES[model];
  if (!price) return 0;
  const cacheRead = usage.cache_read_input_tokens ?? 0;
  // Cache writes cost 1.25x input; close enough to bill them as input here.
  const input = usage.input_tokens + (usage.cache_creation_input_tokens ?? 0) * 1.25;
  const cost =
    (input * price.input + cacheRead * price.cacheRead + usage.output_tokens * price.output) /
    1_000_000;
  return Math.round(cost * 10_000) / 10_000;
}
