import { Copy, PenLine } from "lucide-react";
import { useEffect, useRef } from "react";
import { uiZoom } from "../../../app/sidebarState";

/**
 * The text before a point in a rendered section (and the section's whole text length), from the browser's caret
 * position at that point. Used to open the editor where the learner right-clicked.
 */
export function renderedTextBefore(container: HTMLElement, x: number, y: number): { before: string; total: number } | null {
  const doc = document as Document & {
    caretPositionFromPoint?: (x: number, y: number) => { offsetNode: Node; offset: number } | null;
    caretRangeFromPoint?: (x: number, y: number) => Range | null;
  };
  let node: Node | null = null;
  let offset = 0;
  if (doc.caretPositionFromPoint) {
    const p = doc.caretPositionFromPoint(x, y);
    if (p) ({ offsetNode: node, offset } = p);
  } else if (doc.caretRangeFromPoint) {
    const r = doc.caretRangeFromPoint(x, y);
    if (r) {
      node = r.startContainer;
      offset = r.startOffset;
    }
  }
  if (!node || !container.contains(node)) return null;
  const range = document.createRange();
  range.selectNodeContents(container);
  try {
    range.setEnd(node, offset);
  } catch {
    return null;
  }
  return { before: range.toString(), total: container.textContent?.length ?? 0 };
}

/**
 * Right-click menu on a section of the reading view. Shift + right-click keeps the browser's own menu.
 */
export function SectionMenu({ x, y, selection, onEdit, onClose }: { x: number; y: number; selection: string; onEdit: () => void; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ref.current?.querySelector("button")?.focus();
    const away = (e: Event) => {
      if (!ref.current?.contains(e.target as Node)) onClose();
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("mousedown", away);
    document.addEventListener("keydown", key);
    window.addEventListener("scroll", onClose, true);
    window.addEventListener("resize", onClose);
    return () => {
      document.removeEventListener("mousedown", away);
      document.removeEventListener("keydown", key);
      window.removeEventListener("scroll", onClose, true);
      window.removeEventListener("resize", onClose);
    };
  }, [onClose]);

  // Mouse coordinates are in screen pixels; the page is laid out at the interface zoom. Keep the menu on screen.
  const zoom = uiZoom();
  const left = Math.min(x / zoom, window.innerWidth / zoom - 220);
  const top = Math.min(y / zoom, window.innerHeight / zoom - (selection ? 130 : 90));

  return (
    <div ref={ref} className="sec-menu" role="menu" style={{ left, top }}>
      <button
        type="button"
        role="menuitem"
        onClick={() => {
          onEdit();
          onClose();
        }}
      >
        <PenLine size={16} aria-hidden /> Edit section
      </button>
      {selection && (
        <button
          type="button"
          role="menuitem"
          onClick={() => {
            void navigator.clipboard?.writeText(selection);
            onClose();
          }}
        >
          <Copy size={16} aria-hidden /> Copy
        </button>
      )}
      <p>Shift + right-click for the browser menu</p>
    </div>
  );
}
