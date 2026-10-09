import { FileUp } from "lucide-react";
import { useRef, useState, type DragEvent } from "react";
import { formatBytes } from "../../shared/lib/format";
import { cx } from "../../shared/ui/cx";
import { ACCEPTED_FILE_TYPES, MAX_FILE_BYTES } from "./types";

interface Props {
  onFiles: (files: File[]) => void;
  disabled?: boolean;
  busy?: boolean;
}

export function UploadDropzone({ onFiles, disabled, busy }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    if (!disabled && event.dataTransfer.files.length) onFiles([...event.dataTransfer.files]);
  }

  return (
    <div
      className={cx("dropzone", dragging && "dragging", disabled && "disabled")}
      role="button"
      tabIndex={0}
      aria-label="Upload a document"
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
      <div className="dropzone-icon" aria-hidden>
        {busy ? <span className="spinner" /> : <FileUp size={26} />}
      </div>
      <div>
        <p className="dropzone-title">{busy ? "Uploading…" : "Drop your study material here"}</p>
        <p className="muted small">
          or <span className="dropzone-link">browse files</span>. Syllabus, textbook, lecture notes or a photo of a page.
        </p>
        <p className="muted xs" style={{ marginTop: 4 }}>
          PDF, PNG, JPEG, WEBP, TIFF, BMP · up to {formatBytes(MAX_FILE_BYTES)}
        </p>
      </div>
    </div>
  );
}
