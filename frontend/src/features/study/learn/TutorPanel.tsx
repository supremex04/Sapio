import { MessageCircleQuestion, Send, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/ui/Badge";
import { Button, IconButton } from "../../../shared/ui/Button";
import { Markdown } from "../../../shared/ui/Markdown";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import type { TutorTurn } from "../types";

interface Message extends TutorTurn {
  grounded?: boolean;
}

const MAX_HISTORY = 10;
const STARTERS = ["Can you explain this more simply?", "Why does this matter?", "Give me another example"];

/** "Ask the tutor": elaboration on demand, grounded in the document. History stays on the client. */
export function TutorPanel({ topicId, topicTitle }: { topicId: string; topicTitle: string }) {
  const { refresh } = useProgress();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  async function ask(question: string) {
    const q = question.trim();
    if (!q || sending) return;
    const history = messages.slice(-MAX_HISTORY).map(({ role, text }) => ({ role, text }));
    setMessages((m) => [...m, { role: "user", text: q }]);
    setDraft("");
    setSending(true);
    setError(null);
    try {
      const res = await studyApi.ask(topicId, q, history);
      setMessages((m) => [...m, { role: "tutor", text: res.answer, grounded: res.grounded }]);
      void refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void ask(draft);
  }

  return (
    <>
      <motion.button className="tutor-fab" onClick={() => setOpen(true)} whileHover={{ scale: 1.04 }} whileTap={{ scale: 0.97 }} aria-label="Ask the tutor">
        <MessageCircleQuestion size={20} aria-hidden />
        <span>Ask the tutor</span>
      </motion.button>
      <AnimatePresence>
        {open && (
          <>
            <motion.div className="tutor-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setOpen(false)} />
            <motion.aside
              className="tutor-panel"
              role="dialog"
              aria-modal="true"
              aria-label="Ask the tutor"
              initial={{ x: "100%" }}
              animate={{ x: 0 }}
              exit={{ x: "100%" }}
              transition={{ type: "spring", stiffness: 380, damping: 38 }}
            >
              <header className="tutor-head">
                <div>
                  <strong>Ask the tutor</strong>
                  <p className="xs muted">Answers come from your document · {topicTitle}</p>
                </div>
                <IconButton label="Close" onClick={() => setOpen(false)}>
                  <X size={18} />
                </IconButton>
              </header>
              <div className="tutor-messages" ref={listRef}>
                {messages.length === 0 && (
                  <div className="tutor-empty">
                    <p className="muted small">Asking "why" and "how" questions helps ideas connect. Try:</p>
                    {STARTERS.map((s) => (
                      <button key={s} className="chip" onClick={() => void ask(s)}>
                        {s}
                      </button>
                    ))}
                  </div>
                )}
                {messages.map((m, i) => (
                  <motion.div key={i} className={`bubble bubble-${m.role}`} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
                    {m.role === "tutor" ? <Markdown>{m.text}</Markdown> : <p>{m.text}</p>}
                    {m.role === "tutor" && m.grounded === false && (
                      <Badge tone="warning" className="bubble-badge">
                        Not covered in your document
                      </Badge>
                    )}
                  </motion.div>
                ))}
                {sending && (
                  <div className="bubble bubble-tutor typing" aria-label="Tutor is typing">
                    <span />
                    <span />
                    <span />
                  </div>
                )}
                {error && <p className="small" style={{ color: "var(--color-warning-text)" }}>{error}</p>}
              </div>
              <form className="tutor-input" onSubmit={onSubmit}>
                <textarea
                  ref={inputRef}
                  className="textarea"
                  rows={2}
                  value={draft}
                  maxLength={1000}
                  placeholder="Ask anything about this topic…"
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      void ask(draft);
                    }
                  }}
                />
                <Button type="submit" variant="primary" disabled={!draft.trim()} loading={sending} aria-label="Send">
                  <Send size={16} aria-hidden />
                </Button>
              </form>
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
