import { Globe2 } from "lucide-react";
import { useEffect, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { cx } from "../../shared/ui/cx";
import { Dialog } from "../../shared/ui/Dialog";
import { sharingApi } from "./api";
import { categoryEmoji, type Category, type MyShare } from "./types";
import "./sharing.css";

const NOTE_LIMIT = 500;

/** Share a document to Explore under a category (or change / withdraw an existing share). */
export function ShareDialog({ open, documentId, title, share, onClose, onChanged }: {
  open: boolean;
  documentId: string;
  title: string;
  share: MyShare | null;
  onClose: () => void;
  onChanged: (share: MyShare | null) => void;
}) {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [category, setCategory] = useState<string | null>(share?.category.slug ?? null);
  const [note, setNote] = useState(share?.description ?? "");
  const [busy, setBusy] = useState<"save" | "remove" | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setCategory(share?.category.slug ?? null);
    setNote(share?.description ?? "");
    setError(null);
    if (categories === null) {
      sharingApi.categories().then(setCategories, (e) => setError(errorMessage(e)));
    }
  }, [open, share, categories]);

  async function save() {
    if (!category) return;
    setBusy("save");
    setError(null);
    try {
      onChanged(await sharingApi.share(documentId, category, note.trim()));
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  async function remove() {
    setBusy("remove");
    setError(null);
    try {
      await sharingApi.unshare(documentId);
      onChanged(null);
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={share ? "Shared to Explore" : "Share to Explore"}
      footer={
        <>
          {share && (
            <Button variant="ghost" className="share-stop" loading={busy === "remove"} disabled={busy !== null} onClick={remove}>
              Stop sharing
            </Button>
          )}
          <span style={{ marginLeft: "auto" }} />
          <Button variant="secondary" onClick={onClose} disabled={busy !== null}>
            Cancel
          </Button>
          <Button variant="primary" icon={<Globe2 size={18} />} loading={busy === "save"} disabled={!category || busy !== null} onClick={save}>
            {share ? "Save changes" : "Share"}
          </Button>
        </>
      }
    >
      <p className="share-lead">
        <strong className="break">{title}</strong> will appear in Explore. Others see its title, summary and topics, and can import their own copy
        with the lessons and notes. Your progress, answers and recall cards stay private.
      </p>

      <fieldset className="share-fieldset">
        <legend className="field-label">Category</legend>
        {categories === null ? (
          <div className="share-categories">
            {Array.from({ length: 6 }, (_, i) => (
              <span key={i} className="share-category is-loading" />
            ))}
          </div>
        ) : (
          <div className="share-categories" role="radiogroup" aria-label="Category">
            {categories.map((c) => (
              <button
                key={c.slug}
                type="button"
                role="radio"
                aria-checked={category === c.slug}
                className={cx("share-category", category === c.slug && "is-selected")}
                onClick={() => setCategory(c.slug)}
              >
                <Emoji name={categoryEmoji(c)} size={24} />
                <span>{c.name}</span>
              </button>
            ))}
          </div>
        )}
      </fieldset>

      <div className="field">
        <label htmlFor="share-note">
          Note for learners <span className="muted">(optional)</span>
        </label>
        <textarea
          id="share-note"
          className="textarea share-note"
          maxLength={NOTE_LIMIT}
          placeholder="Who is it for? E.g. “Covers the whole Unit 3 syllabus, good for exam revision.”"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
        <span className="xs muted share-count">
          {note.length}/{NOTE_LIMIT}
        </span>
      </div>

      {share && (
        <p className="small muted">
          <Emoji name="inbox-tray" size={18} /> Imported {share.importCount} {share.importCount === 1 ? "time" : "times"}. Copies stay with the
          learners who imported them, even if you stop sharing.
        </p>
      )}
      {error && <Alert>{error}</Alert>}
    </Dialog>
  );
}
