/**
 * Markdown formatting as pure text transforms (the editor wires them to keys and toolbar buttons). Each takes the
 * document and the selection and returns the changes plus where the selection goes afterwards, so they are
 * tested without an editor. Every command toggles: applying it twice gives back the original text.
 */

export interface Selection {
  from: number;
  to: number;
}

export interface TextEdit {
  changes: { from: number; to: number; insert: string }[];
  /** In the coordinates of the changed document. */
  selection: Selection;
}

/** Applies an edit to a string (for tests and previews). */
export function apply(doc: string, edit: TextEdit): string {
  let out = doc;
  for (const c of [...edit.changes].sort((a, b) => b.from - a.from)) out = out.slice(0, c.from) + c.insert + out.slice(c.to);
  return out;
}

function runBefore(doc: string, pos: number, ch: string): number {
  let n = 0;
  while (pos - n - 1 >= 0 && doc[pos - n - 1] === ch) n++;
  return n;
}

function runAfter(doc: string, pos: number, ch: string): number {
  let n = 0;
  while (pos + n < doc.length && doc[pos + n] === ch) n++;
  return n;
}

/** Whether [from, to) sits inside `marker…marker` (for "*": bold is a run of 2, italic an odd run). */
function isWrapped(doc: string, sel: Selection, marker: string): boolean {
  const ch = marker[0]!;
  const before = runBefore(doc, sel.from, ch);
  const after = runAfter(doc, sel.to, ch);
  if (marker === "*") return before % 2 === 1 && after % 2 === 1;
  if (marker === "**") return before >= 2 && after >= 2;
  if (marker === "$" || marker === "`") return before === 1 && after === 1;
  return doc.slice(sel.from - marker.length, sel.from) === marker && doc.slice(sel.to, sel.to + marker.length) === marker;
}

/** Bold (`**`), italic (`*`), strikethrough (`~~`), inline code (`` ` ``) or inline math (`$`). */
export function toggleWrap(doc: string, sel: Selection, marker: string): TextEdit {
  const n = marker.length;
  if (isWrapped(doc, sel, marker)) {
    return {
      changes: [
        { from: sel.from - n, to: sel.from, insert: "" },
        { from: sel.to, to: sel.to + n, insert: "" },
      ],
      selection: { from: sel.from - n, to: sel.to - n },
    };
  }
  const text = doc.slice(sel.from, sel.to);
  // The selection includes the markers itself: unwrap from the inside.
  if (text.length >= 2 * n && text.startsWith(marker) && text.endsWith(marker) && isWrapped(text, { from: n, to: text.length - n }, marker)) {
    return {
      changes: [{ from: sel.from, to: sel.to, insert: text.slice(n, text.length - n) }],
      selection: { from: sel.from, to: sel.to - 2 * n },
    };
  }
  // Keep surrounding spaces outside the markers ("**word** " not "**word **"), which Markdown requires.
  const lead = text.length - text.trimStart().length;
  const trail = text.length - text.trimEnd().length;
  const from = sel.from + (text.trim() ? lead : 0);
  const to = sel.to - (text.trim() ? trail : 0);
  return {
    changes: [
      { from, to: from, insert: marker },
      { from: to, to, insert: marker },
    ],
    selection: { from: from + n, to: to + n },
  };
}

const URL_LIKE = /^(https?:\/\/|www\.)\S+$/i;

/** A link: the selection becomes its text (or its URL, if it is one); the part left to type is selected. */
export function insertLink(doc: string, sel: Selection): TextEdit {
  const text = doc.slice(sel.from, sel.to);
  if (URL_LIKE.test(text.trim())) {
    const insert = `[](${text.trim()})`;
    return { changes: [{ from: sel.from, to: sel.to, insert }], selection: { from: sel.from + 1, to: sel.from + 1 } };
  }
  const label = text || "link text";
  const insert = `[${label}](url)`;
  const urlAt = sel.from + label.length + 3;
  return {
    changes: [{ from: sel.from, to: sel.to, insert }],
    selection: text ? { from: urlAt, to: urlAt + 3 } : { from: sel.from + 1, to: sel.from + 1 + label.length },
  };
}

/** Pasting a URL over selected text links the text instead of replacing it. */
export function pasteUrlAsLink(doc: string, sel: Selection, pasted: string): TextEdit | null {
  const url = pasted.trim();
  if (sel.from === sel.to || !URL_LIKE.test(url) || /\n/.test(doc.slice(sel.from, sel.to))) return null;
  const insert = `[${doc.slice(sel.from, sel.to)}](${url})`;
  return { changes: [{ from: sel.from, to: sel.to, insert }], selection: { from: sel.from + insert.length, to: sel.from + insert.length } };
}

export type LineKind = "h1" | "h2" | "h3" | "bullet" | "numbered" | "task" | "quote";

const PREFIX = /^(\s*)(#{1,6}\s+|[-*+]\s+\[[ xX]\]\s+|[-*+]\s+|\d+[.)]\s+|>\s?)?/;

function prefixFor(kind: LineKind, index: number): string {
  switch (kind) {
    case "h1":
      return "# ";
    case "h2":
      return "## ";
    case "h3":
      return "### ";
    case "bullet":
      return "- ";
    case "numbered":
      return `${index + 1}. `;
    case "task":
      return "- [ ] ";
    case "quote":
      return "> ";
  }
}

function hasKind(prefix: string, kind: LineKind): boolean {
  const p = prefix.trim();
  switch (kind) {
    case "h1":
      return p === "#";
    case "h2":
      return p === "##";
    case "h3":
      return p === "###";
    case "bullet":
      return /^[-*+]$/.test(p);
    case "numbered":
      return /^\d+[.)]$/.test(p);
    case "task":
      return /^[-*+]\s+\[[ xX]\]$/.test(p);
    case "quote":
      return p === ">";
  }
}

/**
 * Headings, lists, task lists and quotes on every line the selection touches. If all of them already are of that
 * kind it is removed; otherwise each line gets it (replacing another heading / list / quote marker).
 */
export function toggleLines(doc: string, sel: Selection, kind: LineKind): TextEdit {
  const start = doc.lastIndexOf("\n", sel.from - 1) + 1;
  const endBreak = doc.indexOf("\n", sel.to);
  const end = endBreak < 0 ? doc.length : endBreak;
  const lines = doc.slice(start, end).split("\n");
  const parsed = lines.map((line) => {
    const m = PREFIX.exec(line)!;
    return { indent: m[1] ?? "", prefix: m[2] ?? "", rest: line.slice((m[1] ?? "").length + (m[2] ?? "").length) };
  });
  const content = parsed.filter((l) => l.rest.trim() || l.prefix);
  const remove = content.length > 0 && content.every((l) => hasKind(l.prefix, kind));
  let n = 0;
  const next = parsed
    .map((l) => {
      if (!l.rest.trim() && !l.prefix) return l.indent + l.rest;
      return l.indent + (remove ? "" : prefixFor(kind, n++)) + l.rest;
    })
    .join("\n");
  const delta = next.length - (end - start);
  const single = lines.length === 1;
  return {
    changes: [{ from: start, to: end, insert: next }],
    // A single line: keep the cursor at the same spot in the text; several: select them all.
    selection: single ? { from: Math.max(start, sel.from + delta), to: Math.max(start, sel.to + delta) } : { from: start, to: start + next.length },
  };
}

/** A fenced block (``` code or $$ display math) around the selected lines, or an empty one to type in. */
export function insertBlock(doc: string, sel: Selection, fence: "```" | "$$"): TextEdit {
  const text = doc.slice(sel.from, sel.to);
  const before = sel.from === 0 || doc[sel.from - 1] === "\n" ? "" : "\n";
  const after = sel.to === doc.length || doc[sel.to] === "\n" ? "" : "\n";
  const insert = `${before}${fence}\n${text}\n${fence}${after}`;
  const inner = sel.from + before.length + fence.length + 1;
  return { changes: [{ from: sel.from, to: sel.to, insert }], selection: { from: inner, to: inner + text.length } };
}

/** A small table to fill in, with the first header cell selected. */
export function insertTable(doc: string, sel: Selection): TextEdit {
  const before = sel.from === 0 || doc[sel.from - 1] === "\n" ? "" : "\n\n";
  const insert = `${before}| Column | Column |\n| --- | --- |\n|  |  |\n`;
  const at = sel.from + before.length + 2;
  return { changes: [{ from: sel.from, to: sel.to, insert }], selection: { from: at, to: at + 6 } };
}

/**
 * Where to put the cursor in the Markdown for a spot the learner clicked in the rendered text. The rendered text
 * lacks the Markdown syntax, so the text just before the click is looked up in the source, shortening it until it
 * is found; among several matches the one nearest the proportional position wins.
 */
export function sourceOffset(markdown: string, renderedBefore: string, renderedTotal: number): number {
  // Compare with whitespace collapsed on both sides (rendering turns line breaks into spaces), mapping back.
  let flat = "";
  const origin: number[] = [];
  for (let i = 0; i < markdown.length; i++) {
    const space = /\s/.test(markdown[i]!);
    if (space && flat.endsWith(" ")) continue;
    flat += space ? " " : markdown[i];
    origin.push(i);
  }
  const estimate = Math.round(flat.length * (renderedTotal ? renderedBefore.length / renderedTotal : 0));
  const tail = renderedBefore.replace(/\s+/g, " ").trimStart();
  for (let len = Math.min(40, tail.length); len >= 3; len = len > 12 ? Math.floor(len * 0.7) : len - 1) {
    const needle = tail.slice(tail.length - len);
    let best = -1;
    for (let i = flat.indexOf(needle); i >= 0; i = flat.indexOf(needle, i + 1)) {
      const end = i + needle.length;
      if (best < 0 || Math.abs(end - estimate) < Math.abs(best - estimate)) best = end;
    }
    if (best >= 0) return best >= origin.length ? markdown.length : origin[best]!;
  }
  const at = Math.min(origin.length - 1, Math.max(0, estimate));
  return origin.length ? origin[at]! : 0;
}

/** The formula around a position: `$$…$$` display math (may span lines) or `$…$` inline math on one line. */
export function mathAt(doc: string, pos: number): { from: number; to: number; tex: string; display: boolean } | null {
  // Display math: inside when an odd number of $$ fences come before the position.
  let count = 0;
  let open = -1;
  for (let i = doc.indexOf("$$"); i >= 0 && i < pos; i = doc.indexOf("$$", i + 2)) {
    count++;
    open = i;
  }
  if (count % 2 === 1) {
    const close = doc.indexOf("$$", Math.max(pos, open + 2));
    const end = close < 0 ? doc.length : close;
    const tex = doc.slice(open + 2, end).trim();
    return tex ? { from: open, to: close < 0 ? doc.length : close + 2, tex, display: true } : null;
  }
  const lineStart = doc.lastIndexOf("\n", pos - 1) + 1;
  const lineEnd = doc.indexOf("\n", pos);
  const line = doc.slice(lineStart, lineEnd < 0 ? doc.length : lineEnd);
  const inline = /(?<![$\\])\$(?!\$)([^$\n]+?)(?<!\\)\$(?!\$)/g;
  for (let m = inline.exec(line); m; m = inline.exec(line)) {
    const from = lineStart + m.index;
    const to = from + m[0].length;
    if (pos >= from && pos <= to && m[1]!.trim()) return { from, to, tex: m[1]!, display: false };
  }
  return null;
}
