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
  upvotes: number;
  downvotes: number;
  commentCount: number;
  sharedAt: string;
  updatedAt: string;
}

/** 1 = up, -1 = down, 0 = no vote. */
export type VoteValue = 1 | -1 | 0;

export interface Votes {
  upvotes: number;
  downvotes: number;
  myVote: VoteValue;
}

export interface ShareComment {
  id: string;
  body: string;
  author: string;
  createdAt: string;
  /** Written by the person who shared the document. */
  bySharer: boolean;
  mine: boolean;
  /** The viewer wrote it, or shared the document. */
  canDelete: boolean;
}

/** Share of upvotes, when anyone voted. */
export const helpfulPercent = (v: Votes): number | null =>
  v.upvotes + v.downvotes === 0 ? null : Math.round((100 * v.upvotes) / (v.upvotes + v.downvotes));

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
  votes: Votes;
  commentCount: number;
  /** Shared by the viewer (cannot be imported or voted on). */
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

export type ExploreSort = "new" | "top" | "popular";

export const categoryEmoji = (c: Pick<Category, "emoji">): EmojiName =>
  (EMOJI_NAMES as readonly string[]).includes(c.emoji) ? (c.emoji as EmojiName) : "ledger";

const KIND_LABELS: Record<string, string> = {
  SYLLABUS: "Syllabus",
  TEXTBOOK: "Textbook",
  LECTURE_NOTES: "Lecture notes",
  HANDOUT: "Handout",
};
export const kindLabel = (kind: string) => KIND_LABELS[kind] ?? kind.charAt(0) + kind.slice(1).toLowerCase().replaceAll("_", " ");
