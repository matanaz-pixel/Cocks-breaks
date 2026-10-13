"use client";

import { useState, type Dispatch, type SetStateAction } from "react";
import { compressImage } from "@/lib/clientImage";
import { SLOT_INFO } from "@/lib/labels";
import { MAIN_SLOTS, type PhotoSlot } from "@/lib/types";

export type Photos = Partial<Record<PhotoSlot, { blob: Blob; url: string }>>;

export function revokePhotos(photos: Photos) {
  for (const p of Object.values(photos)) if (p) URL.revokeObjectURL(p.url);
}

// Takes the state setter itself: photos are compressed asynchronously, and
// updating from a stale copy would drop a photo picked meanwhile.
export function PhotoCapture({ photos, onChange }: { photos: Photos; onChange: Dispatch<SetStateAction<Photos>> }) {
  const [busy, setBusy] = useState<PhotoSlot[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(slot: PhotoSlot, file: File | undefined) {
    if (!file) return;
    setError(null);
    setBusy((b) => [...b, slot]);
    try {
      const blob = await compressImage(file);
      const url = URL.createObjectURL(blob);
      onChange((current) => {
        const prev = current[slot];
        if (prev) URL.revokeObjectURL(prev.url);
        return { ...current, [slot]: { blob, url } };
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "עיבוד התמונה נכשל.");
    } finally {
      setBusy((b) => b.filter((s) => s !== slot));
    }
  }

  function remove(slot: PhotoSlot) {
    onChange((current) => {
      const prev = current[slot];
      if (prev) URL.revokeObjectURL(prev.url);
      const next = { ...current };
      delete next[slot];
      return next;
    });
  }

  return (
    <div>
      <div className="grid grid-cols-2 gap-3">
        {MAIN_SLOTS.map((slot) => {
          const info = SLOT_INFO[slot];
          const photo = photos[slot];
          return (
            <div key={slot} className="relative">
              <label
                className={`flex aspect-square cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 text-center transition-colors focus-within:outline-3 focus-within:outline-offset-2 focus-within:outline-accent ${
                  photo ? "border-accent" : "border-dashed border-border bg-surface hover:bg-surface-2"
                }`}
              >
                <input
                  type="file"
                  accept="image/*"
                  className="sr-only"
                  onChange={(e) => {
                    void handleFile(slot, e.target.files?.[0]);
                    e.target.value = "";
                  }}
                />
                {photo ? (
                  // eslint-disable-next-line @next/next/no-img-element -- local blob preview
                  <img src={photo.url} alt={info.title} className="h-full w-full object-cover" />
                ) : (
                  <div className="p-3">
                    <div className="text-3xl" aria-hidden="true">
                      {busy.includes(slot) ? "⏳" : "📷"}
                    </div>
                    <div className="mt-1 text-sm font-semibold">
                      {info.title}
                    </div>
                    <div className="mt-1 text-xs text-muted">{info.hint}</div>
                  </div>
                )}
              </label>
              {photo && (
                <button
                  type="button"
                  onClick={() => remove(slot)}
                  aria-label={`הסר את התמונה: ${info.title}`}
                  className="absolute top-2 left-2 flex h-11 w-11 items-center justify-center rounded-full bg-black/60 text-lg text-white"
                >
                  ✕
                </button>
              )}
              {photo && (
                <div className="absolute inset-x-0 bottom-0 rounded-b-2xl bg-black/55 px-2 py-1 text-center text-xs text-white">
                  {info.title}
                </div>
              )}
            </div>
          );
        })}
      </div>
      {error && (
        <p role="alert" className="mt-3 rounded-xl bg-danger-bg p-3 text-sm text-danger">
          {error}
        </p>
      )}
      <p className="mt-3 text-sm text-muted">
        מספיקה תמונה אחת. כל תמונה נוספת משפרת את הדיוק, במיוחד התקריב והצד התחתון של העלה.
      </p>
    </div>
  );
}
