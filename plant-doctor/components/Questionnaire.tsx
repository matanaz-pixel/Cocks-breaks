"use client";

import {
  CONTAINER,
  EXPOSURE,
  PETS_KIDS,
  PLACEMENT,
  POT_SIZE,
  RECENT_CHANGES,
  WATER,
  YES_NO,
} from "@/lib/labels";
import type { Questionnaire as Q } from "@/lib/types";
import { Chips, MultiChips, TextField } from "./ui";

export function Questionnaire({ value, onChange }: { value: Q; onChange: (q: Q) => void }) {
  const set = <K extends keyof Q>(key: K) => (v: Q[K]) => onChange({ ...value, [key]: v });

  return (
    <div>
      <TextField
        label="מה קורה לצמח? (במילים שלך)"
        value={value.description}
        onChange={set("description")}
        placeholder="למשל: העלים התחתונים מצהיבים ונושרים כבר שבועיים"
        multiline
      />
      <Chips label="איפה הצמח?" options={PLACEMENT} value={value.placement} onChange={set("placement")} />
      <Chips label="לאיזה כיוון החלון או החשיפה?" options={EXPOSURE} value={value.exposure} onChange={set("exposure")} />
      <Chips label="עציץ או אדמה?" options={CONTAINER} value={value.container} onChange={set("container")} />
      {value.container !== "ground" && (
        <>
          <Chips label="גודל העציץ" options={POT_SIZE} value={value.potSize} onChange={set("potSize")} />
          <Chips label="יש חור ניקוז?" options={YES_NO} value={value.drainageHole} onChange={set("drainageHole")} />
        </>
      )}
      <TextField
        label="כל כמה זמן אתה משקה?"
        value={value.wateringFrequency}
        onChange={set("wateringFrequency")}
        placeholder="למשל: פעמיים בשבוע, כוס מים"
      />
      <TextField label="מתי השקית לאחרונה?" value={value.lastWatered} onChange={set("lastWatered")} placeholder="למשל: אתמול" />
      <TextField
        label="דישון אחרון ואיזה דשן"
        value={value.lastFertilized}
        onChange={set("lastFertilized")}
        placeholder="למשל: לפני חודש, דשן נוזלי 20-20-20"
      />
      <MultiChips
        label="שינויים אחרונים"
        options={RECENT_CHANGES}
        value={value.recentChanges}
        onChange={set("recentChanges")}
      />
      <TextField
        label="כמה זמן הבעיה קיימת?"
        value={value.problemDuration}
        onChange={set("problemDuration")}
        placeholder="למשל: שבוע"
      />
      <Chips label="הבעיה מתפשטת?" options={YES_NO} value={value.spreading} onChange={set("spreading")} />
      <Chips label="באילו מים משקים?" options={WATER} value={value.water} onChange={set("water")} />
      <Chips label="יש בבית חיות מחמד או ילדים?" options={PETS_KIDS} value={value.petsOrKids} onChange={set("petsOrKids")} />
    </div>
  );
}
