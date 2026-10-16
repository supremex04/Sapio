import { useEffect, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Dialog } from "../../shared/ui/Dialog";
import { useToast } from "../../shared/ui/Toast";
import { classroomApi } from "./api";
import { StudentPicker } from "./StudentPicker";

/** Shares a document with students: each gets their own copy in their library, and a notification. */
export function ShareDialog({ documentId, open, onClose, onShared }: { documentId: string; open: boolean; onClose: () => void; onShared: () => void }) {
  const toast = useToast();
  const [done, setDone] = useState<string[]>([]);
  const [selection, setSelection] = useState<{ ids: string[]; count: number }>({ ids: [], count: 0 });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    classroomApi.documentStudents(documentId).then((p) => setDone(p.filter((x) => !x.removedByStudent).map((x) => x.studentId)), () => setDone([]));
  }, [documentId, open]);

  async function share() {
    setBusy(true);
    setError(null);
    try {
      const r = await classroomApi.share(documentId, selection.ids);
      toast.show("success", r.shared ? `Shared with ${r.shared} student${r.shared === 1 ? "" : "s"}` : "Everyone already has it");
      onShared();
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Share with students"
      footer={
        <Button variant="primary" onClick={() => void share()} loading={busy} disabled={selection.count === 0}>
          Share{selection.count ? ` with ${selection.count}` : ""}
        </Button>
      }
    >
      <div className="stack">
        {error && <Alert>{error}</Alert>}
        <p className="small muted">Each student gets their own copy in their library (their notes and progress stay theirs) and a notification. You then see their progress here.</p>
        <StudentPicker done={done} onChange={(ids, count) => setSelection({ ids, count })} />
      </div>
    </Dialog>
  );
}
