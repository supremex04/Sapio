import type { FigureView } from "../../shared/ui/Markdown";

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
  /** Set on documents imported from Explore (a copy of someone else's shared document). */
  origin: { shareId: string | null; sharedBy: string; importedAt: string } | null;
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

// ---- Preview (knowledge store reading view)

export interface ContentBlock {
  type: "text" | "figure";
  markdown: string | null;
  figureId: string | null;
  page: number;
}

export interface ContentSection {
  id: string;
  parentId: string | null;
  path: string;
  label: string;
  title: string;
  level: number;
  pageFrom: number;
  pageTo: number;
  blocks: ContentBlock[];
  /** Set on study units (they get Expand / Revision): what the unit covers. */
  unit: StudyUnit | null;
}

export interface StudyUnit {
  fromLabel: string;
  toLabel: string;
  sections: number;
  pageFrom: number;
  pageTo: number;
}

export interface DocumentContent {
  documentId: string;
  sections: ContentSection[];
  figures: Record<string, FigureView>;
}

export type NoteStyle = "EXPAND" | "REVISION";

export interface SectionNotes {
  nodeId: string;
  style: NoteStyle;
  scope: "section" | "overview";
  markdown: string;
  figures: Record<string, FigureView>;
  generatedAt: string;
}
