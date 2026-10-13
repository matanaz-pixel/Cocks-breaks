import type { Metadata } from "next";
import { DiagnoseFlow } from "./DiagnoseFlow";

export const metadata: Metadata = { title: "אבחון צמח · דוקטור צמחים" };

export default function DiagnosePage() {
  return (
    <main className="flex-1">
      <DiagnoseFlow />
    </main>
  );
}
