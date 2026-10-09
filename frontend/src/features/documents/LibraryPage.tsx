import { BookOpen, FileText, RotateCcw, Trash2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { usePolling } from "../../shared/hooks/usePolling";
import { formatBytes, formatRelative } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { Skeleton } from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import { documentsApi } from "./api";
import { pipelineState } from "./pipeline";
import { PipelineStepper } from "./PipelineStepper";
import { displayTitle, isTerminal, MAX_FILE_BYTES, type DocumentDto } from "./types";
import { UploadDropzone } from "./UploadDropzone";
import "./documents.css";

export function LibraryPage() {
  const toast = useToast();
  const [documents, setDocuments] = useState<DocumentDto[] | null>(null);
  const [uploading, setUploading] = useState(0);
  const [errors, setErrors] = useState<string[]>([]);

  const load = useCallback(async () => {
    try {
      setDocuments((await documentsApi.list(0, 50)).items);
    } catch (e) {
      setErrors([errorMessage(e)]);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  usePolling(load, 2500, documents?.some((d) => !isTerminal(d.status)) ?? false);

  async function upload(files: File[]) {
    setErrors([]);
    const problems: string[] = [];
    const accepted = files.filter((f) => {
      const ok = f.size <= MAX_FILE_BYTES;
      if (!ok) problems.push(`${f.name}: larger than ${formatBytes(MAX_FILE_BYTES)}`);
      return ok;
    });
    setUploading((n) => n + accepted.length);
    await Promise.all(
      accepted.map(async (file) => {
        try {
          const created = await documentsApi.upload(file);
          setDocuments((docs) => [created, ...(docs ?? [])]);
          toast.show("success", <span>Uploaded {file.name}. We'll build your study guide next.</span>);
        } catch (e) {
          problems.push(`${file.name}: ${errorMessage(e)}`);
        } finally {
          setUploading((n) => n - 1);
        }
      }),
    );
    setErrors(problems);
  }

  async function retry(doc: DocumentDto) {
    try {
      const updated = await documentsApi.retry(doc.id);
      setDocuments((docs) => docs?.map((d) => (d.id === doc.id ? updated : d)) ?? null);
    } catch (e) {
      toast.show("error", errorMessage(e));
    }
  }

  async function remove(doc: DocumentDto) {
    try {
      await documentsApi.remove(doc.id);
      setDocuments((docs) => docs?.filter((d) => d.id !== doc.id) ?? null);
    } catch (e) {
      toast.show("error", errorMessage(e));
    }
  }

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="page-header" style={{ marginBottom: 0 }}>
          <div>
            <h1>Your library</h1>
            <p>Everything you upload becomes a study guide with lessons, practice and reviews.</p>
          </div>
        </header>

        <UploadDropzone onFiles={upload} busy={uploading > 0} />
        {errors.length > 0 && (
          <Alert>
            {errors.map((e) => (
              <div key={e}>{e}</div>
            ))}
          </Alert>
        )}

        {documents === null ? (
          <div className="doc-grid">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} height={180} radius="var(--radius-lg)" />
            ))}
          </div>
        ) : documents.length === 0 ? (
          <Card>
            <EmptyState icon={<BookOpen size={24} />} title="Your shelf is empty">
              Upload a document above. Most study guides are ready in under two minutes.
            </EmptyState>
          </Card>
        ) : (
          <motion.div className="doc-grid" layout>
            <AnimatePresence initial={false}>
              {documents.map((doc) => (
                <motion.div key={doc.id} layout initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.97 }}>
                  <DocumentCard doc={doc} onRetry={() => retry(doc)} onDelete={() => remove(doc)} />
                </motion.div>
              ))}
            </AnimatePresence>
          </motion.div>
        )}
      </div>
    </PageTransition>
  );
}

function DocumentCard({ doc, onRetry, onDelete }: { doc: DocumentDto; onRetry: () => void; onDelete: () => Promise<void> }) {
  const state = pipelineState(doc);
  return (
    <Card className="doc-card">
      <div className="row-between">
        <span className="doc-type" aria-hidden>
          <FileText size={18} />
        </span>
        {state.done ? (
          <Badge tone="success">Ready</Badge>
        ) : state.failed ? (
          <Badge tone="warning">Needs another try</Badge>
        ) : (
          <Badge tone="primary" icon={<span className="spinner" />}>
            {state.label}
          </Badge>
        )}
      </div>
      <Link to={`/documents/${doc.id}`} className="doc-title break">
        {displayTitle(doc)}
      </Link>
      <p className="xs muted">
        {doc.title && <span className="break">{doc.filename} · </span>}
        {doc.pageCount != null && `${doc.pageCount} page${doc.pageCount === 1 ? "" : "s"} · `}
        {formatRelative(doc.createdAt)}
      </p>
      {!state.done && <PipelineStepper doc={doc} compact />}
      {state.failed && doc.errorMessage && <p className="small doc-error">{doc.errorMessage}</p>}
      <div className="doc-actions">
        {state.done && (
          <Link to={`/documents/${doc.id}`} className="btn btn-soft btn-sm">
            Open study guide
          </Link>
        )}
        {state.failed && (
          <Button size="sm" variant="soft" icon={<RotateCcw size={14} />} onClick={onRetry}>
            Try again
          </Button>
        )}
        <span style={{ marginLeft: "auto" }}>
          <ConfirmButton onConfirm={onDelete} icon={<Trash2 size={14} />} prompt="Delete?" confirmLabel="Delete" ariaLabel={`Delete ${displayTitle(doc)}`}>
            <span className="visually-hidden">Delete</span>
          </ConfirmButton>
        </span>
      </div>
    </Card>
  );
}
