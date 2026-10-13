"use client";

import { useState } from "react";
import { compressImage } from "@/lib/clientImage";
import { TEXT_LIMITS } from "@/lib/types";
import { Button, Card, CardTitle } from "./ui";

const QUICK = ["כן", "לא", "לא בטוח"];

export function FollowUp({
  questions,
  requestedPhoto,
  extraPhoto,
  onExtraPhoto,
  onSubmit,
  onSkip,
  disabled,
}: {
  questions: string[];
  requestedPhoto: string | null;
  extraPhoto: { url: string } | null;
  onExtraPhoto: (blob: Blob) => void;
  onSubmit: (answers: Array<{ question: string; answer: string }>) => void;
  onSkip: () => void;
  disabled: boolean;
}) {
  const [answers, setAnswers] = useState<string[]>(() => questions.map(() => ""));
  const [photoBusy, setPhotoBusy] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);

  async function handlePhoto(file: File | undefined) {
    if (!file) return;
    setPhotoBusy(true);
    setPhotoError(null);
    try {
      onExtraPhoto(await compressImage(file));
    } catch (e) {
      setPhotoError(e instanceof Error ? e.message : "עיבוד התמונה נכשל.");
    } finally {
      setPhotoBusy(false);
    }
  }

  return (
    <Card className="border-accent">
      <CardTitle icon="🤔">כמה שאלות כדי לדייק</CardTitle>
      <p className="mb-4 text-sm text-muted">
        האבחון למטה הוא ראשוני. התשובות שלך יעזרו להבחין בין האפשרויות.
      </p>
      {questions.map((q, i) => (
        <div key={i} className="mb-4">
          <p className="mb-2 font-semibold">{q}</p>
          <div className="mb-2 flex gap-2">
            {QUICK.map((quick) => (
              <button
                key={quick}
                type="button"
                aria-pressed={answers[i] === quick}
                onClick={() => setAnswers((a) => a.map((v, j) => (j === i ? quick : v)))}
                className={`min-h-11 rounded-full border px-4 text-sm ${
                  answers[i] === quick ? "border-accent bg-accent text-accent-contrast" : "border-border bg-surface"
                }`}
              >
                {quick}
              </button>
            ))}
          </div>
          <input
            aria-label={`תשובה חופשית: ${q}`}
            className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-base focus:border-accent focus:outline-none"
            placeholder="או כתוב תשובה"
            maxLength={TEXT_LIMITS.answer}
            value={QUICK.includes(answers[i]) ? "" : answers[i]}
            onChange={(e) => {
              const value = e.target.value;
              setAnswers((a) => a.map((v, j) => (j === i ? value : v)));
            }}
          />
        </div>
      ))}
      {requestedPhoto && (
        <div className="mb-4 rounded-xl bg-surface-2 p-3">
          <p className="mb-2 text-sm">📷 תמונה שתעזור: {requestedPhoto}</p>
          <div className="flex items-center gap-3">
            {extraPhoto && (
              // eslint-disable-next-line @next/next/no-img-element -- local blob preview
              <img src={extraPhoto.url} alt="התמונה הנוספת שצירפת" className="h-14 w-14 rounded-lg object-cover" />
            )}
            <label className="inline-flex min-h-11 cursor-pointer items-center rounded-xl border border-border bg-surface px-4 text-sm font-semibold focus-within:outline-3 focus-within:outline-accent">
              <input
                type="file"
                accept="image/*"
                className="sr-only"
                disabled={disabled || photoBusy}
                onChange={(e) => {
                  void handlePhoto(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
              {photoBusy ? "מעבד…" : extraPhoto ? "החלף תמונה" : "צרף תמונה"}
            </label>
          </div>
          {photoError && <p role="alert" className="mt-2 text-sm text-danger">{photoError}</p>}
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Button disabled={disabled || photoBusy} onClick={() => onSubmit(questions.map((q, i) => ({ question: q, answer: answers[i] })))}>
          שלח תשובות ואבחן שוב
        </Button>
        <Button variant="secondary" onClick={onSkip} disabled={disabled}>
          מספיק לי האבחון הראשוני
        </Button>
      </div>
    </Card>
  );
}
