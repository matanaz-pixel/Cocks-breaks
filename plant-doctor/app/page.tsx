import Link from "next/link";

const TIPS = [
  { icon: "☀️", text: "צלם באור יום, בלי פלאש" },
  { icon: "🍃", text: "תקריב של העלה הבעייתי, בפוקוס" },
  { icon: "🔄", text: "גם את הצד התחתון של העלה" },
  { icon: "🪴", text: "תמונה של הצמח כולו והמצע" },
];

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 pb-32 pt-10">
      <div className="text-5xl" aria-hidden="true">
        🌿
      </div>
      <h1 className="mt-3 text-3xl font-extrabold">דוקטור צמחים</h1>
      <p className="mt-2 text-lg leading-relaxed text-muted">
        מצלמים צמח, מספרים מה קורה, ומקבלים זיהוי, אבחון ותוכנית טיפול שמותאמת למזג האוויר אצלך.
      </p>

      <section className="mt-8 rounded-2xl border border-border bg-surface p-4">
        <h2 className="mb-3 font-bold">איך מקבלים אבחון מדויק</h2>
        <ul className="space-y-2">
          {TIPS.map((t) => (
            <li key={t.text} className="flex items-center gap-3">
              <span aria-hidden="true" className="text-xl">
                {t.icon}
              </span>
              {t.text}
            </li>
          ))}
        </ul>
      </section>

      <p className="mt-6 text-sm text-muted">
        בקרוב: הגינה שלי, היסטוריה לכל צמח ותזכורות השקיה.
      </p>

      <div className="fixed inset-x-0 bottom-0 border-t border-border bg-bg/95 px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3 backdrop-blur">
        <Link
          href="/diagnose"
          className="mx-auto flex min-h-14 max-w-2xl items-center justify-center gap-2 rounded-2xl bg-accent text-lg font-bold text-accent-contrast hover:bg-accent-strong"
        >
          <span aria-hidden="true">📷</span> צלם צמח
        </Link>
      </div>
    </main>
  );
}
