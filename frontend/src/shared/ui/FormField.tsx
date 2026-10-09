import type { InputHTMLAttributes } from "react";

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export function FormField({ label, error, id, ...input }: Props) {
  const fieldId = id ?? input.name;
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <input id={fieldId} aria-invalid={error ? true : undefined} aria-describedby={error ? `${fieldId}-error` : undefined} {...input} />
      {error && (
        <p className="field-error" id={`${fieldId}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}
