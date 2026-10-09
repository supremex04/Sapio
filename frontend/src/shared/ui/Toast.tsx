import { Emoji } from "../illustrations/Emoji";
import type { EmojiName } from "../illustrations/emojiNames";
import { AnimatePresence, motion } from "motion/react";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";

export type ToastKind = "success" | "error" | "info" | "xp" | "level";

interface Toast {
  id: number;
  kind: ToastKind;
  message: ReactNode;
}

interface ToastApi {
  show: (kind: ToastKind, message: ReactNode, durationMs?: number) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const ICONS: Record<ToastKind, EmojiName> = { success: "check-mark-button", error: "cross-mark", info: "light-bulb", xp: "high-voltage", level: "crown" };

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => setToasts((all) => all.filter((t) => t.id !== id)), []);

  const show = useCallback<ToastApi["show"]>(
    (kind, message, durationMs = 3500) => {
      const id = nextId.current++;
      setToasts((all) => [...all.slice(-3), { id, kind, message }]);
      setTimeout(() => dismiss(id), durationMs);
    },
    [dismiss],
  );

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-region" aria-live="polite" aria-atomic="false">
        <AnimatePresence initial={false}>
          {toasts.map((t) => {
            return (
              <motion.div
                key={t.id}
                layout
                className={`toast toast-${t.kind}`}
                initial={{ opacity: 0, y: 16, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, x: 40, transition: { duration: 0.15 } }}
                transition={{ type: "spring", stiffness: 420, damping: 30 }}
                onClick={() => dismiss(t.id)}
                role={t.kind === "error" ? "alert" : "status"}
              >
                <span className="toast-icon">
                  <Emoji name={ICONS[t.kind]} size={26} />
                </span>
                <div>{t.message}</div>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");
  return ctx;
}
