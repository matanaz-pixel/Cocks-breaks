import { z } from "zod";
import { fetchJson, NonRetryableError } from "../adapter";
import { findIsraeliCity } from "../israelCities";
import type { UserLocation, WeatherSummary } from "../types";

// Open-Meteo: free, no key. https://open-meteo.com/en/docs
export class CityNotFoundError extends NonRetryableError {
  constructor(city: string) {
    super(`city not found: ${city}`);
  }
}

const FORECAST = "https://api.open-meteo.com/v1/forecast";
const GEOCODE = "https://geocoding-api.open-meteo.com/v1/search";

const GeocodeSchema = z.object({
  results: z
    .array(z.object({ name: z.string(), latitude: z.number(), longitude: z.number() }))
    .optional(),
});

const ForecastSchema = z.object({
  current: z.object({
    temperature_2m: z.number(),
    relative_humidity_2m: z.number(),
    precipitation: z.number(),
    uv_index: z.number().nullable().optional(),
    wind_speed_10m: z.number(),
  }),
  daily: z.object({
    time: z.array(z.string()),
    temperature_2m_max: z.array(z.number()),
    temperature_2m_min: z.array(z.number()),
    precipitation_sum: z.array(z.number()),
    precipitation_probability_max: z.array(z.number().nullable()),
    uv_index_max: z.array(z.number().nullable()),
    et0_fao_evapotranspiration: z.array(z.number().nullable()),
  }),
});

async function resolveLocation(
  location: UserLocation,
  signal: AbortSignal,
): Promise<{ lat: number; lon: number; placeName: string | null }> {
  if (location.kind === "coords") {
    return { lat: location.lat, lon: location.lon, placeName: null };
  }
  const known = findIsraeliCity(location.name);
  if (known) return { lat: known.lat, lon: known.lon, placeName: known.name };

  const url = `${GEOCODE}?name=${encodeURIComponent(location.name)}&count=1&language=he&format=json`;
  const geo = GeocodeSchema.parse(await fetchJson(url, { signal }));
  const hit = geo.results?.[0];
  if (!hit) throw new CityNotFoundError(location.name);
  return { lat: hit.latitude, lon: hit.longitude, placeName: hit.name };
}

export async function getWeather(
  location: UserLocation,
  signal: AbortSignal,
): Promise<WeatherSummary> {
  const { lat, lon, placeName } = await resolveLocation(location, signal);

  const params = new URLSearchParams({
    latitude: lat.toFixed(3),
    longitude: lon.toFixed(3),
    current: "temperature_2m,relative_humidity_2m,precipitation,uv_index,wind_speed_10m",
    daily: [
      "temperature_2m_max",
      "temperature_2m_min",
      "precipitation_sum",
      "precipitation_probability_max",
      "uv_index_max",
      "et0_fao_evapotranspiration",
    ].join(","),
    timezone: "auto",
    forecast_days: "7",
  });
  const data = ForecastSchema.parse(await fetchJson(`${FORECAST}?${params}`, { signal }));
  const d = data.daily;

  return {
    placeName,
    current: {
      temperature: data.current.temperature_2m,
      humidity: data.current.relative_humidity_2m,
      precipitation: data.current.precipitation,
      uvIndex: data.current.uv_index ?? null,
      windSpeed: data.current.wind_speed_10m,
    },
    daily: d.time.map((date, i) => ({
      date,
      tMax: d.temperature_2m_max[i],
      tMin: d.temperature_2m_min[i],
      precipitation: d.precipitation_sum[i],
      precipitationProbability: d.precipitation_probability_max[i],
      uvIndexMax: d.uv_index_max[i],
      et0: d.et0_fao_evapotranspiration[i],
    })),
  };
}
