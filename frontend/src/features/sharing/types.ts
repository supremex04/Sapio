import { EMOJI_NAMES, type EmojiName } from "../../shared/illustrations/emojiNames";

export interface Category {
  slug: string;
  name: string;
  /** Illustration name; see categoryEmoji. */
  emoji: string;
}

/** One of the viewer's own shares. */
export interface MyShare {
  id: string;
  documentId: string;
  category: Category;
  description: string;
  importCount: number;
  sharedAt: string;
  updatedAt: string;
}

export interface ShareSummary {
  id: string;
  title: string;
  summary: string;
  category: Category;
  kind: string;
  language: string;
  pageCount: number;
  topicCount: number;
  sharedBy: string;
  sharedAt: string;
  importCount: number;
  /** Shared by the viewer (cannot be imported). */
  mine: boolean;
  /** The viewer's copy, if they imported it already. */
  importedDocumentId: string | null;
}

export interface ShareDetail {
  share: ShareSummary;
  description: string;
  topics: string[];
  updatedAt: string;
}

export interface ImportResult {
  documentId: string;
  alreadyImported: boolean;
}

export type ExploreSort = "new" | "popular";

export const categoryEmoji = (c: Pick<Category, "emoji">): EmojiName =>
  (EMOJI_NAMES as readonly string[]).includes(c.emoji) ? (c.emoji as EmojiName) : "books";

const KIND_LABELS: Record<string, string> = {
  SYLLABUS: "Syllabus",
  TEXTBOOK: "Textbook",
  LECTURE_NOTES: "Lecture notes",
  HANDOUT: "Handout",
};
export const kindLabel = (kind: string) => KIND_LABELS[kind] ?? kind.charAt(0) + kind.slice(1).toLowerCase().replaceAll("_", " ");
