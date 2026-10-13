"use client";

import { useState } from "react";
import { Button, Card, CardTitle } from "./ui";

export function FollowUp({
  questions,
  requestedPhoto,
  onSubmit,
  onSkip,
  disabled,
}: {
  questions: string[];
  requestedPhoto: string | null;
  onSubmit: (answers: Array<{ question: string; answer: string }>) => void;
  onSkip: () => void;
  disabled: boolean;
}) {
  const [answers, setAnswers] = useState<string[]>(() => questions.map(() => ""));

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
            {["כן", "לא", "לא בטוח"].map((quick) => (
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
            value={["כן", "לא", "לא בטוח"].includes(answers[i]) ? "" : answers[i]}
            onChange={(e) => {
              const value = e.target.value;
              setAnswers((a) => a.map((v, j) => (j === i ? value : v)));
            }}
          />
        </div>
      ))}
      {requestedPhoto && (
        <p className="mb-4 rounded-xl bg-surface-2 p-3 text-sm">
          📷 תמונה שתעזור: {requestedPhoto}. אפשר לחזור לשלב התמונות ולהוסיף אותה.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button disabled={disabled} onClick={() => onSubmit(questions.map((q, i) => ({ question: q, answer: answers[i] })))}>
          שלח תשובות ואבחן שוב
        </Button>
        <Button variant="secondary" onClick={onSkip} disabled={disabled}>
          מספיק לי האבחון הראשוני
        </Button>
      </div>
    </Card>
  );
}
