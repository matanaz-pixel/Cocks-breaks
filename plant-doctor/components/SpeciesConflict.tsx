import type { SpeciesConflict as Conflict } from "@/lib/types";
import { Button, Card, CardTitle } from "./ui";

export function SpeciesConflict({
  conflict,
  onPickPlantNet,
  onKeep,
  disabled,
}: {
  conflict: Conflict;
  onPickPlantNet: () => void;
  onKeep: () => void;
  disabled: boolean;
}) {
  return (
    <Card className="border-warn">
      <CardTitle icon="🌿">איזה צמח זה?</CardTitle>
      <p className="mb-4 text-sm text-muted">
        שני המקורות לא מסכימים על זיהוי הצמח. בחר את מה שנראה לך נכון, והאבחון יתעדכן בהתאם.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl bg-surface-2 p-3">
          <p className="text-xs text-muted">ניתוח התמונות</p>
          <p className="font-bold">{conflict.claude.name}</p>
          <p className="text-sm text-muted">
            <i lang="la">{conflict.claude.scientificName}</i>
          </p>
          <p className="mb-3 text-sm">ביטחון {conflict.claude.confidence}%</p>
          <Button variant="secondary" onClick={onKeep} disabled={disabled} className="w-full">
            זה הצמח
          </Button>
        </div>
        <div className="rounded-xl bg-surface-2 p-3">
          <p className="text-xs text-muted">מאגר Pl@ntNet</p>
          <p className="font-bold">{conflict.plantnet.name}</p>
          <p className="text-sm text-muted">
            <i lang="la">{conflict.plantnet.scientificName}</i>
          </p>
          <p className="mb-3 text-sm">ביטחון {conflict.plantnet.confidence}%</p>
          <Button variant="secondary" onClick={onPickPlantNet} disabled={disabled} className="w-full">
            זה הצמח, אבחן מחדש
          </Button>
        </div>
      </div>
    </Card>
  );
}
