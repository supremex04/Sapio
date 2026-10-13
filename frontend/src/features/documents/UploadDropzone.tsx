import { useRef, useState, type DragEvent, type RefObject } from "react";
import { Sage } from "../../shared/illustrations/Sage";
import { formatBytes } from "../../shared/lib/format";
import { cx } from "../../shared/ui/cx";
import { SpeechBubble } from "../../shared/ui/SpeechBubble";
import { ACCEPTED_FILE_TYPES, MAX_FILE_BYTES, MAX_MEDIA_BYTES } from "./types";

interface Props {
  onFiles: (files: File[]) => void;
  disabled?: boolean;
  busy?: boolean;
  /** Lets another control (e.g. the page's Upload button) open the same file picker. */
  inputRef?: RefObject<HTMLInputElement | null>;
}

/** Chunky dashed drop target with Sapi asking for material. */
export function UploadDropzone({ onFiles, disabled, busy, inputRef }: Props) {
  const ownInput = useRef<HTMLInputElement>(null);
  const input = inputRef ?? ownInput;
  const [dragging, setDragging] = useState(false);

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    if (!disabled && event.dataTransfer.files.length) onFiles([...event.dataTransfer.files]);
  }

  return (
    <div
      className={cx("dropzone", dragging && "is-dragging", disabled && "is-disabled")}
      role="button"
      tabIndex={0}
      aria-label="Upload a document"
      aria-busy={busy || undefined}
      onClick={() => !disabled && input.current?.click()}
      onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && !disabled && (e.preventDefault(), input.current?.click())}
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <input
        ref={input}
        type="file"
        multiple
        hidden
        accept={ACCEPTED_FILE_TYPES}
        onChange={(e) => {
          if (e.target.files?.length) onFiles([...e.target.files]);
          e.target.value = "";
        }}
      />
      <Sage mood={busy ? "thinking" : dragging ? "cheer" : "happy"} size={80} />
      <div className="dropzone-text">
        <SpeechBubble>{busy ? "Uploading… I'll read it in a moment!" : dragging ? "Yes! Drop it here!" : "Feed me a PDF, a scan or your notes!"}</SpeechBubble>
        <p className="small muted">
          Drag files here or <span className="dropzone-link">browse</span>. PDF, Word or images up to {formatBytes(MAX_FILE_BYTES)} · lecture audio or
          video up to {formatBytes(MAX_MEDIA_BYTES)}
        </p>
      </div>
    </div>
  );
}
