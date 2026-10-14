import { EditorSelection, EditorState } from "@codemirror/state";
import { EditorView, type Command } from "@codemirror/view";
import {
  Bold,
  Code,
  Eye,
  Heading1,
  Heading2,
  Heading3,
  Italic,
  Keyboard,
  Link,
  List,
  ListChecks,
  ListOrdered,
  PenLine,
  Sigma,
  SquareCode,
  SquareFunction,
  Strikethrough,
  Table,
  TextQuote,
} from "lucide-react";
import { useEffect, useRef, useState, type ReactElement } from "react";
import { ApiError } from "../../../shared/api/errors";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { Markdown, type FigureView } from "../../../shared/ui/Markdown";
import { SHORTCUTS, commands, extensions, keyLabel } from "./codemirror";
import { clearDraft, readDraft, writeDraft, type Draft } from "./drafts";
import "./editor.css";

export interface SectionEditorProps {
  sectionId: string;
  revision: number;
  /** The section's title and text as saved (figures as `![caption](figure:<id>)`). */
  title: string;
  markdown: string;
  /** Where to put the cursor (offset in the Markdown); null: the end. */
  cursor: number | null;
  figures: Record<string, FigureView>;
  headingLevel: number;
  /** The document's own numbering ("Chapter 1"), shown before the title. */
  label: string;
  /** Saves; resolves once the reading view shows the saved text, rejects with the API error. */
  onSave: (title: string, markdown: string) => Promise<void>;
  onClose: () => void;
  /** Reloads the reading view (after a conflict). */
  onReload: () => void;
}

type Status =
  | { kind: "idle" }
  | { kind: "saving" }
  | { kind: "saved"; at: Date }
  | { kind: "error"; message: string; conflict: boolean };

/**
 * In-place Markdown editor for one section of the reading view: what you edit is exactly what the reading view
 * renders. Low friction by design: it opens where you clicked, Ctrl+S saves without leaving, Escape closes, the
 * usual formatting shortcuts work, formulas preview as you type, and unsaved text survives a closed tab.
 */
export default function SectionEditor(props: SectionEditorProps) {
  const { sectionId, revision, figures } = props;
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const [title, setTitle] = useState(props.title);
  const [text, setText] = useState(props.markdown);
  const [saved, setSaved] = useState({
    title: props.title,
    markdown: props.markdown,
  });
  const [status, setStatus] = useState<Status>({ kind: "idle" });
  const [preview, setPreview] = useState(false);
  const [help, setHelp] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [draft, setDraft] = useState<Draft | null>(() => {
    const d = readDraft(sectionId);
    return d && (d.markdown !== props.markdown || d.title !== props.title)
      ? d
      : null;
  });

  const dirty = title !== saved.title || text !== saved.markdown;

  // The editor is created once; its key handlers reach the latest state through this ref.
  const latest = useRef({
    title,
    text,
    dirty,
    saved,
    revision,
    preview,
    confirmDiscard,
  });
  latest.current = {
    title,
    text,
    dirty,
    saved,
    revision,
    preview,
    confirmDiscard,
  };

  async function save(close: boolean) {
    const { title: t, text: md, dirty: changed } = latest.current;
    if (!t.trim()) {
      setStatus({
        kind: "error",
        message: "A section needs a title.",
        conflict: false,
      });
      return;
    }
    if (!changed) {
      if (close) props.onClose();
      return;
    }
    setStatus({ kind: "saving" });
    try {
      await props.onSave(t.trim(), md);
      setSaved({ title: t.trim(), markdown: md });
      clearDraft(sectionId);
      setStatus({ kind: "saved", at: new Date() });
      if (close) props.onClose();
    } catch (e) {
      const conflict = e instanceof ApiError && e.code === "EDIT_CONFLICT";
      setStatus({
        kind: "error",
        message: e instanceof Error ? e.message : "Could not save",
        conflict,
      });
    }
  }

  function close() {
    if (latest.current.dirty && !latest.current.confirmDiscard) {
      setConfirmDiscard(true); // a second Escape (or Discard) confirms
      return;
    }
    clearDraft(sectionId);
    props.onClose();
  }

  const hooks = useRef({ save, close, setPreview });
  hooks.current = { save, close, setPreview };

  useEffect(() => {
    if (!host.current) return;
    const doc = props.markdown;
    const at = Math.min(props.cursor ?? doc.length, doc.length);
    const editor = new EditorView({
      parent: host.current,
      state: EditorState.create({
        doc,
        selection: EditorSelection.cursor(at),
        extensions: extensions(figures, {
          save: () => void hooks.current.save(false),
          saveAndClose: () => void hooks.current.save(true),
          close: () => hooks.current.close(),
          togglePreview: () => hooks.current.setPreview((p) => !p),
          change: (md) => {
            setText(md);
            setConfirmDiscard(false);
          },
        }),
      }),
    });
    view.current = editor;
    editor.focus();
    editor.dispatch({
      effects: EditorView.scrollIntoView(at, { y: "center" }),
    });
    return () => {
      editor.destroy();
      view.current = null;
    };
    // Created once per opening; later prop changes (a save's new revision) must not reset the text.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keep a draft in this browser while there are unsaved changes.
  useEffect(() => {
    if (!dirty) return;
    const timer = window.setTimeout(
      () =>
        writeDraft(sectionId, {
          title,
          markdown: text,
          revision,
          savedAt: new Date().toISOString(),
        }),
      400,
    );
    return () => window.clearTimeout(timer);
  }, [dirty, title, text, revision, sectionId]);

  // Leaving the page with unsaved changes: the browser asks first.
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  // Back from preview: the editor gets the focus again.
  useEffect(() => {
    if (!preview) view.current?.focus();
  }, [preview]);

  function restore(d: Draft) {
    setTitle(d.title);
    view.current?.dispatch({
      changes: {
        from: 0,
        to: view.current.state.doc.length,
        insert: d.markdown,
      },
    });
    setDraft(null);
  }

  const exec = (command: Command) => () => {
    if (view.current) command(view.current);
  };
  const Heading =
    props.headingLevel <= 1 ? "h2" : props.headingLevel === 2 ? "h3" : "h4";

  return (
    <div
      className="sec-edit"
      onKeyDown={(e) => {
        // The title field shares the editor's save / close keys (the editor handles its own).
        if (e.defaultPrevented) return;
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
          e.preventDefault();
          void save(false);
        } else if (e.key === "Escape" && e.target instanceof HTMLInputElement) {
          close();
        }
      }}
    >
      <header className="preview-section-head sec-edit-head">
        <Heading className="sec-edit-title">
          {props.label && <span className="preview-label">{props.label}</span>}
          <input
            value={title}
            onChange={(e) => {
              setTitle(e.target.value);
              setConfirmDiscard(false);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === "ArrowDown") {
                e.preventDefault();
                view.current?.focus();
              }
            }}
            aria-label="Section title"
            maxLength={400}
          />
        </Heading>
      </header>

      <div className="sec-edit-bar" role="toolbar" aria-label="Formatting">
        <Tool
          label="Bold"
          keys="Mod-b"
          icon={<Bold />}
          onClick={exec(commands.bold)}
          disabled={preview}
        />
        <Tool
          label="Italic"
          keys="Mod-i"
          icon={<Italic />}
          onClick={exec(commands.italic)}
          disabled={preview}
        />
        <Tool
          label="Strikethrough"
          keys="Mod-Shift-x"
          icon={<Strikethrough />}
          onClick={exec(commands.strike)}
          disabled={preview}
        />
        <Tool
          label="Inline code"
          keys="Mod-e"
          icon={<Code />}
          onClick={exec(commands.code)}
          disabled={preview}
        />
        <Tool
          label="Link"
          keys="Mod-k"
          icon={<Link />}
          onClick={exec(commands.link)}
          disabled={preview}
        />
        <span className="sec-edit-sep" />
        <Tool
          label="Heading 1"
          keys="Mod-Alt-1"
          icon={<Heading1 />}
          onClick={exec(commands.lines("h1"))}
          disabled={preview}
        />
        <Tool
          label="Heading 2"
          keys="Mod-Alt-2"
          icon={<Heading2 />}
          onClick={exec(commands.lines("h2"))}
          disabled={preview}
        />
        <Tool
          label="Heading 3"
          keys="Mod-Alt-3"
          icon={<Heading3 />}
          onClick={exec(commands.lines("h3"))}
          disabled={preview}
        />
        <span className="sec-edit-sep" />
        <Tool
          label="Bulleted list"
          keys="Mod-Shift-8"
          icon={<List />}
          onClick={exec(commands.lines("bullet"))}
          disabled={preview}
        />
        <Tool
          label="Numbered list"
          keys="Mod-Shift-7"
          icon={<ListOrdered />}
          onClick={exec(commands.lines("numbered"))}
          disabled={preview}
        />
        <Tool
          label="Checklist"
          keys="Mod-Shift-9"
          icon={<ListChecks />}
          onClick={exec(commands.lines("task"))}
          disabled={preview}
        />
        <Tool
          label="Quote"
          icon={<TextQuote />}
          onClick={exec(commands.lines("quote"))}
          disabled={preview}
        />
        <span className="sec-edit-sep" />
        <Tool
          label="Inline formula"
          keys="Mod-m"
          icon={<Sigma />}
          onClick={exec(commands.math)}
          disabled={preview}
        />
        <Tool
          label="Display formula"
          keys="Mod-Shift-m"
          icon={<SquareFunction />}
          onClick={exec(commands.displayMath)}
          disabled={preview}
        />
        <Tool
          label="Code block"
          keys="Mod-Alt-c"
          icon={<SquareCode />}
          onClick={exec(commands.codeBlock)}
          disabled={preview}
        />
        <Tool
          label="Table"
          icon={<Table />}
          onClick={exec(commands.table)}
          disabled={preview}
        />
        <span className="sec-edit-grow" />
        <Tool
          label={preview ? "Back to writing" : "Preview"}
          keys="Mod-Alt-p"
          icon={preview ? <PenLine /> : <Eye />}
          pressed={preview}
          onClick={() => setPreview((p) => !p)}
        />
        <Tool
          label="Keyboard shortcuts"
          icon={<Keyboard />}
          pressed={help}
          onClick={() => setHelp((h) => !h)}
        />
      </div>

      {help && (
        <dl className="sec-edit-help">
          {SHORTCUTS.map((s) => (
            <div key={s.keys}>
              <dt>
                <kbd>{keyLabel(s.keys)}</kbd>
              </dt>
              <dd>{s.label}</dd>
            </div>
          ))}
          <div>
            <dt>
              <kbd>Enter</kbd>
            </dt>
            <dd>In a list: continues it (twice ends it)</dd>
          </div>
        </dl>
      )}

      {draft && (
        <Alert
          tone="info"
          action={
            <span className="row" style={{ gap: "var(--space-2)" }}>
              <Button size="sm" variant="soft" onClick={() => restore(draft)}>
                Restore
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  clearDraft(sectionId);
                  setDraft(null);
                }}
              >
                Discard
              </Button>
            </span>
          }
        >
          You have unsaved changes to this section from{" "}
          {new Date(draft.savedAt).toLocaleString()}
          {draft.revision !== revision &&
            " (the section was saved again since)"}
          .
        </Alert>
      )}

      <div
        className={cx(
          "markdown sec-edit-surface",
          preview && "sec-edit-surface--hidden",
        )}
        ref={host}
      />
      {preview && (
        <div className="sec-edit-preview">
          <Markdown figures={figures}>{text}</Markdown>
        </div>
      )}

      {status.kind === "error" && (
        <Alert
          action={
            status.conflict ? (
              <Button size="sm" variant="soft" onClick={props.onReload}>
                Reload latest
              </Button>
            ) : undefined
          }
        >
          {status.conflict
            ? "This section was saved somewhere else meanwhile. Your text is kept as a draft in this browser; reload to see the latest and restore it."
            : status.message}
        </Alert>
      )}

      <div className="sec-edit-foot">
        <span className="sec-edit-status xs muted" role="status">
          {confirmDiscard
            ? "Unsaved changes. Press Esc again to discard them."
            : status.kind === "saving"
              ? "Saving…"
              : dirty
                ? "Unsaved changes"
                : status.kind === "saved"
                  ? `Saved at ${status.at.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`
                  : `${keyLabel("Mod-s")} saves · Esc closes`}
        </span>
        <span className="row" style={{ gap: "var(--space-2)" }}>
          <Button
            size="sm"
            variant={confirmDiscard ? "danger" : "ghost"}
            onClick={close}
          >
            {confirmDiscard ? "Discard changes" : dirty ? "Cancel" : "Close"}
          </Button>
          <Button
            size="sm"
            variant="primary"
            loading={status.kind === "saving"}
            onClick={() => void save(true)}
          >
            {dirty ? "Save" : "Done"}
          </Button>
        </span>
      </div>
    </div>
  );
}

function Tool({
  label,
  keys,
  icon,
  onClick,
  disabled,
  pressed,
}: {
  label: string;
  keys?: string;
  icon: ReactElement;
  onClick: () => void;
  disabled?: boolean;
  pressed?: boolean;
}) {
  const title = keys ? `${label} (${keyLabel(keys)})` : label;
  return (
    <button
      type="button"
      className="sec-edit-tool"
      title={title}
      aria-label={title}
      aria-pressed={pressed}
      disabled={disabled}
      // Keep the editor's selection: the button must not take the focus on click.
      onMouseDown={(e) => e.preventDefault()}
      onClick={onClick}
    >
      {icon}
    </button>
  );
}
