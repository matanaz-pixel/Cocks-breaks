import type { ConditionKey, Diagnosis } from "./schema";
import type { PhotoSlot, Questionnaire } from "./types";

export const SLOT_INFO: Record<PhotoSlot, { title: string; hint: string }> = {
  whole: { title: "הצמח כולו", hint: "מרחק של מטר בערך, שיראו את כל הצמח והעציץ" },
  closeup: { title: "תקריב של העלה הבעייתי", hint: "קרוב ובפוקוס, באור יום" },
  underside: { title: "הצד התחתון של העלה", hint: "שם מסתתרים רוב המזיקים" },
  soil: { title: "המצע והגבעול", hint: "בסיס הגבעול ופני האדמה" },
};

type Options<K extends keyof Questionnaire> = Array<{
  value: NonNullable<Questionnaire[K]> extends Array<infer U> ? U : NonNullable<Questionnaire[K]>;
  label: string;
}>;

export const PLACEMENT: Options<"placement"> = [
  { value: "indoor", label: "בבית" },
  { value: "balcony", label: "מרפסת" },
  { value: "outdoor", label: "בחוץ" },
];
export const EXPOSURE: Options<"exposure"> = [
  { value: "south", label: "דרום" },
  { value: "west", label: "מערב" },
  { value: "east", label: "מזרח" },
  { value: "north", label: "צפון" },
];
export const CONTAINER: Options<"container"> = [
  { value: "pot", label: "עציץ" },
  { value: "ground", label: "באדמה" },
];
export const POT_SIZE: Options<"potSize"> = [
  { value: "small", label: "קטן (עד 15 ס״מ)" },
  { value: "medium", label: "בינוני" },
  { value: "large", label: "גדול (30+ ס״מ)" },
];
export const YES_NO: Options<"drainageHole"> = [
  { value: "yes", label: "כן" },
  { value: "no", label: "לא" },
  { value: "unknown", label: "לא יודע" },
];
export const RECENT_CHANGES: Options<"recentChanges"> = [
  { value: "moved", label: "הזזתי מקום" },
  { value: "repotted", label: "העברתי עציץ" },
  { value: "pruned", label: "גזמתי" },
  { value: "heatwave", label: "היה שרב" },
  { value: "cold", label: "היה קר" },
];
export const WATER: Options<"water"> = [
  { value: "tap", label: "מי ברז" },
  { value: "filtered", label: "מסוננים" },
  { value: "rain", label: "מי גשם" },
];
export const PETS_KIDS: Options<"petsOrKids"> = [
  { value: "pets", label: "חיות מחמד" },
  { value: "kids", label: "ילדים" },
  { value: "both", label: "גם וגם" },
  { value: "none", label: "אף אחד" },
];

export const CONDITION_LABEL: Record<ConditionKey, string> = {
  light: "אור",
  watering: "השקיה",
  temperature: "טמפרטורה",
  humidity: "לחות",
  soil: "מצע",
  ph: "pH",
  fertilizer: "דישון",
  potAndDrainage: "עציץ וניקוז",
  pruning: "גיזום",
};

export const CATEGORY_LABEL: Record<Diagnosis["diagnoses"][number]["category"], string> = {
  nutrient: "תזונה",
  water: "השקיה",
  light: "אור",
  pest: "מזיקים",
  disease: "מחלה",
  temperature: "טמפרטורה",
  transplant: "הלם שתילה",
  roots: "שורשים",
  other: "אחר",
};

export const TOXICITY_LABEL: Record<Diagnosis["toxicity"]["pets"], string> = {
  safe: "בטוח",
  mild: "רעילות קלה",
  toxic: "רעיל",
  unknown: "לא ידוע",
};

export const TREATMENT_KIND_LABEL: Record<"organic" | "chemical" | "care", string> = {
  organic: "אורגני",
  chemical: "כימי",
  care: "טיפול",
};
