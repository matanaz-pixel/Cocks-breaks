"use client";

import { useState } from "react";
import type { UserLocation } from "@/lib/types";
import { Button } from "./ui";

const STORAGE_KEY = "plant-doctor:location";

export function loadSavedLocation(): UserLocation | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as UserLocation) : null;
  } catch {
    return null;
  }
}

function save(location: UserLocation | null) {
  try {
    if (location) localStorage.setItem(STORAGE_KEY, JSON.stringify(location));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Private mode or blocked storage: the location just won't be remembered.
  }
}

// Holds its own draft of the city name; remount it (via key) when the saved location loads.
export function LocationPicker({
  value,
  onChange,
}: {
  value: UserLocation | null;
  onChange: (l: UserLocation | null) => void;
}) {
  const [city, setCity] = useState(value?.kind === "city" ? value.name : "");
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update(location: UserLocation | null) {
    save(location);
    onChange(location);
  }

  function locateMe() {
    if (!("geolocation" in navigator)) {
      setError("הדפדפן לא תומך באיתור מיקום. כתוב את שם העיר.");
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        // Two decimals (~1 km) is plenty for weather and keeps the exact address private.
        update({
          kind: "coords",
          lat: Math.round(pos.coords.latitude * 100) / 100,
          lon: Math.round(pos.coords.longitude * 100) / 100,
        });
      },
      () => {
        setLocating(false);
        setError("לא קיבלנו הרשאה למיקום. אפשר לכתוב את שם העיר במקום.");
      },
      { timeout: 10_000, maximumAge: 60 * 60 * 1000 },
    );
  }

  return (
    <div className="mb-2">
      <p className="mb-2 text-sm font-semibold text-muted">מיקום (בשביל מזג האוויר)</p>
      {value?.kind === "coords" ? (
        <div className="flex items-center justify-between gap-2 rounded-xl bg-ok-bg p-3 text-sm text-ok">
          <span>📍 המיקום הנוכחי שלך נשמר</span>
          <Button variant="ghost" onClick={() => update(null)} className="!min-h-0 !px-2 !py-1">
            שנה
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-2 sm:flex-row">
          <Button variant="secondary" onClick={locateMe} disabled={locating}>
            {locating ? "מאתר…" : "📍 השתמש במיקום שלי"}
          </Button>
          <input
            aria-label="שם העיר"
            className="min-h-11 flex-1 rounded-xl border border-border bg-surface px-3 text-base placeholder:text-muted/70 focus:border-accent focus:outline-none"
            placeholder="או כתוב עיר, למשל: חיפה"
            value={city}
            onChange={(e) => setCity(e.target.value)}
            onBlur={() => update(city.trim() ? { kind: "city", name: city.trim() } : null)}
          />
        </div>
      )}
      {error && <p className="mt-2 text-sm text-warn">{error}</p>}
    </div>
  );
}
