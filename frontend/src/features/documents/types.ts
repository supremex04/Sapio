export type DocumentStatus = "PENDING" | "EXTRACTING" | "INDEXING" | "ANALYZING" | "READY" | "FAILED";

export interface DocumentDto {
  id: string;
  filename: string;
  title: string | null;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  engine: string | null;
  pageCount: number | null;
  textPages: number | null;
  ocrPages: number | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  /** Set when a transient failure (AI busy) will be retried automatically at this time. */
  retryAt: string | null;
}

export interface DocumentResult {
  id: string;
  format: "markdown";
  content: string;
}

/** Nothing more will happen without the user: ready, or failed with no automatic retry scheduled. */
export const isSettled = (doc: Pick<DocumentDto, "status" | "retryAt">) =>
  doc.status === "READY" || (doc.status === "FAILED" && !doc.retryAt);
export const hasExtractedText = (status: DocumentStatus) =>
  status === "INDEXING" || status === "ANALYZING" || status === "READY";
export const displayTitle = (doc: Pick<DocumentDto, "title" | "filename">) => doc.title?.trim() || doc.filename;

/** Keep in sync with backend SupportedFileType. */
export const ACCEPTED_FILE_TYPES = ".pdf,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp";
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
