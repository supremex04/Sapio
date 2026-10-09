export type DocumentStatus = "PENDING" | "EXTRACTING" | "ANALYZING" | "READY" | "FAILED";

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
}

export interface DocumentResult {
  id: string;
  format: "markdown";
  content: string;
}

export const isTerminal = (status: DocumentStatus) => status === "READY" || status === "FAILED";
export const hasExtractedText = (status: DocumentStatus) => status === "ANALYZING" || status === "READY";
export const displayTitle = (doc: Pick<DocumentDto, "title" | "filename">) => doc.title?.trim() || doc.filename;

/** Keep in sync with backend SupportedFileType. */
export const ACCEPTED_FILE_TYPES = ".pdf,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp";
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
