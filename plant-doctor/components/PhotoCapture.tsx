"use client";

import { useState } from "react";
import { compressImage } from "@/lib/clientImage";
import { SLOT_INFO } from "@/lib/labels";
import { PHOTO_SLOTS, type PhotoSlot } from "@/lib/types";

export type Photos = Partial<Record<PhotoSlot, { blob: Blob; url: string }>>;

export function PhotoCapture({ photos, onChange }: { photos: Photos; onChange: (p: Photos) => void }) {
  const [busy, setBusy] = useState<PhotoSlot | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(slot: PhotoSlot, file: File | undefined) {
    if (!file) return;
    setError(null);
    setBusy(slot);
    try {
      const blob = await compressImage(file);
      const prev = photos[slot];
      if (prev) URL.revokeObjectURL(prev.url);
      onChange({ ...photos, [slot]: { blob, url: URL.createObjectURL(blob) } });
    } catch (e) {
      setError(e instanceof Error ? e.message : "עיבוד התמונה נכשל.");
    } finally {
      setBusy(null);
    }
  }

  function remove(slot: PhotoSlot) {
    const prev = photos[slot];
    if (prev) URL.revokeObjectURL(prev.url);
    const next = { ...photos };
    delete next[slot];
    onChange(next);
  }

  return (
    <div>
      <div className="grid grid-cols-2 gap-3">
        {PHOTO_SLOTS.map((slot) => {
          const info = SLOT_INFO[slot];
          const photo = photos[slot];
          return (
            <div key={slot} className="relative">
              <label
                className={`flex aspect-square cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 text-center transition-colors ${
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
                      {busy === slot ? "⏳" : "📷"}
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
