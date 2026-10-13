import {
  CATEGORY_LABEL,
  CONDITION_LABEL,
  TOXICITY_LABEL,
  TREATMENT_KIND_LABEL,
} from "@/lib/labels";
import type { ConditionKey, Diagnosis, TreatmentStep } from "@/lib/schema";
import type { DiagnoseMeta, SourceName } from "@/lib/types";
import { Card, CardTitle } from "./ui";

function scoreTone(score: number) {
  if (score >= 75) return { text: "text-ok", bg: "bg-ok-bg", label: "במצב טוב" };
  if (score >= 45) return { text: "text-warn", bg: "bg-warn-bg", label: "צריך תשומת לב" };
  return { text: "text-danger", bg: "bg-danger-bg", label: "במצוקה" };
}

function IdentityCard({ d, photoUrl }: { d: Diagnosis; photoUrl: string | null }) {
  const tone = scoreTone(d.healthScore);
  return (
    <Card>
      <div className="flex gap-4">
        {photoUrl && (
          // eslint-disable-next-line @next/next/no-img-element -- local blob preview
          <img src={photoUrl} alt={`תמונה של ${d.plant.hebrewName}`} className="h-20 w-20 shrink-0 rounded-xl object-cover" />
        )}
        <div className="min-w-0 flex-1">
          <h2 className="text-2xl font-bold leading-tight">{d.plant.hebrewName}</h2>
          <p className="text-sm text-muted">
            <i lang="la">{d.plant.scientificName}</i>
          </p>
          <p className="text-sm text-muted">
            משפחה: <span lang="la">{d.plant.family}</span> · ביטחון בזיהוי {d.plant.confidence}%
          </p>
        </div>
      </div>
      <div className={`mt-4 flex items-center gap-3 rounded-xl px-3 py-2 ${tone.bg} ${tone.text}`}>
        <span className="text-3xl font-extrabold leading-none">{d.healthScore}</span>
        <span className="text-sm font-semibold">
          ציון בריאות מתוך 100 · {tone.label}
        </span>
      </div>
      <p className="mt-4 leading-relaxed">{d.summary}</p>
      {d.plant.alternatives.length > 0 && (
        <p className="mt-3 text-sm text-muted">
          אפשרויות זיהוי נוספות:{" "}
          {d.plant.alternatives.map((a) => `${a.hebrewName} (${a.confidence}%)`).join(" · ")}
        </p>
      )}
    </Card>
  );
}

function Diagnoses({ d }: { d: Diagnosis }) {
  if (d.diagnoses.length === 0) return null;
  const sorted = [...d.diagnoses].sort((a, b) => b.likelihood - a.likelihood);
  return (
    <Card>
      <CardTitle icon="🔍">מה מצאנו</CardTitle>
      <ol className="space-y-4">
        {sorted.map((dx, i) => (
          <li key={i} className={i > 0 ? "border-t border-border pt-4" : ""}>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="text-base font-bold">{dx.issue}</h3>
              <span className="text-sm text-muted">
                {CATEGORY_LABEL[dx.category]} · סבירות {dx.likelihood}%
              </span>
            </div>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
              <div className="h-full rounded-full bg-accent" style={{ width: `${dx.likelihood}%` }} />
            </div>
            {dx.visualEvidence.length > 0 && (
              <>
                <p className="mt-3 text-sm font-semibold">מה רואים בתמונה:</p>
                <ul className="mt-1 list-disc space-y-1 ps-5 text-sm">
                  {dx.visualEvidence.map((e, j) => (
                    <li key={j}>{e}</li>
                  ))}
                </ul>
              </>
            )}
            <p className="mt-2 text-sm leading-relaxed">{dx.reasoning}</p>
            {dx.differential.length > 0 && (
              <p className="mt-2 text-sm text-muted">מה עוד זה יכול להיות: {dx.differential.join(" · ")}</p>
            )}
          </li>
        ))}
      </ol>
    </Card>
  );
}

const KIND_STYLE: Record<TreatmentStep["kind"], string> = {
  organic: "bg-ok-bg text-ok",
  chemical: "bg-warn-bg text-warn",
  care: "bg-surface-2 text-muted",
};

function StepList({ title, steps }: { title: string; steps: TreatmentStep[] }) {
  if (steps.length === 0) return null;
  return (
    <div className="mb-4 last:mb-0">
      <h3 className="mb-2 font-bold">{title}</h3>
      <ul className="space-y-2">
        {steps.map((s, i) => (
          <li key={i} className="rounded-xl bg-surface-2 p-3">
            <div className="flex items-start justify-between gap-2">
              <span className="font-semibold">{s.action}</span>
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${KIND_STYLE[s.kind]}`}>
                {TREATMENT_KIND_LABEL[s.kind]}
              </span>
            </div>
            {s.details && <p className="mt-1 text-sm leading-relaxed text-muted">{s.details}</p>}
          </li>
        ))}
      </ul>
    </div>
  );
}

function TreatmentPlan({ d }: { d: Diagnosis }) {
  const { now, thisWeek, thisMonth } = d.treatmentPlan;
  if (now.length + thisWeek.length + thisMonth.length === 0) return null;
  return (
    <Card>
      <CardTitle icon="🩺">תוכנית טיפול</CardTitle>
      <StepList title="עכשיו" steps={now} />
      <StepList title="השבוע" steps={thisWeek} />
      <StepList title="בחודש הקרוב" steps={thisMonth} />
    </Card>
  );
}

const STATUS_ICON = { ok: "✓", warn: "⚠", unknown: "?" } as const;
const STATUS_STYLE = { ok: "text-ok", warn: "text-warn", unknown: "text-muted" } as const;

function Conditions({ d }: { d: Diagnosis }) {
  const keys = Object.keys(CONDITION_LABEL) as ConditionKey[];
  return (
    <Card>
      <CardTitle icon="🌱">תנאים אידיאליים מול המצב אצלך</CardTitle>
      <div className="divide-y divide-border">
        {keys.map((k) => {
          const c = d.idealConditions[k];
          return (
            <div key={k} className="grid grid-cols-[auto_1fr] gap-x-3 py-3">
              <span className={`row-span-3 text-lg font-bold ${STATUS_STYLE[c.status]}`} aria-label={c.status === "ok" ? "תקין" : c.status === "warn" ? "דורש שינוי" : "לא ידוע"}>
                {STATUS_ICON[c.status]}
              </span>
              <span className="font-semibold">{CONDITION_LABEL[k]}</span>
              <span className="text-sm">
                <span className="text-muted">אידיאלי: </span>
                {c.ideal}
              </span>
              <span className="text-sm">
                <span className="text-muted">אצלך: </span>
                {c.current}
              </span>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function Weather({ d, meta }: { d: Diagnosis; meta: DiagnoseMeta }) {
  const w = meta.weather;
  if (!w && d.weatherAdvice.length === 0) return null;
  return (
    <Card>
      <CardTitle icon="🌤️">התאמה למזג האוויר</CardTitle>
      {w && (
        <p className="mb-3 text-sm text-muted">
          עכשיו{w.placeName ? ` ב${w.placeName}` : ""}: {Math.round(w.current.temperature)}°, לחות {w.current.humidity}%
          {w.daily[0] ? ` · היום ${Math.round(w.daily[0].tMin)}°–${Math.round(w.daily[0].tMax)}°` : ""}
        </p>
      )}
      {w && (
        <div className="mb-3 flex gap-2 overflow-x-auto pb-1">
          {w.daily.map((day) => (
            <div key={day.date} className="min-w-14 rounded-xl bg-surface-2 p-2 text-center text-xs">
              <div className="font-semibold">
                {new Date(`${day.date}T12:00:00`).toLocaleDateString("he-IL", { weekday: "short" })}
              </div>
              <div>{Math.round(day.tMax)}°</div>
              <div className="text-muted">{Math.round(day.tMin)}°</div>
              {day.precipitation > 0 && <div className="text-accent">{day.precipitation.toFixed(1)} מ״מ</div>}
            </div>
          ))}
        </div>
      )}
      <ul className="list-disc space-y-1.5 ps-5">
        {d.weatherAdvice.map((a, i) => (
          <li key={i}>{a}</li>
        ))}
      </ul>
    </Card>
  );
}

function Safety({ d }: { d: Diagnosis }) {
  const { pets, children, notes } = d.toxicity;
  const risky = pets === "toxic" || pets === "mild" || children === "toxic" || children === "mild";
  return (
    <Card className={risky ? "border-warn" : ""}>
      <CardTitle icon={risky ? "⚠️" : "🐾"}>בטיחות לחיות ולילדים</CardTitle>
      <p>
        חיות מחמד: <strong>{TOXICITY_LABEL[pets]}</strong> · ילדים: <strong>{TOXICITY_LABEL[children]}</strong>
      </p>
      {notes && <p className="mt-2 text-sm text-muted">{notes}</p>}
    </Card>
  );
}

function WarningSigns({ d }: { d: Diagnosis }) {
  if (d.warningSigns.length === 0) return null;
  return (
    <Card>
      <CardTitle icon="🚨">מתי לחשוש</CardTitle>
      <ul className="list-disc space-y-1.5 ps-5">
        {d.warningSigns.map((s, i) => (
          <li key={i}>{s}</li>
        ))}
      </ul>
    </Card>
  );
}

const SOURCE_LABEL: Record<SourceName, string> = {
  plantnet: "מאגר הזיהוי Pl@ntNet",
  weather: "מזג האוויר",
  claude: "האבחון",
};

export function ResultView({ d, meta, photoUrl }: { d: Diagnosis; meta: DiagnoseMeta; photoUrl: string | null }) {
  return (
    <div className="space-y-4">
      {meta.missingSources.length > 0 && (
        <p className="rounded-xl bg-warn-bg p-3 text-sm text-warn">
          לא היה זמין בבדיקה הזו: {meta.missingSources.map((s) => SOURCE_LABEL[s]).join(", ")}. האבחון מתבסס על שאר המקורות.
        </p>
      )}
      <IdentityCard d={d} photoUrl={photoUrl} />
      <Diagnoses d={d} />
      <TreatmentPlan d={d} />
      <Weather d={d} meta={meta} />
      <Conditions d={d} />
      <Safety d={d} />
      <WarningSigns d={d} />
      <p className="px-1 text-xs text-muted">
        מקורות: {d.sources.join(" · ")} · {(meta.durationMs / 1000).toFixed(1)} שניות · עלות משוערת ${meta.estimatedCostUsd.toFixed(3)}
      </p>
    </div>
  );
}
