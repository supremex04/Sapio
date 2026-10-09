import { useCallback, useEffect, useState } from "react";
import Markdown from "react-markdown";
import { Link, useNavigate, useParams } from "react-router";
import remarkGfm from "remark-gfm";
import { errorMessage } from "../../shared/api/errors";
import { saveBlob } from "../../shared/lib/download";
import { formatBytes, formatDateTime } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { documentsApi } from "./api";
import { DocumentStatusBadge } from "./DocumentStatusBadge";
import { isTerminal, type DocumentDto } from "./types";
import { usePolling } from "./usePolling";

type View = "preview" | "markdown";

export function DocumentDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [doc, setDoc] = useState<DocumentDto | null>(null);
  const [content, setContent] = useState<string | null>(null);
  const [view, setView] = useState<View>("preview");
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    try {
      const fresh = await documentsApi.get(id);
      setDoc(fresh);
      if (fresh.status === "COMPLETED") setContent((await documentsApi.result(id)).content);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);
  usePolling(load, 2000, doc !== null && !isTerminal(doc.status));

  async function download() {
    if (!doc) return;
    try {
      const { blob, filename } = await documentsApi.download(doc.id, "document.md");
      saveBlob(blob, filename);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function copy() {
    if (content === null) return;
    await navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  async function remove() {
    if (!doc) return;
    try {
      await documentsApi.remove(doc.id);
      navigate("/", { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  if (error && !doc) {
    return (
      <div className="stack">
        <Link to="/">← All documents</Link>
        <Alert>{error}</Alert>
      </div>
    );
  }
  if (!doc) return <p className="muted">Loading…</p>;

  return (
    <div className="stack-lg">
      <div>
        <Link to="/" className="small">
          ← All documents
        </Link>
        <div className="title-row">
          <h1 className="break">{doc.filename}</h1>
          <DocumentStatusBadge status={doc.status} />
        </div>
        <p className="muted small">
          {formatBytes(doc.sizeBytes)} · uploaded {formatDateTime(doc.createdAt)}
          {doc.pageCount != null && ` · ${doc.pageCount} page${doc.pageCount === 1 ? "" : "s"}`}
          {doc.engine && ` · ${doc.engine}`}
        </p>
      </div>

      {error && <Alert>{error}</Alert>}
      {doc.status === "FAILED" && <Alert>{doc.errorMessage ?? "Conversion failed."}</Alert>}
      {!isTerminal(doc.status) && (
        <Alert tone="info">Extracting text… this page updates automatically. Large PDFs can take a few minutes.</Alert>
      )}

      {doc.status === "COMPLETED" && content !== null && (
        <section className="card result">
          <div className="result-toolbar">
            <div className="segmented" role="tablist">
              {(["preview", "markdown"] as const).map((v) => (
                <button key={v} role="tab" aria-selected={view === v} className={view === v ? "active" : ""} onClick={() => setView(v)}>
                  {v === "preview" ? "Preview" : "Markdown"}
                </button>
              ))}
            </div>
            <div className="row">
              <button className="btn btn-secondary btn-sm" onClick={copy}>
                {copied ? "Copied" : "Copy"}
              </button>
              <button className="btn btn-primary btn-sm" onClick={download}>
                Download .md
              </button>
            </div>
          </div>
          {content.trim() === "" ? (
            <p className="muted">No text was found in this document.</p>
          ) : view === "preview" ? (
            <article className="markdown-body">
              <Markdown remarkPlugins={[remarkGfm]}>{content}</Markdown>
            </article>
          ) : (
            <pre className="markdown-source">{content}</pre>
          )}
        </section>
      )}

      <div>
        <button className="btn btn-ghost btn-sm danger" onClick={remove}>
          Delete document
        </button>
      </div>
    </div>
  );
}
