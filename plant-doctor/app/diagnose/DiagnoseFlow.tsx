"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { FollowUp } from "@/components/FollowUp";
import { LocationPicker, loadSavedLocation } from "@/components/LocationPicker";
import { PhotoCapture, revokePhotos, type Photos } from "@/components/PhotoCapture";
import { IDLE_STAGES, ProgressSteps, type Stages } from "@/components/ProgressSteps";
import { Questionnaire } from "@/components/Questionnaire";
import { ResultView } from "@/components/ResultView";
import { SpeciesConflict } from "@/components/SpeciesConflict";
import { Button, Card, CardTitle } from "@/components/ui";
import type { Diagnosis } from "@/lib/schema";
import {
  EMPTY_QUESTIONNAIRE,
  PHOTO_SLOTS,
  type DiagnoseEvent,
  type DiagnoseMeta,
  type DiagnoseRequest,
  type UserLocation,
} from "@/lib/types";

const STEPS = ["photos", "questions", "result"] as const;
type Step = (typeof STEPS)[number];

function stepFromUrl(): Step {
  const s = new URLSearchParams(window.location.search).get("step");
  return STEPS.find((x) => x === s) ?? "photos";
}
type RunOptions = Pick<DiagnoseRequest, "followUp" | "forceFinal" | "confirmedSpecies" | "deep">;

export function DiagnoseFlow() {
  const [step, setStep] = useState<Step>("photos");
  const [photos, setPhotos] = useState<Photos>({});
  const [questionnaire, setQuestionnaire] = useState(EMPTY_QUESTIONNAIRE);
  const [location, setLocation] = useState<UserLocation | null>(null);
  const [locationLoaded, setLocationLoaded] = useState(false);
  const [stages, setStages] = useState<Stages>(IDLE_STAGES);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ data: Diagnosis; meta: DiagnoseMeta } | null>(null);
  const [lastRun, setLastRun] = useState<RunOptions | null>(null);
  const [followUpDismissed, setFollowUpDismissed] = useState(false);
  const [conflictDismissed, setConflictDismissed] = useState(false);
  const topRef = useRef<HTMLDivElement>(null);
  const stepRef = useRef<Step>("photos");
  const runRef = useRef<AbortController | null>(null);

  useEffect(() => {
    // Saved location lives in localStorage, which only exists after hydration.
    // eslint-disable-next-line react-hooks/set-state-in-effect -- one-time read of browser storage
    setLocation(loadSavedLocation());
    setLocationLoaded(true);

    // Each step is a history entry, so the phone's back button steps back
    // through the flow instead of leaving it. A reload loses the photos, so
    // it always starts from the first step.
    if (window.location.search) window.history.replaceState(null, "", window.location.pathname);
    const onPopState = () => {
      const next = stepFromUrl();
      if (next !== "result") runRef.current?.abort();
      stepRef.current = next;
      setStep(next);
    };
    window.addEventListener("popstate", onPopState);
    return () => {
      window.removeEventListener("popstate", onPopState);
      // Leaving the page cancels a diagnosis in flight, so nobody pays for it.
      runRef.current?.abort();
    };
  }, []);

  const photoCount = Object.keys(photos).length;
  const firstPhotoUrl = PHOTO_SLOTS.map((s) => photos[s]?.url).find(Boolean) ?? null;

  function goTo(next: Step) {
    if (next !== stepRef.current) {
      const url = next === "photos" ? window.location.pathname : `?step=${next}`;
      window.history.pushState(null, "", url);
    }
    stepRef.current = next;
    setStep(next);
    topRef.current?.scrollIntoView({ behavior: "smooth" });
  }

  function startOver() {
    runRef.current?.abort();
    revokePhotos(photos);
    setPhotos({});
    setResult(null);
    setError(null);
    setQuestionnaire(EMPTY_QUESTIONNAIRE);
    goTo("photos");
  }

  async function run(options: RunOptions) {
    // Ignore a second tap while a diagnosis is already running.
    if (runRef.current && !runRef.current.signal.aborted) return;
    const controller = new AbortController();
    runRef.current = controller;

    const slots = PHOTO_SLOTS.filter((s) => photos[s]);
    const body: DiagnoseRequest = { slots, questionnaire, location, ...options };
    const form = new FormData();
    form.append("meta", JSON.stringify(body));
    for (const slot of slots) form.append("images", photos[slot]!.blob, `${slot}.jpg`);

    setRunning(true);
    setError(null);
    setStages(IDLE_STAGES);
    setLastRun(options);
    goTo("result");

    try {
      const res = await fetch("/api/diagnose", { method: "POST", body: form, signal: controller.signal });
      if (res.status === 413) {
        throw new Error("התמונות גדולות מדי לשליחה. נסה עם פחות תמונות.");
      }
      if (!res.ok || !res.body) {
        const payload = (await res.json().catch(() => null)) as { error?: string } | null;
        throw new Error(payload?.error ?? "השרת לא זמין כרגע. נסה שוב בעוד רגע.");
      }

      const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = "";
      let gotResult = false;
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";
        for (const line of lines) {
          if (!line.trim()) continue;
          const event = JSON.parse(line) as DiagnoseEvent;
          if (event.type === "stage") {
            setStages((s) => ({ ...s, [event.stage]: { status: event.status, note: event.note } }));
          } else if (event.type === "result") {
            gotResult = true;
            setResult({ data: event.data, meta: event.meta });
            setFollowUpDismissed(false);
            setConflictDismissed(false);
          } else {
            throw new Error(event.message);
          }
        }
      }
      if (!gotResult) throw new Error("החיבור נקטע לפני שהאבחון הסתיים. נסה שוב.");
    } catch (e) {
      if (controller.signal.aborted) return; // cancelled on purpose
      setError(
        e instanceof TypeError
          ? "אין חיבור לאינטרנט, או שהשרת לא זמין. בדוק את החיבור ונסה שוב."
          : e instanceof SyntaxError || !(e instanceof Error)
            ? "התקבלה תשובה לא תקינה מהשרת. נסה שוב."
            : e.message,
      );
    } finally {
      if (runRef.current === controller) runRef.current = null;
      setRunning(false);
    }
  }

  const d = result?.data;
  const showFollowUp =
    d && d.needsMoreInfo && d.followUpQuestions.length > 0 && !lastRun?.forceFinal && !followUpDismissed;
  const conflict = result?.meta.conflict;

  return (
    <div ref={topRef} className="mx-auto w-full max-w-2xl px-4 pb-28 pt-4">
      <header className="mb-4 flex items-center justify-between">
        <Link href="/" className="flex min-h-11 items-center text-accent">
          → דוקטור צמחים
        </Link>
        <span className="text-sm text-muted">
          {step === "photos" ? "שלב 1 מתוך 3" : step === "questions" ? "שלב 2 מתוך 3" : "אבחון"}
        </span>
      </header>

      {step === "photos" && (
        <>
          <h1 className="mb-1 text-2xl font-bold">צלם את הצמח</h1>
          <p className="mb-4 text-muted">באור יום, בלי פלאש. אפשר לצלם או לבחור מהגלריה.</p>
          <PhotoCapture photos={photos} onChange={setPhotos} />
          <StickyBar>
            <Button onClick={() => goTo("questions")} disabled={photoCount === 0} className="w-full">
              המשך ({photoCount} תמונות)
            </Button>
          </StickyBar>
        </>
      )}

      {step === "questions" && (
        <>
          <h1 className="mb-1 text-2xl font-bold">ספר לי קצת על הצמח</h1>
          <p className="mb-4 text-muted">הכל אופציונלי. כל תשובה עוזרת לדייק.</p>
          {locationLoaded && (
            <LocationPicker
              key={location?.kind === "city" ? location.name : (location?.kind ?? "none")}
              value={location}
              onChange={setLocation}
            />
          )}
          <hr className="my-4 border-border" />
          <Questionnaire value={questionnaire} onChange={setQuestionnaire} />
          <StickyBar>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => goTo("photos")}>
                חזרה
              </Button>
              <Button
                className="flex-1"
                onClick={() => run({ followUp: null, forceFinal: false, confirmedSpecies: null, deep: false })}
              >
                🔍 אבחן את הצמח
              </Button>
            </div>
          </StickyBar>
        </>
      )}

      {step === "result" && (
        <div className="space-y-4">
          {running && (
            <Card>
              <CardTitle icon="🌿">עובדים על זה…</CardTitle>
              <ProgressSteps stages={stages} />
            </Card>
          )}

          {error && !running && (
            <Card className="border-danger">
              <p role="alert" className="mb-3 text-danger">
                {error}
              </p>
              <div className="flex flex-wrap gap-2">
                {lastRun && <Button onClick={() => run(lastRun)}>נסה שוב</Button>}
                <Button variant="secondary" onClick={() => goTo("questions")}>
                  חזרה לשאלון
                </Button>
              </div>
            </Card>
          )}

          {d && result && (
            <div className={running ? "pointer-events-none opacity-50" : ""} aria-busy={running}>
              {conflict && !conflictDismissed && (
                <div className="mb-4">
                  <SpeciesConflict
                    conflict={conflict}
                    disabled={running}
                    onKeep={() => setConflictDismissed(true)}
                    onPickPlantNet={() =>
                      run({
                        followUp: lastRun?.followUp ?? null,
                        forceFinal: true,
                        confirmedSpecies: conflict.plantnet.scientificName,
                        deep: lastRun?.deep ?? false,
                      })
                    }
                  />
                </div>
              )}
              {showFollowUp && (
                <div className="mb-4">
                  <FollowUp
                    key={d.followUpQuestions.join("|")}
                    questions={d.followUpQuestions}
                    requestedPhoto={d.requestedPhoto}
                    extraPhoto={photos.extra ?? null}
                    onExtraPhoto={(blob) =>
                      setPhotos((current) => {
                        if (current.extra) URL.revokeObjectURL(current.extra.url);
                        return { ...current, extra: { blob, url: URL.createObjectURL(blob) } };
                      })
                    }
                    disabled={running}
                    onSkip={() => setFollowUpDismissed(true)}
                    onSubmit={(answers) => {
                      const followUp = d.requestedPhoto
                        ? [
                            ...answers,
                            {
                              question: `ביקשת תמונה: ${d.requestedPhoto}`,
                              answer: photos.extra ? "צירפתי אותה" : "לא צירפתי",
                            },
                          ]
                        : answers;
                      void run({ followUp, forceFinal: true, confirmedSpecies: null, deep: lastRun?.deep ?? false });
                    }}
                  />
                </div>
              )}
              <ResultView d={d} meta={result.meta} photoUrl={firstPhotoUrl} />
              <Card className="mt-4">
                <CardTitle icon="🔬">רוצה חוות דעת מעמיקה יותר?</CardTitle>
                <p className="mb-3 text-sm text-muted">
                  בדיקה עם המודל החזק ביותר. לוקחת יותר זמן ועולה יותר, ושווה במקרים לא ברורים.
                </p>
                <Button
                  variant="secondary"
                  disabled={running || lastRun?.deep}
                  onClick={() =>
                    run({
                      followUp: lastRun?.followUp ?? null,
                      forceFinal: true,
                      confirmedSpecies: lastRun?.confirmedSpecies ?? null,
                      deep: true,
                    })
                  }
                >
                  {lastRun?.deep ? "זו כבר בדיקה מעמיקה" : "בדיקה מעמיקה"}
                </Button>
              </Card>
              <div className="mt-4 flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  onClick={startOver}
                >
                  אבחון של צמח אחר
                </Button>
                <Button variant="ghost" onClick={() => goTo("photos")}>
                  הוסף תמונות לאותו צמח
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function StickyBar({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-x-0 bottom-0 border-t border-border bg-bg/95 px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3 backdrop-blur">
      <div className="mx-auto max-w-2xl">{children}</div>
    </div>
  );
}
