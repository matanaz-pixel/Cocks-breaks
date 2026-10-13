import type { SourceName } from "@/lib/types";

export type StageState = { status: "idle" | "start" | "done" | "failed"; note?: string };
export type Stages = Record<SourceName, StageState>;

export const IDLE_STAGES: Stages = {
  plantnet: { status: "idle" },
  weather: { status: "idle" },
  claude: { status: "idle" },
};

function label(stage: SourceName, s: StageState): string {
  switch (stage) {
    case "plantnet":
      return s.status === "failed" ? "מאגר הזיהוי לא זמין, ממשיכים בלעדיו" : s.status === "done" ? "הצמח זוהה במאגר" : "מזהה את הצמח…";
    case "weather":
      if (s.status === "failed") {
        if (s.note === "no-location") return "בלי מיקום, אין התאמה למזג האוויר";
        if (s.note === "city-not-found") return "לא מצאנו את העיר שכתבת, ממשיכים בלי מזג אוויר";
        return "מזג האוויר לא זמין כרגע";
      }
      return s.status === "done" ? "מזג האוויר והתחזית נבדקו" : "בודק מזג אוויר…";
    case "claude":
      if (s.status === "failed") return "האבחון נכשל";
      if (s.status === "done") return "האבחון מוכן";
      return s.note === "writing" ? "כותב את תוכנית הטיפול…" : "מאבחן את התמונות…";
  }
}

const ICON: Record<StageState["status"], string> = { idle: "○", start: "⏳", done: "✓", failed: "⚠" };

export function ProgressSteps({ stages }: { stages: Stages }) {
  return (
    <ol className="space-y-3" aria-live="polite">
      {(Object.keys(stages) as SourceName[]).map((stage) => {
        const s = stages[stage];
        if (s.status === "idle") return null;
        const color = s.status === "done" ? "text-ok" : s.status === "failed" ? "text-warn" : "text-text";
        return (
          <li key={stage} className={`flex items-center gap-3 ${color}`}>
            <span
              aria-hidden="true"
              className={`inline-flex h-7 w-7 items-center justify-center rounded-full bg-surface-2 ${s.status === "start" ? "animate-pulse" : ""}`}
            >
              {ICON[s.status]}
            </span>
            <span>{label(stage, s)}</span>
          </li>
        );
      })}
    </ol>
  );
}
