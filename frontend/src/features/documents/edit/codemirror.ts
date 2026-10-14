import { markdownKeymap, markdownLanguage } from "@codemirror/lang-markdown";
import { HighlightStyle, LanguageSupport, syntaxHighlighting } from "@codemirror/language";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import { EditorSelection, Prec, StateField, type EditorState, type Extension } from "@codemirror/state";
import { Decoration, EditorView, WidgetType, keymap, placeholder, showTooltip, type Command, type DecorationSet, type Tooltip } from "@codemirror/view";
import { tags as t } from "@lezer/highlight";
import katex from "katex";
import type { FigureView } from "../../../shared/ui/Markdown";
import { insertBlock, insertLink, insertTable, mathAt, pasteUrlAsLink, toggleLines, toggleWrap, type LineKind, type Selection, type TextEdit } from "./markdownCommands";

/** Runs one of the pure Markdown transforms on the editor's main selection. */
export function run(transform: (doc: string, sel: Selection) => TextEdit): Command {
  return (view) => {
    const main = view.state.selection.main;
    const edit = transform(view.state.doc.toString(), { from: main.from, to: main.to });
    view.dispatch({
      changes: edit.changes,
      selection: EditorSelection.range(edit.selection.from, edit.selection.to),
      scrollIntoView: true,
      userEvent: "input.format",
    });
    view.focus();
    return true;
  };
}

export const commands = {
  bold: run((d, s) => toggleWrap(d, s, "**")),
  italic: run((d, s) => toggleWrap(d, s, "*")),
  strike: run((d, s) => toggleWrap(d, s, "~~")),
  code: run((d, s) => toggleWrap(d, s, "`")),
  math: run((d, s) => toggleWrap(d, s, "$")),
  displayMath: run((d, s) => insertBlock(d, s, "$$")),
  codeBlock: run((d, s) => insertBlock(d, s, "```")),
  link: run(insertLink),
  table: run(insertTable),
  lines: (kind: LineKind) => run((d, s) => toggleLines(d, s, kind)),
};

/** The shortcuts, shown in the editor's help and used to build the keymap (Mod = Ctrl, or Cmd on a Mac). */
export const SHORTCUTS: { keys: string; label: string; run?: Command }[] = [
  { keys: "Mod-s", label: "Save" },
  { keys: "Mod-Enter", label: "Save and close" },
  { keys: "Escape", label: "Close (asks before discarding changes)" },
  { keys: "Mod-b", label: "Bold", run: commands.bold },
  { keys: "Mod-i", label: "Italic", run: commands.italic },
  { keys: "Mod-Shift-x", label: "Strikethrough", run: commands.strike },
  { keys: "Mod-e", label: "Inline code", run: commands.code },
  { keys: "Mod-k", label: "Link (or paste a URL over selected text)", run: commands.link },
  { keys: "Mod-m", label: "Inline formula  $x^2$", run: commands.math },
  { keys: "Mod-Shift-m", label: "Display formula  $$ … $$", run: commands.displayMath },
  { keys: "Mod-Alt-c", label: "Code block", run: commands.codeBlock },
  { keys: "Mod-Alt-1", label: "Heading 1", run: commands.lines("h1") },
  { keys: "Mod-Alt-2", label: "Heading 2", run: commands.lines("h2") },
  { keys: "Mod-Alt-3", label: "Heading 3", run: commands.lines("h3") },
  { keys: "Mod-Shift-8", label: "Bulleted list", run: commands.lines("bullet") },
  { keys: "Mod-Shift-7", label: "Numbered list", run: commands.lines("numbered") },
  { keys: "Mod-Shift-9", label: "Checklist", run: commands.lines("task") },
  { keys: "Mod-Alt-p", label: "Preview / back to writing" },
  { keys: "Mod-z", label: "Undo (Mod-Shift-z or Mod-y to redo)" },
];

/** "Mod-Shift-m" as the learner's keyboard shows it. */
export function keyLabel(keys: string): string {
  const mac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform);
  return keys
    .split("-")
    .map((k) => (k === "Mod" ? (mac ? "⌘" : "Ctrl") : k === "Alt" ? (mac ? "⌥" : "Alt") : k === "Shift" ? "Shift" : k.length === 1 ? k.toUpperCase() : k))
    .join(mac ? "" : "+");
}

// ---------------------------------------------------------------------------------------------- look

/** Markdown shown as it will read: headings large, bold bold, links in the link colour, markup quieter. */
const highlight = HighlightStyle.define([
  { tag: t.heading1, fontSize: "1.45em", fontWeight: "800" },
  { tag: t.heading2, fontSize: "1.25em", fontWeight: "800" },
  { tag: [t.heading3, t.heading4, t.heading5, t.heading6], fontSize: "1.1em", fontWeight: "800" },
  { tag: t.strong, fontWeight: "800" },
  { tag: t.emphasis, fontStyle: "italic" },
  { tag: t.strikethrough, textDecoration: "line-through" },
  { tag: [t.link, t.url], color: "var(--color-primary)" },
  { tag: t.monospace, fontFamily: "var(--font-mono)", fontSize: "0.92em" },
  { tag: t.quote, fontStyle: "italic", color: "var(--text-2)" },
  { tag: [t.processingInstruction, t.meta, t.contentSeparator], color: "var(--text-muted)" },
  { tag: t.list, color: "var(--text-muted)" },
]);

const theme = EditorView.theme({
  "&": { fontSize: "inherit", backgroundColor: "transparent", color: "var(--text)" },
  "&.cm-focused": { outline: "none" },
  ".cm-scroller": { fontFamily: "inherit", lineHeight: "inherit" },
  ".cm-content": { fontFamily: "inherit", padding: "8px 0", caretColor: "var(--color-primary)" },
  ".cm-line": { padding: "0 2px" },
  ".cm-cursor": { borderLeftColor: "var(--color-primary)", borderLeftWidth: "2px" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection": {
    backgroundColor: "color-mix(in srgb, var(--color-primary) 22%, transparent) !important",
  },
  ".cm-placeholder": { color: "var(--text-muted)" },
  ".cm-tooltip": { border: "none", background: "transparent" },
});

// ---------------------------------------------------------------------------------------------- figures

const FIGURE_LINE = /^!\[[^\]\n]*]\(figure:([0-9a-fA-F-]{36})\)\s*$/;

class FigureWidget extends WidgetType {
  constructor(
    readonly id: string,
    readonly figure: FigureView | undefined,
  ) {
    super();
  }
  eq(other: FigureWidget) {
    return other.id === this.id;
  }
  toDOM() {
    const box = document.createElement("div");
    box.className = "sec-edit-figure";
    if (this.figure) {
      const img = document.createElement("img");
      img.src = this.figure.url;
      img.alt = "";
      box.append(img);
    }
    const label = document.createElement("span");
    label.textContent = this.figure?.caption || this.figure?.description?.slice(0, 80) || "Figure";
    const hint = document.createElement("small");
    hint.textContent = "Figure · move the line to move it, delete it to remove the figure";
    label.append(hint);
    box.append(label);
    return box;
  }
}

/** Figure lines show as the figure (a thumbnail and its caption) and move or delete as one unit. */
function figures(byId: Record<string, FigureView>): Extension {
  const build = (state: EditorState): DecorationSet => {
    const ranges = [];
    for (let i = 1; i <= state.doc.lines; i++) {
      const line = state.doc.line(i);
      const m = FIGURE_LINE.exec(line.text);
      if (m) ranges.push(Decoration.replace({ widget: new FigureWidget(m[1]!, byId[m[1]!]), block: true }).range(line.from, line.to));
    }
    return Decoration.set(ranges);
  };
  const field = StateField.define<DecorationSet>({
    create: build,
    update: (deco, tr) => (tr.docChanged ? build(tr.state) : deco),
    provide: (f) => [EditorView.decorations.from(f), EditorView.atomicRanges.of((view) => view.state.field(f))],
  });
  return field;
}

// ---------------------------------------------------------------------------------------------- formulas

/** While the caret is in a formula, it is shown rendered just below, so mistakes show as you type. */
const mathPreview = StateField.define<Tooltip | null>({
  create: (state) => mathTooltip(state),
  update: (tip, tr) => (tr.docChanged || tr.selection ? mathTooltip(tr.state) : tip),
  provide: (f) => showTooltip.from(f),
});

function mathTooltip(state: EditorState): Tooltip | null {
  const sel = state.selection.main;
  if (!sel.empty) return null;
  const math = mathAt(state.doc.toString(), sel.head);
  if (!math) return null;
  return {
    pos: math.from,
    above: false,
    arrow: false,
    create: () => {
      const dom = document.createElement("div");
      dom.className = "sec-edit-math";
      dom.innerHTML = katex.renderToString(math.tex, { displayMode: math.display, throwOnError: false, output: "html" });
      return { dom };
    },
  };
}

// ---------------------------------------------------------------------------------------------- setup

export interface EditorHooks {
  save: () => void;
  saveAndClose: () => void;
  close: () => void;
  togglePreview: () => void;
  change: (markdown: string) => void;
}

export function extensions(figuresById: Record<string, FigureView>, hooks: EditorHooks): Extension[] {
  const own: { key: string; run: Command }[] = [
    { key: "Mod-s", run: () => (hooks.save(), true) },
    { key: "Mod-Enter", run: () => (hooks.saveAndClose(), true) },
    { key: "Escape", run: () => (hooks.close(), true) },
    { key: "Mod-Alt-p", run: () => (hooks.togglePreview(), true) },
    ...SHORTCUTS.filter((s) => s.run).map((s) => ({ key: s.keys, run: s.run! })),
  ];
  return [
    history(),
    EditorView.lineWrapping,
    // GFM Markdown only: markdown() would also bundle the HTML, CSS and JavaScript grammars for embedded code.
    new LanguageSupport(markdownLanguage),
    syntaxHighlighting(highlight),
    theme,
    placeholder("Write in Markdown: **bold**, *italic*, [links](https://…), $formulas$ …"),
    figures(figuresById),
    mathPreview,
    Prec.highest(keymap.of(own)),
    keymap.of([...markdownKeymap, ...defaultKeymap, ...historyKeymap, indentWithTab]),
    EditorView.domEventHandlers({
      paste(event, view) {
        const text = event.clipboardData?.getData("text/plain") ?? "";
        const main = view.state.selection.main;
        const edit = pasteUrlAsLink(view.state.doc.toString(), { from: main.from, to: main.to }, text);
        if (!edit) return false;
        event.preventDefault();
        view.dispatch({ changes: edit.changes, selection: EditorSelection.cursor(edit.selection.from), userEvent: "input.paste" });
        return true;
      },
    }),
    EditorView.updateListener.of((u) => {
      if (u.docChanged) hooks.change(u.state.doc.toString());
    }),
  ];
}
