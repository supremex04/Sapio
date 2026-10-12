import { useEffect, useState, type ComponentProps } from "react";
import ReactMarkdown, { defaultUrlTransform, type Components } from "react-markdown";
import rehypeKatex from "rehype-katex";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import "katex/dist/katex.min.css";
import { linkCitations } from "./markdown/citations";
import { inlineHtmlToMarkdown } from "./markdown/inlineHtml";

/** A figure from the learner's document, as returned by the API (signed, expiring URL). */
export interface FigureView {
  id: string;
  url: string;
  caption: string | null;
  description: string | null;
  kind: string | null;
  width: number;
  height: number;
}

const remarkPlugins = [remarkGfm, remarkMath];
const rehypePlugins = [rehypeKatex];

/** Our own schemes survive; everything else gets react-markdown's safe default handling. */
function urlTransform(url: string): string {
  return url.startsWith("figure:") || url.startsWith("cite:") ? url : defaultUrlTransform(url);
}

/**
 * Renders AI/OCR Markdown safely (no raw HTML), with tables and LaTeX math. AI text may cite sources as
 * "[§2.3]" (shown as small source chips) and embed document figures as `![caption](figure:<id>)`, resolved
 * through `figures` (unknown ids are dropped).
 */
export function Markdown({
  children,
  className,
  figures,
}: {
  children: string;
  className?: string;
  figures?: Record<string, FigureView>;
}) {
  const components: Components = {
    a: ({ href, children: label, ...rest }) => {
      if (href?.startsWith("cite:")) {
        const section = href.slice(5);
        return (
          <span className="md-cite" title={`From section ${section} of your document`}>
            {section}
          </span>
        );
      }
      return (
        <a href={href} target="_blank" rel="noreferrer" {...rest}>
          {label}
        </a>
      );
    },
    img: ({ src, alt }) => {
      if (typeof src === "string" && src.startsWith("figure:")) {
        const figure = figures?.[src.slice(7)];
        return figure ? <DocumentFigure figure={figure} alt={alt} /> : null;
      }
      return null; // external images are not loaded
    },
    // The wrapper scrolls sideways on narrow screens, so the table itself can fill the available width.
    table: ({ children: rows }) => (
      <div className="md-table">
        <table>{rows}</table>
      </div>
    ),
  };
  return (
    <div className={className ? `markdown ${className}` : "markdown"}>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        urlTransform={urlTransform}
        components={components}
      >
        {linkCitations(inlineHtmlToMarkdown(children))}
      </ReactMarkdown>
    </div>
  );
}

/** Inline figure (spans only: Markdown images sit inside paragraphs) that opens full size on click. */
function DocumentFigure({ figure, alt }: { figure: FigureView; alt?: string }) {
  const [open, setOpen] = useState(false);
  const caption = figure.caption || alt || "";
  const label = caption || figure.description || "Figure from your document";
  return (
    <span className="md-figure">
      <button type="button" className="md-figure-open" onClick={() => setOpen(true)} aria-label={`Enlarge: ${label}`}>
        <img src={figure.url} alt={figure.description || label} width={figure.width} height={figure.height} loading="lazy" />
      </button>
      {caption && <span className="md-figure-caption">{caption}</span>}
      {open && <Lightbox src={figure.url} alt={figure.description || label} caption={caption} onClose={() => setOpen(false)} />}
    </span>
  );
}

function Lightbox({ caption, onClose, ...img }: ComponentProps<"img"> & { caption: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <span className="md-lightbox" role="dialog" aria-modal="true" aria-label={caption || "Figure"} onClick={onClose}>
      <img {...img} />
      {caption && <span className="md-lightbox-caption">{caption}</span>}
    </span>
  );
}
