import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { formatBytes, formatDateTime } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { documentsApi } from "./api";
import { DocumentStatusBadge } from "./DocumentStatusBadge";
import { isTerminal, MAX_FILE_BYTES, type DocumentDto } from "./types";
import { UploadDropzone } from "./UploadDropzone";
import { usePolling } from "./usePolling";

export function DocumentsPage() {
  const [documents, setDocuments] = useState<DocumentDto[] | null>(null);
  const [uploading, setUploading] = useState(0);
  const [errors, setErrors] = useState<string[]>([]);

  const load = useCallback(async () => {
    try {
      setDocuments((await documentsApi.list()).items);
    } catch (e) {
      setErrors([errorMessage(e)]);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const hasActiveJobs = documents?.some((d) => !isTerminal(d.status)) ?? false;
  usePolling(load, 2000, hasActiveJobs);

  async function upload(files: File[]) {
    setErrors([]);
    const problems: string[] = [];
    const accepted = files.filter((f) => {
      if (f.size > MAX_FILE_BYTES) problems.push(`${f.name}: larger than ${formatBytes(MAX_FILE_BYTES)}`);
      return f.size <= MAX_FILE_BYTES;
    });
    setUploading((n) => n + accepted.length);
    await Promise.all(
      accepted.map(async (file) => {
        try {
          const created = await documentsApi.upload(file);
          setDocuments((docs) => [created, ...(docs ?? [])]);
        } catch (e) {
          problems.push(`${file.name}: ${errorMessage(e)}`);
        } finally {
          setUploading((n) => n - 1);
        }
      }),
    );
    setErrors(problems);
  }

  async function remove(doc: DocumentDto) {
    try {
      await documentsApi.remove(doc.id);
      setDocuments((docs) => docs?.filter((d) => d.id !== doc.id) ?? null);
    } catch (e) {
      setErrors([errorMessage(e)]);
    }
  }

  return (
    <div className="stack-lg">
      <header>
        <h1>Document to text</h1>
        <p className="muted">Upload scanned notes, worksheets or PDFs and get editable Markdown back.</p>
      </header>

      <UploadDropzone onFiles={upload} />
      {uploading > 0 && <Alert tone="info">Uploading {uploading} file(s)…</Alert>}
      {errors.length > 0 && (
        <Alert>
          {errors.map((e) => (
            <div key={e}>{e}</div>
          ))}
        </Alert>
      )}

      <section className="card">
        <h2>Your documents</h2>
        {documents === null ? (
          <p className="muted">Loading…</p>
        ) : documents.length === 0 ? (
          <p className="muted">No documents yet. Upload one above to get started.</p>
        ) : (
          <div className="table-wrap">
            <table className="doc-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Status</th>
                  <th>Size</th>
                  <th>Uploaded</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {documents.map((doc) => (
                  <tr key={doc.id}>
                    <td>
                      <Link to={`/documents/${doc.id}`} className="doc-name">
                        {doc.filename}
                      </Link>
                      {doc.status === "FAILED" && doc.errorMessage && <div className="small error-text">{doc.errorMessage}</div>}
                    </td>
                    <td>
                      <DocumentStatusBadge status={doc.status} />
                    </td>
                    <td className="nowrap">{formatBytes(doc.sizeBytes)}</td>
                    <td className="nowrap">{formatDateTime(doc.createdAt)}</td>
                    <td className="actions">
                      <button className="btn btn-ghost btn-sm" onClick={() => remove(doc)} aria-label={`Delete ${doc.filename}`}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
