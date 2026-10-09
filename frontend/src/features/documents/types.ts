export type DocumentStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface DocumentDto {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  engine: string | null;
  pageCount: number | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DocumentResult {
  id: string;
  format: "markdown";
  content: string;
}

export const isTerminal = (status: DocumentStatus) => status === "COMPLETED" || status === "FAILED";

/** Keep in sync with backend SupportedFileType. */
export const ACCEPTED_FILE_TYPES = ".pdf,.png,.jpg,.jpeg,.webp,.tif,.tiff,.bmp";
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
