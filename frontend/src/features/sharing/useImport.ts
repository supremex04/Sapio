import { useState } from "react";
import { useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { useToast } from "../../shared/ui/Toast";
import { sharingApi } from "./api";
import type { ShareSummary } from "./types";

/** Imports a share into the viewer's library, then opens their copy. */
export function useImport(onImported?: (shareId: string, documentId: string) => void) {
  const toast = useToast();
  const navigate = useNavigate();
  const [importing, setImporting] = useState<string | null>(null);

  async function importShare(share: Pick<ShareSummary, "id" | "title">) {
    setImporting(share.id);
    try {
      const result = await sharingApi.import(share.id);
      onImported?.(share.id, result.documentId);
      toast.show("success", result.alreadyImported ? "Already in your library. Opening your copy." : `Imported “${share.title}”. It's yours now.`);
      navigate(`/documents/${result.documentId}`);
    } catch (e) {
      toast.show("error", errorMessage(e));
    } finally {
      setImporting(null);
    }
  }

  return { importing, importShare };
}
