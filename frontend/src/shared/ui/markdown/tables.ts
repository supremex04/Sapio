/**
 * Repairs Markdown tables produced by PDF extraction:
 * - a cell's soft line breaks (`<br>`) become spaces: a table row must stay on one line;
 * - when a cell wrapped, extraction may emit the wrapped tail as its own row (`|parameter|||messy|`). A row with
 *   an empty cell that follows a complete row (no empty cells) is such a tail: its cells are appended to the row
 *   above. Header rows and tables that genuinely have empty cells are left alone.
 * - a table continued after a page break repeats its header: consecutive tables with the same header, separated
 *   only by blank lines or a page-break rule (`---`), become one table.
 * Mirrors inference/src/sapio_inference/parsing/tables.py (applied at parse time to new documents).
 */
const ROW = /^\s*\|.*\|\s*$/;
const DELIMITER = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/;
const BR = /<br\s*\/?>/gi;
const FENCE = /^\s*```/;

/** Splits a row into cells on unescaped pipes outside inline code. */
export function splitRow(line: string): string[] {
  const body = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  const cells: string[] = [];
  let current = "";
  let inCode = false;
  for (let i = 0; i < body.length; i++) {
    const ch = body[i];
    if (ch === "`") inCode = !inCode;
    if (ch === "\\" && body[i + 1] === "|") {
      current += "\\|";
      i++;
      continue;
    }
    if (ch === "|" && !inCode) {
      cells.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  cells.push(current.trim());
  return cells;
}

const joinRow = (cells: string[]) => `|${cells.join("|")}|`;
const isComplete = (cells: string[]) => cells.every((c) => c !== "");

function repairTable(lines: string[]): string[] {
  const [header, delimiter, ...body] = lines;
  const rows: string[][] = [];
  for (const line of body) {
    const cells = splitRow(line.replace(BR, " "));
    const previous = rows.at(-1);
    if (previous && cells.some((c) => c === "") && isComplete(previous)) {
      // The wrapped tail of the row above: append cell by cell.
      cells.forEach((c, i) => {
        if (c && i < previous.length) previous[i] = `${previous[i]} ${c}`;
      });
      continue;
    }
    rows.push(cells);
  }
  return [joinRow(splitRow((header ?? "").replace(BR, " "))), (delimiter ?? "").trim(), ...rows.map(joinRow)];
}

const BREAK = /^\s*(-{3,}|\*{3,}|_{3,})?\s*$/; // blank line or a horizontal rule (page break)

/** End (exclusive) of the table whose header is at `start`, or -1 if no table starts there. */
function tableEnd(lines: string[], start: number): number {
  if (!ROW.test(lines[start] ?? "") || !DELIMITER.test(lines[start + 1] ?? "")) return -1;
  let end = start + 2;
  while (end < lines.length && ROW.test(lines[end] ?? "")) end++;
  return end;
}

export function repairTables(markdown: string): string {
  const lines = markdown.split("\n");
  const out: string[] = [];
  let inFence = false;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? "";
    if (FENCE.test(line)) inFence = !inFence;
    const end = inFence ? -1 : tableEnd(lines, i);
    if (end === -1) {
      out.push(line);
      continue;
    }
    const table = lines.slice(i, end);
    let last = end;
    // Continued after a page break with the same header? Append its rows.
    for (;;) {
      let next = last;
      while (next < lines.length && BREAK.test(lines[next] ?? "")) next++;
      const nextEnd = tableEnd(lines, next);
      if (nextEnd === -1 || splitRow(lines[next] ?? "").join("|") !== splitRow(table[0] ?? "").join("|")) break;
      table.push(...lines.slice(next + 2, nextEnd));
      last = nextEnd;
    }
    out.push(...repairTable(table));
    i = last - 1;
  }
  return out.join("\n");
}
