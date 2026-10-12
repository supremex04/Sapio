import { X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";

/**
 * Modal dialog on the native <dialog> element: focus is trapped and restored by the browser, Esc closes,
 * and the page behind is inert. Clicking the backdrop closes it too.
 */
export function Dialog({ open, onClose, title, children, footer }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby="dialog-title"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose(); // the backdrop
      }}
    >
      {open && (
        <div className="dialog-body">
          <header className="dialog-header">
            <h2 id="dialog-title" className="dialog-title">
              {title}
            </h2>
            <button type="button" className="dialog-close" aria-label="Close" onClick={onClose}>
              <X size={20} />
            </button>
          </header>
          <div className="dialog-content">{children}</div>
          {footer && <footer className="dialog-footer">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}
