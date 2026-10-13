"""Repairs Markdown tables produced by PDF extraction (mirrored by frontend/src/shared/ui/markdown/tables.ts).

- A cell's soft line breaks (``<br>``) become spaces: a table row must stay on one line.
- When a cell wrapped, extraction may emit the wrapped tail as its own row (``|parameter|||messy|``). A row with an
  empty cell that follows a complete row (no empty cells) is such a tail: its cells are appended to the row above.
  Header rows and tables that genuinely have empty cells are left alone.
- A table continued after a page break repeats its header: consecutive tables with the same header, separated only
  by blank lines or a page-break rule (``---``), become one table.
"""

import re

_ROW = re.compile(r"^\s*\|.*\|\s*$")
_DELIMITER = re.compile(r"^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$")
_BR = re.compile(r"<br\s*/?>", re.IGNORECASE)
_FENCE = re.compile(r"^\s*```")


def split_row(line: str) -> list[str]:
    """Splits a row into cells on unescaped pipes outside inline code."""
    body = line.strip()
    body = body.removeprefix("|")
    body = body.removesuffix("|")
    cells: list[str] = []
    current = ""
    in_code = False
    i = 0
    while i < len(body):
        ch = body[i]
        if ch == "`":
            in_code = not in_code
        if ch == "\\" and body[i + 1 : i + 2] == "|":
            current += "\\|"
            i += 2
            continue
        if ch == "|" and not in_code:
            cells.append(current.strip())
            current = ""
        else:
            current += ch
        i += 1
    cells.append(current.strip())
    return cells


def _join(cells: list[str]) -> str:
    return "|" + "|".join(cells) + "|"


def _repair(lines: list[str]) -> list[str]:
    header, delimiter, *body = lines
    rows: list[list[str]] = []
    for line in body:
        cells = split_row(_BR.sub(" ", line))
        if rows and any(c == "" for c in cells) and all(c != "" for c in rows[-1]):
            previous = rows[-1]
            for i, c in enumerate(cells):
                if c and i < len(previous):
                    previous[i] = f"{previous[i]} {c}"
            continue
        rows.append(cells)
    return [_join(split_row(_BR.sub(" ", header))), delimiter.strip(), *(_join(r) for r in rows)]


_BREAK = re.compile(r"^\s*(-{3,}|\*{3,}|_{3,})?\s*$")  # blank line or a horizontal rule (page break)


def _table_end(lines: list[str], start: int) -> int:
    """End (exclusive) of the table whose header is at ``start``, or -1 if no table starts there."""
    if start + 1 >= len(lines) or not _ROW.match(lines[start]) or not _DELIMITER.match(lines[start + 1]):
        return -1
    end = start + 2
    while end < len(lines) and _ROW.match(lines[end]):
        end += 1
    return end


def repair_tables(markdown: str) -> str:
    lines = markdown.split("\n")
    out: list[str] = []
    in_fence = False
    i = 0
    while i < len(lines):
        line = lines[i]
        if _FENCE.match(line):
            in_fence = not in_fence
        end = -1 if in_fence else _table_end(lines, i)
        if end == -1:
            out.append(line)
            i += 1
            continue
        table = lines[i:end]
        last = end
        while True:  # continued after a page break with the same header? append its rows
            nxt = last
            while nxt < len(lines) and _BREAK.match(lines[nxt]):
                nxt += 1
            next_end = _table_end(lines, nxt)
            if next_end == -1 or split_row(lines[nxt]) != split_row(table[0]):
                break
            table.extend(lines[nxt + 2 : next_end])
            last = next_end
        out.extend(_repair(table))
        i = last
    return "\n".join(out)
