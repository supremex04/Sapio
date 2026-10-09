import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { ApiError, errorMessage } from "../../shared/api/errors";
import { Alert } from "../../shared/ui/Alert";
import { FormField } from "../../shared/ui/FormField";
import { useAuth } from "./AuthContext";
import { AuthLayout } from "./AuthLayout";
import type { RegisterInput } from "./types";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [role, setRole] = useState<RegisterInput["role"]>("STUDENT");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({
        fullName: String(form.get("fullName")),
        email: String(form.get("email")),
        password: String(form.get("password")),
        role,
      });
      navigate("/", { replace: true });
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length > 0) setFieldErrors(e.fieldErrors);
      else setError(errorMessage(e));
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start learning with Ved.">
      <form onSubmit={onSubmit} className="stack" noValidate>
        {error && <Alert>{error}</Alert>}
        <fieldset className="role-picker">
          <legend>I am a</legend>
          {(["STUDENT", "TEACHER"] as const).map((r) => (
            <label key={r} className={role === r ? "selected" : undefined}>
              <input type="radio" name="role" value={r} checked={role === r} onChange={() => setRole(r)} />
              {r === "STUDENT" ? "Student" : "Teacher"}
            </label>
          ))}
        </fieldset>
        <FormField label="Full name" name="fullName" autoComplete="name" required error={fieldErrors.fullName} />
        <FormField label="Email" name="email" type="email" autoComplete="email" required error={fieldErrors.email} />
        <FormField
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          minLength={8}
          required
          placeholder="At least 8 characters"
          error={fieldErrors.password}
        />
        <button className="btn btn-primary" type="submit" disabled={submitting}>
          {submitting ? "Creating account…" : "Create account"}
        </button>
      </form>
      <p className="muted center">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
