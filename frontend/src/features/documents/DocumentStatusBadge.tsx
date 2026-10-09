import type { DocumentStatus } from "./types";

const LABELS: Record<DocumentStatus, string> = {
  PENDING: "Queued",
  PROCESSING: "Processing",
  COMPLETED: "Ready",
  FAILED: "Failed",
};

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      {(status === "PENDING" || status === "PROCESSING") && <span className="spinner" aria-hidden />}
      {LABELS[status]}
    </span>
  );
}
