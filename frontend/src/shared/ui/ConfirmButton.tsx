import { useEffect, useRef, useState, type ReactNode } from "react";
import { Button, type ButtonSize, type ButtonVariant } from "./Button";

interface Props {
  onConfirm: () => void | Promise<void>;
  children: ReactNode;
  confirmLabel?: string;
  prompt?: string;
  icon?: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
  ariaLabel?: string;
}

/**
 * Two-step inline confirmation (no browser dialogs). It stays open until the user confirms, cancels
 * or presses Escape. No auto-dismiss timer (WCAG 2.2.1); focus moves to the confirm button.
 */
export function ConfirmButton({ onConfirm, children, confirmLabel = "Confirm", prompt = "Are you sure?", icon, variant = "ghost", size = "sm", ariaLabel }: Props) {
  const [armed, setArmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const group = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (armed) group.current?.querySelector<HTMLButtonElement>("button")?.focus();
  }, [armed]);

  if (!armed) {
    return (
      <Button variant={variant} size={size} icon={icon} onClick={() => setArmed(true)} aria-label={ariaLabel}>
        {children}
      </Button>
    );
  }
  return (
    <span
      ref={group}
      className="confirm-inline"
      role="group"
      aria-label={prompt}
      onKeyDown={(e) => {
        if (e.key === "Escape" && !busy) setArmed(false);
      }}
    >
      <span className="muted">{prompt}</span>
      <Button
        variant="danger"
        size="sm"
        loading={busy}
        onClick={async () => {
          setBusy(true);
          try {
            await onConfirm();
          } finally {
            setBusy(false);
            setArmed(false);
          }
        }}
      >
        {confirmLabel}
      </Button>
      <Button variant="ghost" size="sm" disabled={busy} onClick={() => setArmed(false)}>
        Cancel
      </Button>
    </span>
  );
}
