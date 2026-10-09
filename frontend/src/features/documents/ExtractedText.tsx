import { Check, Copy, Download } from "lucide-react";
import { useEffect, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { saveBlob } from "../../shared/lib/download";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Markdown } from "../../shared/ui/Markdown";
import { Segmented } from "../../shared/ui/Segmented";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { documentsApi } from "./api";
import type { DocumentDto } from "./types";

/** The raw extracted Markdown: preview / source, copy, download. */
export function ExtractedText({ doc }: { doc: DocumentDto }) {
  const [content, setContent] = useState<string | null>(null);
  const [view, setView] = useState<"preview" | "markdown">("preview");
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    documentsApi.result(doc.id).then(
      (r) => setContent(r.content),
      (e) => setError(errorMessage(e)),
    );
  }, [doc.id]);

  async function download() {
    try {
      const { blob, filename } = await documentsApi.download(doc.id, "document.md");
      saveBlob(blob, filename);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function copy() {
    if (content === null) return;
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Copy isn't available here. Use download instead.");
    }
  }

  if (error) return <Alert>{error}</Alert>;
  if (content === null) return <SkeletonText lines={8} />;

  return (
    <div className="stack">
      <div className="row-between">
        <Segmented
          label="View"
          value={view}
          onChange={setView}
          options={[
            { value: "preview", label: "Preview" },
            { value: "markdown", label: "Markdown" },
          ]}
        />
        <div className="row">
          {doc.textPages != null && (
            <span className="xs muted">
              {doc.textPages} text page{doc.textPages === 1 ? "" : "s"} · {doc.ocrPages ?? 0} scanned
            </span>
          )}
          <Button size="sm" icon={copied ? <Check size={14} /> : <Copy size={14} />} onClick={copy}>
            {copied ? "Copied" : "Copy"}
          </Button>
          <Button size="sm" variant="soft" icon={<Download size={14} />} onClick={download}>
            .md
          </Button>
        </div>
      </div>
      {content.trim() === "" ? (
        <p className="muted">No text was found in this document.</p>
      ) : view === "preview" ? (
        <div className="reading">
          <Markdown>{content}</Markdown>
        </div>
      ) : (
        <pre className="markdown-source">{content}</pre>
      )}
    </div>
  );
}
