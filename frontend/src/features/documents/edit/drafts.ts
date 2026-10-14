/**
 * Unsaved edits are kept in this browser while typing, so a closed tab or a crash loses nothing; the editor
 * offers to restore them the next time the section is opened. Storage may be unavailable: every access is guarded.
 */
export interface Draft {
  title: string;
  markdown: string;
  /** The section revision the draft started from. */
  revision: number;
  savedAt: string;
}

const key = (sectionId: string) => `sapio.draft.${sectionId}`;

export function readDraft(sectionId: string): Draft | null {
  try {
    const raw = localStorage.getItem(key(sectionId));
    if (!raw) return null;
    const draft = JSON.parse(raw) as Draft;
    return typeof draft.markdown === "string" && typeof draft.title === "string" ? draft : null;
  } catch {
    return null;
  }
}

export function writeDraft(sectionId: string, draft: Draft): void {
  try {
    localStorage.setItem(key(sectionId), JSON.stringify(draft));
  } catch {
    /* not kept: fine */
  }
}

export function clearDraft(sectionId: string): void {
  try {
    localStorage.removeItem(key(sectionId));
  } catch {
    /* nothing to clear */
  }
}
