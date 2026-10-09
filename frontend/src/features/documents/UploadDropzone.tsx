import { useRef, useState, type DragEvent } from "react";
import { formatBytes } from "../../shared/lib/format";
import { ACCEPTED_FILE_TYPES, MAX_FILE_BYTES } from "./types";

interface Props {
  onFiles: (files: File[]) => void;
  disabled?: boolean;
}

export function UploadDropzone({ onFiles, disabled }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    if (!disabled && event.dataTransfer.files.length) onFiles([...event.dataTransfer.files]);
  }

  return (
    <div
      className={`dropzone${dragging ? " dragging" : ""}${disabled ? " disabled" : ""}`}
      role="button"
      tabIndex={0}
      onClick={() => !disabled && input.current?.click()}
      onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && !disabled && input.current?.click()}
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
      <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
        <path d="M12 16V4m0 0l-4 4m4-4l4 4M4 16v3a1 1 0 001 1h14a1 1 0 001-1v-3" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <p className="dropzone-title">Drop a document here, or click to choose</p>
      <p className="muted small">PDF or image (PNG, JPEG, WEBP, TIFF, BMP), up to {formatBytes(MAX_FILE_BYTES)}</p>
    </div>
  );
}
