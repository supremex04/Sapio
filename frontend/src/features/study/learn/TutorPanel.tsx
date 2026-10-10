import { MessageCircleQuestion, Send, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Dia } from "../../../shared/illustrations/Dia";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button, IconButton } from "../../../shared/ui/Button";
import { Markdown } from "../../../shared/ui/Markdown";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import type { TutorAnswer, TutorFocus, TutorTurn } from "../types";

interface Message extends TutorTurn {
  grounded?: boolean;
  figures?: TutorAnswer["figures"];
}

const MAX_HISTORY = 10;
const STARTERS = ["Can you explain this more simply?", "Why does this matter?", "Give me another example"];
const QUESTION_STARTERS = ["Give me a hint", "What is this question really asking?"];
const ANSWERED_STARTERS = ["Why is that the answer?", "Where did my thinking go wrong?", "Give me a similar example"];

function starters(focus?: TutorFocus | null): string[] {
  if (focus?.questionId) return focus.answered ? ANSWERED_STARTERS : QUESTION_STARTERS;
  return STARTERS;
}

/**
 * "Ask the tutor": elaboration on demand, grounded in the document. Renders its own trigger (for the
 * lesson's focus header) and a solid slide-over panel. History stays on the client.
 */
export function TutorPanel({ topicId, topicTitle, focus }: { topicId: string; topicTitle: string; focus?: TutorFocus | null }) {
  const { refresh } = useProgress();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  function close() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  async function ask(question: string) {
    const q = question.trim();
    if (!q || sending) return;
    const history = messages.slice(-MAX_HISTORY).map(({ role, text }) => ({ role, text }));
    setMessages((m) => [...m, { role: "user", text: q }]);
    setDraft("");
    setSending(true);
    setError(null);
    try {
      const res = await studyApi.ask(topicId, q, history, focus);
      setMessages((m) => [...m, { role: "tutor", text: res.answer, grounded: res.grounded, figures: res.figures }]);
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
      <button ref={triggerRef} type="button" className="chip tutor-trigger" onClick={() => setOpen(true)} aria-haspopup="dialog" aria-label="Ask the tutor">
        <MessageCircleQuestion size={18} strokeWidth={2.5} aria-hidden />
        <span className="tutor-trigger-label">Ask</span>
      </button>
      {/* Portalled: the trigger sits in the sticky header, whose stacking context would trap the panel. */}
      {createPortal(
        <AnimatePresence>
          {open && (
            <>
              <motion.div className="tutor-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={close} />
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
                    <p className="xs muted break">From your document · {topicTitle}</p>
                    {focus && (
                      <p className="tutor-focus xs break" title="The tutor knows what's on your screen">
                        About: {focus.label}
                      </p>
                    )}
                  </div>
                  <IconButton label="Close" onClick={close}>
                    <X size={20} strokeWidth={2.5} />
                  </IconButton>
                </header>
                <div className="tutor-messages" ref={listRef}>
                  {messages.length === 0 && (
                    <div className="tutor-empty">
                      <div className="chat-row">
                        <Dia mood="happy" size={40} still />
                        <div className="chat-msg chat-tutor">Asking "why" and "how" helps ideas connect. What would you like to know?</div>
                      </div>
                      {starters(focus).map((s) => (
                        <button key={s} type="button" className="chip" onClick={() => void ask(s)}>
                          {s}
                        </button>
                      ))}
                    </div>
                  )}
                  {messages.map((m, i) => (
                    <motion.div key={i} className={m.role === "user" ? "chat-row chat-row-user" : "chat-row"} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>
                      {m.role === "tutor" && <Dia mood={m.grounded === false ? "thinking" : "happy"} size={32} still />}
                      <div className={m.role === "user" ? "chat-msg chat-user" : "chat-msg chat-tutor"}>
                        {m.role === "tutor" ? <Markdown figures={m.figures}>{m.text}</Markdown> : <p>{m.text}</p>}
                        {m.role === "tutor" && m.grounded === false && (
                          <Badge tone="warning" className="chat-badge">
                            Not covered in your document
                          </Badge>
                        )}
                      </div>
                    </motion.div>
                  ))}
                  {sending && (
                    <div className="chat-row">
                      <Dia mood="thinking" size={32} still />
                      <div className="chat-msg chat-tutor chat-typing" role="status" aria-label="Tutor is typing">
                        <span />
                        <span />
                        <span />
                      </div>
                    </div>
                  )}
                  {error && <Alert>{error}</Alert>}
                </div>
                <form className="tutor-input" onSubmit={onSubmit}>
                  <textarea
                    ref={inputRef}
                    className="textarea"
                    rows={2}
                    value={draft}
                    maxLength={1000}
                    placeholder="Ask anything about this topic…"
                    aria-label="Your question"
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && !e.shiftKey) {
                        e.preventDefault();
                        void ask(draft);
                      }
                    }}
                  />
                  <Button type="submit" variant="primary" disabled={!draft.trim()} loading={sending} aria-label="Send">
                    <Send size={18} aria-hidden />
                  </Button>
                </form>
              </motion.aside>
            </>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </>
  );
}
