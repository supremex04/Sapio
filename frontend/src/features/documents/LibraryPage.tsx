import { ArrowRight, RotateCcw, Trash2, Upload } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { usePolling } from "../../shared/hooks/usePolling";
import { formatBytes, formatRelative } from "../../shared/lib/format";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { Skeleton } from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import { documentsApi } from "./api";
import { pipelineState } from "./pipeline";
import { PipelineStepper } from "./PipelineStepper";
import { DocumentWorkbench } from "./DocumentWorkbench";
import { displayTitle, isSettled, MAX_FILE_BYTES, type DocumentDto } from "./types";
import { UploadDropzone } from "./UploadDropzone";
import "./documents.css";

export function LibraryPage() {
  const toast = useToast();
  const [documents, setDocuments] = useState<DocumentDto[] | null>(null);
  const [uploading, setUploading] = useState(0);
  const [errors, setErrors] = useState<string[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);

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

  usePolling(load, 2500, documents?.some((d) => !isSettled(d)) ?? false);

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
        <header className="lib-header">
          <div className="lib-header-text">
            <h1>Your library</h1>
            <p className="muted">Everything you upload becomes a course with lessons, practice and reviews.</p>
          </div>
          <Button variant="primary" icon={<Upload size={18} />} loading={uploading > 0} onClick={() => fileInput.current?.click()}>
            Upload
          </Button>
        </header>

        <UploadDropzone onFiles={upload} busy={uploading > 0} inputRef={fileInput} />
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
            <EmptyState icon={<Emoji name="books" size={64} />} title="Your shelf is empty">
              Upload a document above. Most courses are ready in under two minutes.
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
    <Card tone={state.failed ? "red" : undefined} className="doc-card">
      <div className="row-between">
        <Emoji name={state.done ? "books" : "page-facing-up"} size={40} />
        {state.done ? (
          <Badge tone="success">Ready</Badge>
        ) : state.failed ? (
          <Badge tone="danger">Needs another try</Badge>
        ) : state.paused ? (
          <Badge tone="warning">Paused</Badge>
        ) : (
          <Badge tone="primary" icon={<span className="spinner" />}>
            Working
          </Badge>
        )}
      </div>
      {state.done ? (
        <Link to={`/documents/${doc.id}`} className="doc-title break">
          {displayTitle(doc)}
        </Link>
      ) : (
        <h3 className="doc-title break">{displayTitle(doc)}</h3>
      )}
      <p className="small muted">
        {doc.title && <span className="break">{doc.filename} · </span>}
        {doc.pageCount != null && `${doc.pageCount} page${doc.pageCount === 1 ? "" : "s"} · `}
        {formatRelative(doc.createdAt)}
      </p>
      {!state.done && <PipelineStepper doc={doc} compact />}
      {!state.done && !state.failed && <DocumentWorkbench doc={doc} compact />}
      {state.failed && doc.errorMessage && <p className="small doc-error">{doc.errorMessage}</p>}
      <div className="doc-actions">
        {state.done && (
          <ButtonLink to={`/documents/${doc.id}`} variant="soft" size="sm" iconRight={<ArrowRight size={16} />}>
            Open
          </ButtonLink>
        )}
        {state.failed && (
          <Button size="sm" variant="danger" icon={<RotateCcw size={16} />} onClick={onRetry}>
            Retry
          </Button>
        )}
        <span style={{ marginLeft: "auto" }}>
          <ConfirmButton onConfirm={onDelete} icon={<Trash2 size={16} />} prompt="Delete?" confirmLabel="Delete" ariaLabel={`Delete ${displayTitle(doc)}`}>
            <span className="visually-hidden">Delete</span>
          </ConfirmButton>
        </span>
      </div>
    </Card>
  );
}
