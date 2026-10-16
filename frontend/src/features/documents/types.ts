import { formatDuration } from "../../shared/lib/format";
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
  /** A copy: imported from Explore, or shared by the learner's teacher (CLASS). */
  origin: { shareId: string | null; sharedBy: string; importedAt: string; kind: "EXPLORE" | "CLASS" } | null;
  /** Documents are parsed; audio and video are transcribed and turned into notes (pages are then minutes). */
  kind: DocumentKind;
  /** Length of a recording; null for documents. */
  durationSeconds: number | null;
}

export type DocumentKind = "DOCUMENT" | "AUDIO" | "VIDEO";
export const isMedia = (doc: Pick<DocumentDto, "kind">) => doc.kind === "AUDIO" || doc.kind === "VIDEO";

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

/** Keep in sync with backend SupportedFileType and sapio.documents.max-size-bytes / max-media-bytes. */
const DOCUMENT_EXTENSIONS = ["pdf", "docx", "png", "jpg", "jpeg", "webp", "tif", "tiff", "bmp"];
const MEDIA_EXTENSIONS = ["mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "mp4", "m4v", "mov", "webm", "mkv", "avi"];
export const ACCEPTED_FILE_TYPES = [...DOCUMENT_EXTENSIONS, ...MEDIA_EXTENSIONS].map((e) => `.${e}`).join(",");
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
/** 1 GiB unless the build sets VITE_MAX_MEDIA_BYTES (hosts with a request size cap; matches SAPIO_MAX_MEDIA_BYTES). */
export const MAX_MEDIA_BYTES = Number(import.meta.env.VITE_MAX_MEDIA_BYTES) || 1024 * 1024 * 1024;

const extension = (name: string) => name.slice(name.lastIndexOf(".") + 1).toLowerCase();
export const isMediaFile = (name: string) => MEDIA_EXTENSIONS.includes(extension(name));
/** The size limit for this file: recordings may be much larger than documents. */
export const uploadLimit = (name: string) => (isMediaFile(name) ? MAX_MEDIA_BYTES : MAX_FILE_BYTES);

/** "52 pages" for documents, "48m" for recordings. */
export function lengthLabel(doc: Pick<DocumentDto, "kind" | "pageCount" | "durationSeconds">): string | null {
  if (isMedia(doc)) return doc.durationSeconds != null ? formatDuration(doc.durationSeconds) : null;
  return doc.pageCount != null ? `${doc.pageCount} page${doc.pageCount === 1 ? "" : "s"}` : null;
}

/** A span of a document: "pp. 3–8" for documents; for recordings (page = minute) "12:00–19:00". */
export function spanLabel(from: number, to: number, media: boolean): string {
  if (!media) return from === to ? `p. ${from}` : `pp. ${from}–${to}`;
  return `${clock((from - 1) * 60)}–${clock(to * 60)}`;
}

function clock(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:00` : `${String(m).padStart(2, "0")}:00`;
}

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
  /** 0 as extracted; a save names the revision it started from. */
  revision: number;
  /** When the learner last saved this section (null: never edited). */
  editedAt: string | null;
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
  /** Written before the learner's latest significant edits to the covered text (a rewrite is on request). */
  stale: boolean;
}
