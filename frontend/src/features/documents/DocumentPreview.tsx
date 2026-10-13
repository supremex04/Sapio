import { Download, ExternalLink, List } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { errorMessage } from "../../shared/api/errors";
import { API_BASE } from "../../shared/api/http";
import { Dia } from "../../shared/illustrations/Dia";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { cx } from "../../shared/ui/cx";
import { Markdown, type FigureView } from "../../shared/ui/Markdown";
import { Segmented } from "../../shared/ui/Segmented";
import { readTextStep, saveTextStep, type TextStep } from "./textScale";
import { TextSizeControl } from "./TextSizeControl";
import { Skeleton, SkeletonText } from "../../shared/ui/Skeleton";
import { documentsApi } from "./api";
import { ExtractedText } from "./ExtractedText";
import { isMedia, spanLabel, type ContentSection, type DocumentContent, type DocumentDto, type NoteStyle, type SectionNotes } from "./types";
import "./preview.css";

type Mode = "reading" | "original" | "raw";

/**
 * Preview of the uploaded document. "Reading" is Sapio's structured version (sections in order, figures in place,
 * readable in any theme and on phones) with per-section AI rewrites; "Original" is the file exactly as uploaded;
 * "Raw text" is the extracted Markdown.
 */
export function DocumentPreview({ doc, ready }: { doc: DocumentDto; ready: boolean }) {
  const [mode, setMode] = useState<Mode>("reading");
  const [textStep, setTextStep] = useState<TextStep>(readTextStep);
  const changeTextStep = (step: TextStep) => {
    setTextStep(step);
    saveTextStep(step);
  };
  return (
    <div className="preview">
      <div className="preview-toolbar">
        <Segmented
          label="Preview mode"
          value={mode}
          onChange={setMode}
          options={[
            { value: "reading", label: "Reading view" },
            { value: "original", label: isMedia(doc) ? "Recording" : "Original" },
            { value: "raw", label: isMedia(doc) ? "Transcript" : "Raw text" },
          ]}
        />
      </div>
      {mode !== "original" && <TextSizeControl step={textStep} onChange={changeTextStep} />}
      <div className="preview-scale" style={{ "--text-scale": 1 + textStep / 100 } as CSSProperties}>
        {mode === "reading" && <ReadingView doc={doc} ready={ready} />}
        {mode === "original" && <OriginalView doc={doc} />}
        {mode === "raw" && <ExtractedText doc={doc} />}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------- reading view

function ReadingView({ doc, ready }: { doc: DocumentDto; ready: boolean }) {
  const [content, setContent] = useState<DocumentContent | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setContent(null);
    setError(null);
    documentsApi.content(doc.id).then(setContent, (e) => setError(errorMessage(e)));
  }, [doc.id, doc.status]);

  // Which unit each section belongs to (itself or its nearest ancestor), so a unit's rewrite can stand in for
  // all of its subsections.
  const unitOf = useMemo(() => coveringUnits(content?.sections ?? []), [content]);
  const [views, setViews] = useState<Record<string, View>>({});

  if (error) return <Alert>{error}</Alert>;
  if (!content) {
    return (
      <div className="stack">
        <Skeleton height={28} width="60%" />
        <SkeletonText lines={6} />
      </div>
    );
  }
  if (content.sections.length === 0) {
    return <p className="muted">Sapio is still organising this document. The preview appears once its chapters are ready.</p>;
  }

  return (
    <div className="preview-reading">
      <Contents sections={content.sections} />
      {!ready && <p className="small muted">Expand and Revision become available once your course is ready.</p>}
      {content.sections.map((section) => {
        const unitId = unitOf.get(section.id);
        // Inside a unit that currently shows a rewrite: the rewrite covers this subsection.
        if (unitId && unitId !== section.id && (views[unitId] ?? "source") !== "source") return null;
        return (
          <PreviewSection
            key={section.id}
            media={isMedia(doc)}
            documentId={doc.id}
            section={section}
            figures={content.figures}
            canRewrite={ready && section.unit !== null}
            view={views[section.id] ?? "source"}
            onView={(v) => setViews((all) => ({ ...all, [section.id]: v }))}
          />
        );
      })}
    </div>
  );
}

/** section id → id of the study unit covering it (itself if it is a unit). Sections arrive in document order. */
export function coveringUnits(sections: ContentSection[]): Map<string, string> {
  const result = new Map<string, string>();
  for (const s of sections) {
    const inherited = s.parentId ? result.get(s.parentId) : undefined;
    const unit = s.unit ? s.id : inherited;
    if (unit) result.set(s.id, unit);
  }
  return result;
}

/** "1.1–1.1.3 · pp. 1–5" or "this section · p. 2". */
function coverage(section: ContentSection, media: boolean): string {
  const u = section.unit;
  if (!u) return "";
  const range = u.sections <= 1 ? "this section" : `${u.fromLabel || section.title}–${u.toLabel}`;
  return `${range} · ${spanLabel(u.pageFrom, u.pageTo, media)}`;
}

function Contents({ sections }: { sections: ContentSection[] }) {
  return (
    <details className="preview-contents">
      <summary>
        <List size={18} strokeWidth={2.5} aria-hidden /> Contents · {sections.length} sections
      </summary>
      <ol>
        {sections.map((s) => (
          <li key={s.id} style={{ paddingLeft: `${(s.level - 1) * 1}rem` }}>
            <a
              href={`#section-${s.id}`}
              onClick={(e) => {
                e.preventDefault();
                document.getElementById(`section-${s.id}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
              }}
            >
              {s.label && <span className="preview-label">{s.label}</span>} {s.title}
            </a>
          </li>
        ))}
      </ol>
    </details>
  );
}

type View = "source" | NoteStyle;

const REWRITE_COPY: Record<NoteStyle, { working: string; badge: string }> = {
  EXPAND: { working: "Dia is explaining this section step by step…", badge: "Expanded by AI" },
  REVISION: { working: "Dia is boiling this section down to what matters…", badge: "Revision notes by AI" },
};

/** One section: its text and figures, with Original / Expand / Revision views. */
function PreviewSection({
  media,
  documentId,
  section,
  figures,
  canRewrite,
  view,
  onView,
}: {
  /** Audio / video: spans are times (page = minute). */
  media: boolean;
  documentId: string;
  section: ContentSection;
  figures: Record<string, FigureView>;
  canRewrite: boolean;
  view: View;
  onView: (view: View) => void;
}) {
  const [notes, setNotes] = useState<Partial<Record<NoteStyle, SectionNotes>>>({});
  const [loading, setLoading] = useState<NoteStyle | null>(null);
  const [error, setError] = useState<string | null>(null);

  const markdown = useMemo(() => sectionMarkdown(section, figures), [section, figures]);
  const Heading = section.level <= 1 ? "h2" : section.level === 2 ? "h3" : "h4";

  async function show(next: View) {
    onView(next);
    setError(null);
    if (next === "source" || notes[next]) return;
    setLoading(next);
    try {
      const result = await documentsApi.notes(documentId, section.id, next);
      setNotes((n) => ({ ...n, [next]: result }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading((l) => (l === next ? null : l));
    }
  }

  const current = view === "source" ? null : notes[view];
  return (
    <section id={`section-${section.id}`} className={cx("preview-section", `preview-level-${Math.min(section.level, 4)}`)}>
      <header className="preview-section-head">
        <Heading>
          {section.label && <span className="preview-label">{section.label}</span>} {section.title}
        </Heading>
        {canRewrite ? (
          <div className="preview-actions">
            <Segmented
              label={`View of ${section.title}`}
              value={view}
              onChange={(v) => void show(v)}
              options={[
                { value: "source", label: "Original" },
                { value: "EXPAND", label: <><Emoji name="sparkles" size={16} /> Expand</> },
                { value: "REVISION", label: <><Emoji name="high-voltage" size={16} /> Revision</> },
              ]}
            />
            <span className="preview-coverage xs muted">Covers {coverage(section, media)}</span>
          </div>
        ) : (
          <span className="xs muted">{spanLabel(section.pageFrom, section.pageTo, media)}</span>
        )}
      </header>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={view} initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.18 }}>
          {view === "source" ? (
            markdown && <Markdown figures={figures}>{markdown}</Markdown>
          ) : loading === view ? (
            <div className="preview-working" role="status">
              <Dia mood="thinking" size={48} />
              <span>{REWRITE_COPY[view].working}</span>
            </div>
          ) : error ? (
            <Alert
              action={
                <Button size="sm" variant="soft" onClick={() => void show(view)}>
                  Try again
                </Button>
              }
            >
              {error}
            </Alert>
          ) : current ? (
            <div className={cx("preview-notes", `preview-notes-${view.toLowerCase()}`)}>
              <span className="preview-notes-badge xs">{REWRITE_COPY[view].badge} · covers {coverage(section, media)}</span>
              <Markdown figures={current.figures}>{current.markdown}</Markdown>
            </div>
          ) : null}
        </motion.div>
      </AnimatePresence>
    </section>
  );
}

/** A section's blocks as Markdown, figures embedded in place (resolved by the Markdown renderer). */
export function sectionMarkdown(section: ContentSection, figures: Record<string, FigureView>): string {
  return section.blocks
    .map((b) => {
      if (b.type === "text") return b.markdown ?? "";
      const figure = b.figureId ? figures[b.figureId] : undefined;
      if (!figure) return "";
      const caption = (figure.caption || firstSentence(figure.description) || "Figure").replace(/[[\]\n]/g, " ");
      return `![${caption}](figure:${figure.id})`;
    })
    .filter(Boolean)
    .join("\n\n");
}

/** "A diagram of X. It shows…" → "A diagram of X." (a caption, not a paragraph). */
function firstSentence(text: string | null): string {
  if (!text) return "";
  const match = text.match(/^.{20,200}?[.!?](\s|$)/s);
  return (match ? match[0] : text.slice(0, 160)).trim();
}

// ---------------------------------------------------------------------------------------------------- original

function OriginalView({ doc }: { doc: DocumentDto }) {
  if (isMedia(doc)) return <RecordingView doc={doc} />;
  if (doc.contentType === WORD) return <WordOriginal doc={doc} />;
  return <FileOriginal doc={doc} />;
}

const WORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/** PDFs in the browser's viewer, images as images. */
function FileOriginal({ doc }: { doc: DocumentDto }) {
  const url = useOriginalUrl(doc.id);
  if (url.error) return <Alert>{url.error}</Alert>;
  if (!url.value) return <Skeleton height={480} />;
  const isPdf = doc.contentType === "application/pdf";
  return (
    <div className="preview-original">
      <div className="row-between">
        <p className="small muted">Exactly as you uploaded it.</p>
        <Button size="sm" variant="soft" iconRight={<ExternalLink size={16} />} onClick={() => window.open(url.value!, "_blank", "noopener")}>
          Open in new tab
        </Button>
      </div>
      {isPdf ? (
        <iframe className="preview-frame" src={url.value} title={`Original: ${doc.filename}`} />
      ) : (
        <img className="preview-image" src={url.value} alt={`Original: ${doc.filename}`} />
      )}
    </div>
  );
}

/** Browsers cannot display a .docx: offer the file itself; Reading view shows its content. */
function WordOriginal({ doc }: { doc: DocumentDto }) {
  const url = useOriginalUrl(doc.id);
  if (url.error) return <Alert>{url.error}</Alert>;
  return (
    <div className="preview-original preview-word">
      <p className="small muted">Browsers can't show Word files directly. Reading view has the whole document; here is the file itself.</p>
      <a className="btn btn-soft btn-sm" href={url.value ?? undefined} download={doc.filename} aria-disabled={!url.value}>
        <Download size={16} aria-hidden /> Download {doc.filename}
      </a>
    </div>
  );
}

/** Audio / video: streamed from a signed URL, so the player can seek without downloading the whole recording. */
function RecordingView({ doc }: { doc: DocumentDto }) {
  const [url, setUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    documentsApi.playback(doc.id).then((p) => setUrl(p.url), (e) => setError(errorMessage(e)));
  }, [doc.id]);
  if (error) return <Alert>{error}</Alert>;
  if (!url) return <Skeleton height={doc.kind === "VIDEO" ? 360 : 64} />;
  const src = `${API_BASE}${url.replace(/^\/api/, "")}`;
  return (
    <div className="preview-original">
      <p className="small muted">The recording as you uploaded it. Reading view has the notes written from it; Transcript has every word.</p>
      {doc.kind === "VIDEO" ? (
        <video className="preview-media" src={src} controls preload="metadata" />
      ) : (
        <audio className="preview-media preview-audio" src={src} controls preload="metadata" />
      )}
    </div>
  );
}

/** The uploaded file as an object URL (fetched with the access token), revoked on unmount. */
function useOriginalUrl(documentId: string): { value: string | null; error: string | null } {
  const [value, setValue] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    documentsApi.original(documentId).then(
      (blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setValue(objectUrl);
      },
      (e) => !cancelled && setError(errorMessage(e)),
    );
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [documentId]);
  return { value, error };
}
