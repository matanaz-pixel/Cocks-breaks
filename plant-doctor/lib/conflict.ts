import type { Diagnosis } from "./schema";
import type { PlantNetCandidate, SpeciesConflict } from "./types";

// Genus-level comparison: species-level disagreement between two good
// sources is common and not worth interrupting the user for.
export function findConflict(diagnosis: Diagnosis, plantnet: PlantNetCandidate[]): SpeciesConflict | null {
  const top = plantnet[0];
  if (!top || top.score < 0.25) return null;
  const claudeGenus = diagnosis.plant.scientificName.trim().split(/\s+/)[0]?.toLowerCase();
  if (!claudeGenus || claudeGenus === top.genus.toLowerCase()) return null;
  const alt = diagnosis.plant.alternatives.find((a) =>
    a.scientificName.toLowerCase().startsWith(top.genus.toLowerCase()),
  );
  return {
    claude: {
      name: diagnosis.plant.hebrewName,
      scientificName: diagnosis.plant.scientificName,
      confidence: diagnosis.plant.confidence,
    },
    plantnet: {
      name: alt?.hebrewName ?? top.commonNames[0] ?? top.scientificName,
      scientificName: top.scientificName,
      confidence: Math.round(top.score * 100),
    },
  };
}
